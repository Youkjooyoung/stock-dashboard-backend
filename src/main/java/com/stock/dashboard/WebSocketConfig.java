package com.stock.dashboard;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.stock.dashboard.config.StompAuthChannelInterceptor;

import lombok.RequiredArgsConstructor;

/** 웹소켓(STOMP) 설정. 접속 주소는 /ws, 클라이언트가 보내는 주소는 /app/**, 구독은 /topic/**·/user/queue/**. */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /queue 는 개인 알림(convertAndSendToUser → /user/queue/alert)에 쓴다.
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 브라우저에서는 허용한 사이트만 접속할 수 있다. (브라우저 밖 스크립트는 인터셉터의 인증으로 막는다.)
        registry.addEndpoint("/ws")
                .setAllowedOrigins(SecurityConfig.ALLOWED_ORIGINS.toArray(String[]::new))
                .withSockJS()
                .setSessionCookieNeeded(false);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
