package com.stock.dashboard.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.stock.dashboard.exception.BusinessException;

/**
 * 사용자별 AI 분석 호출 횟수 제한(최근 1시간 기준).
 * OpenAI 호출은 건마다 요금이 나가므로, 로그인만 하면 무제한으로 호출되던 문제를 막는다.
 * 서버 한 대(메모리) 기준 구현이다.
 */
@Component
public class AiUsageLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final int   maxPerHour;
    private final Clock clock;
    private final Map<String, Deque<Instant>> usage = new ConcurrentHashMap<>();

    @Autowired
    public AiUsageLimiter(@Value("${openai.rate-limit.per-hour:20}") int maxPerHour) {
        this(maxPerHour, Clock.systemUTC());
    }

    AiUsageLimiter(int maxPerHour, Clock clock) {
        this.maxPerHour = maxPerHour;
        this.clock = clock;
    }

    public void acquire(String userKey) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(WINDOW);
        Deque<Instant> calls = usage.computeIfAbsent(userKey, k -> new ArrayDeque<>());
        synchronized (calls) {
            while (!calls.isEmpty() && !calls.peekFirst().isAfter(windowStart)) {
                calls.pollFirst();
            }
            if (calls.size() >= maxPerHour) {
                throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                        "AI 분석은 1시간에 " + maxPerHour + "회까지 사용할 수 있습니다. 잠시 후 다시 시도해주세요.");
            }
            calls.addLast(now);
        }
    }
}
