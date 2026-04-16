package com.stock.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.data.redis.RedisHealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.data.redis.RedisReactiveHealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.mail.MailHealthContributorAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
		RedisHealthContributorAutoConfiguration.class,
		RedisReactiveHealthContributorAutoConfiguration.class,
		MailHealthContributorAutoConfiguration.class
})
@EnableScheduling
@EnableAsync
public class StockDashboardApplication {

	public static void main(String[] args) {
		SpringApplication.run(StockDashboardApplication.class, args);
	}

}
