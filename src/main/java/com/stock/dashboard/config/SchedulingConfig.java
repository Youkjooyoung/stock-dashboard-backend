package com.stock.dashboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * @Scheduled 작업 전용 스케줄러.
 *
 * 이 빈이 없으면 웹소켓 설정(@EnableWebSocketMessageBroker)이 만든 messageBrokerTaskScheduler 를
 * @Scheduled 작업이 같이 쓴다(로그의 스레드 이름 "MessageBroker-1"). 주가 수집은 재시도하며 몇 초씩 멈추므로
 * 웹소켓 하트비트 같은 브로커 작업과 스레드를 나눠 쓰면 안 된다.
 * 빈 이름이 "taskScheduler" 이면 @Scheduled 가 이 스케줄러를 우선 사용한다.
 */
@Configuration
public class SchedulingConfig {

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // 수집(수십 초)과 1분 주기 가격 알림 검사가 서로 막지 않도록 2개
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}
