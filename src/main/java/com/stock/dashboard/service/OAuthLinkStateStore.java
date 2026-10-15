package com.stock.dashboard.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 소셜 계정 "연동" 요청마다 일회용 state 값을 발급하고, 누가 요청했는지 서버에 기억한다.
 * 예전에는 액세스 토큰을 URL(state)에 그대로 실어 보내서 브라우저 기록·서버 로그·구글/카카오 서버에 토큰이 남았다.
 *
 * 서버 한 대(메모리) 기준 구현이다. 서버를 여러 대로 늘리면 Redis 같은 공용 저장소로 옮겨야 한다.
 */
@Component
public class OAuthLinkStateStore {

    private static final Duration TTL = Duration.ofMinutes(10);

    public record LinkRequest(String email, String provider, Instant expiresAt) {}

    private final SecureRandom random = new SecureRandom();
    private final Map<String, LinkRequest> requests = new ConcurrentHashMap<>();

    public String issue(String email, String provider) {
        removeExpired();
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        requests.put(state, new LinkRequest(email, provider, Instant.now().plus(TTL)));
        return state;
    }

    /** state 는 한 번만 쓸 수 있다. 없거나 만료됐으면 null. */
    public LinkRequest consume(String state) {
        if (state == null) return null;
        LinkRequest request = requests.remove(state);
        if (request == null || request.expiresAt().isBefore(Instant.now())) return null;
        return request;
    }

    private void removeExpired() {
        Instant now = Instant.now();
        requests.values().removeIf(r -> r.expiresAt().isBefore(now));
    }
}
