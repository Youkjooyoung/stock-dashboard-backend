package com.stock.dashboard.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.Principal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.stock.dashboard.JwtUtil;

@DisplayName("[보안] STOMP 인증 인터셉터")
class StompAuthChannelInterceptorTest {

    private final JwtUtil jwtUtil = new JwtUtil(
            "jwt-access-secret-key-for-testing-only-32chars!!",
            "jwt-refresh-secret-key-for-testing-only-32chars!!");
    private final StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(jwtUtil);

    private Message<byte[]> frame(StompCommand command, String destination, String authorization, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) accessor.setDestination(destination);
        if (authorization != null) accessor.addNativeHeader("Authorization", authorization);
        if (user != null) accessor.setUser(user);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Principal user() {
        return new UsernamePasswordAuthenticationToken("me@test.com", null);
    }

    @Test
    @DisplayName("유효한 액세스 토큰으로 CONNECT 하면 그 사용자로 세션이 기록된다")
    void connect_withValidToken_setsUser() {
        String token = jwtUtil.generateAccessToken("me@test.com", "USER");
        Message<byte[]> message = frame(StompCommand.CONNECT, null, "Bearer " + token, null);

        interceptor.preSend(message, null);

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("me@test.com");
    }

    @Test
    @DisplayName("본인인증(verify) 토큰으로는 로그인 세션이 만들어지지 않는다")
    void connect_withVerifyToken_staysAnonymous() {
        String verifyToken = jwtUtil.generateVerifyToken("me@test.com");
        Message<byte[]> message = frame(StompCommand.CONNECT, null, "Bearer " + verifyToken, null);

        interceptor.preSend(message, null);

        assertThat(MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class).getUser()).isNull();
    }

    @Test
    @DisplayName("비로그인 세션도 시세 채널은 구독할 수 있다")
    void anonymous_canSubscribePrices() {
        assertThatCode(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/prices", null, null), null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("비로그인 세션은 채팅·개인 알림을 구독할 수 없다")
    void anonymous_cannotSubscribeChatOrAlerts() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/chat/005930", null, null), null))
                .isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/alert", null, null), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    @DisplayName("예전 개인 알림 채널(/topic/alert/{userId})은 로그인해도 구독할 수 없다")
    void legacyAlertTopic_isBlocked() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/alert/1", null, user()), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    @DisplayName("로그인 사용자는 채팅과 자기 알림 큐를 구독할 수 있다")
    void user_canSubscribeChatAndOwnQueue() {
        assertThatCode(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/chat/005930", null, user()), null))
                .doesNotThrowAnyException();
        assertThatCode(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/alert", null, user()), null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("/topic 으로 직접 SEND 하는 위조 메시지는 로그인해도 거부된다")
    void sendToTopicDirectly_isBlocked() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/topic/chat/005930", null, user()), null))
                .isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/topic/alert/1", null, null), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    @DisplayName("채팅 전송(/app/chat/**)은 로그인 사용자만 가능하다")
    void sendChat_requiresUser() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/app/chat/005930", null, null), null))
                .isInstanceOf(MessagingException.class);
        assertThatCode(() -> interceptor.preSend(frame(StompCommand.SEND, "/app/chat/005930", null, user()), null))
                .doesNotThrowAnyException();
    }
}
