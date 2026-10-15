package com.stock.dashboard.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.stock.dashboard.exception.BusinessException;

@DisplayName("[보안] AI 사용량 제한")
class AiUsageLimiterTest {

    /** 테스트에서 시간을 직접 움직이기 위한 시계 */
    static class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-15T00:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public Instant instant() { return now; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }

    @Test
    @DisplayName("1시간에 정한 횟수를 넘으면 429 업무 오류")
    void blocksAfterLimit() {
        AiUsageLimiter limiter = new AiUsageLimiter(2, new MovableClock());
        limiter.acquire("me@test.com");
        limiter.acquire("me@test.com");

        assertThatThrownBy(() -> limiter.acquire("me@test.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("1시간에 2회");
    }

    @Test
    @DisplayName("사용자마다 따로 센다")
    void countsPerUser() {
        AiUsageLimiter limiter = new AiUsageLimiter(1, new MovableClock());
        limiter.acquire("a@test.com");

        assertThatCode(() -> limiter.acquire("b@test.com")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("1시간이 지나면 다시 사용할 수 있다")
    void resetsAfterWindow() {
        MovableClock clock = new MovableClock();
        AiUsageLimiter limiter = new AiUsageLimiter(1, clock);
        limiter.acquire("me@test.com");

        clock.advance(Duration.ofMinutes(61));

        assertThatCode(() -> limiter.acquire("me@test.com")).doesNotThrowAnyException();
    }
}
