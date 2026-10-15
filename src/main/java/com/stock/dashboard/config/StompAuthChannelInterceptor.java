package com.stock.dashboard.config;

import java.util.List;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.stock.dashboard.JwtUtil;

import lombok.RequiredArgsConstructor;

/**
 * 웹소켓(STOMP)으로 들어오는 모든 메시지를 검사하는 검문소.
 *
 * - CONNECT  : Authorization 헤더의 액세스 토큰이 유효하면 그 사용자로 세션을 기록한다.
 *              토큰이 없거나 만료됐으면 비로그인 세션이 된다(시세 채널만 구독 가능).
 * - SUBSCRIBE: 시세(/topic/prices)는 누구나, 채팅(/topic/chat/**)·개인 알림(/user/queue/**)은 로그인 사용자만.
 * - SEND     : /app/** 만 허용한다. /topic 으로 직접 보내면 컨트롤러를 거치지 않고 모든 구독자에게
 *              그대로 방송되어 위조 채팅·가짜 알림을 뿌릴 수 있기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String PRICE_TOPIC       = "/topic/prices";
    private static final String CHAT_TOPIC_PREFIX = "/topic/chat/";
    private static final String USER_QUEUE_PREFIX = "/user/queue/";
    private static final String APP_PREFIX        = "/app/";

    private final JwtUtil jwtUtil;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT   -> authenticate(accessor);
            case SUBSCRIBE -> checkSubscribe(accessor);
            case SEND      -> checkSend(accessor);
            default        -> { }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String token = jwtUtil.stripBearer(accessor.getFirstNativeHeader("Authorization"));
        if (token == null || token.isBlank() || !jwtUtil.validateAccessToken(token)) {
            return;
        }
        String role = jwtUtil.getRoleFromAccess(token);
        List<SimpleGrantedAuthority> authorities = role != null
                ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                : List.of();
        accessor.setUser(new UsernamePasswordAuthenticationToken(jwtUtil.getEmailFromAccess(token), null, authorities));
    }

    private void checkSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (PRICE_TOPIC.equals(destination)) {
            return;
        }
        boolean allowed = destination != null
                && (destination.startsWith(CHAT_TOPIC_PREFIX) || destination.startsWith(USER_QUEUE_PREFIX));
        if (!allowed) {
            throw new MessagingException("구독할 수 없는 채널입니다.");
        }
        requireUser(accessor);
    }

    private void checkSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(APP_PREFIX)) {
            throw new MessagingException("메시지를 보낼 수 없는 채널입니다.");
        }
        requireUser(accessor);
    }

    private void requireUser(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new MessagingException("로그인이 필요합니다.");
        }
    }
}
