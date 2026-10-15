package com.stock.dashboard.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 외부 API(OAuth, PortOne, Resend, 네이버) 호출용 공용 HttpClient.
 * HttpClient 는 생성 비용이 크고 스레드를 만들기 때문에 요청마다 새로 만들지 않는다.
 * 요청 단위 제한 시간은 HttpRequest.timeout(REQUEST_TIMEOUT) 으로 건다.
 */
@Configuration
public class HttpClientConfig {

    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }
}
