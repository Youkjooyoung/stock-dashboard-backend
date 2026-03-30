package com.stock.dashboard.scheduler;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.stock.dashboard.service.PriceAlertService;
import com.stock.dashboard.service.StockService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class StockScheduler {

	private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final LocalTime COLLECT_TIME = LocalTime.of(18, 0);
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final PriceAlertService alertService;
	private final StockService stockService;
	private final SimpMessagingTemplate messagingTemplate;

	@Scheduled(cron = "0 30 16 * * MON-FRI", zone = "Asia/Seoul")
	public void checkBigMoveAlerts() {
		alertService.checkBigMoveStocks();
	}

	@Scheduled(fixedDelay = 60000)
	public void checkPriceAlerts() {
		alertService.checkAlerts();
	}

	// 전일 데이터 보완 (공공데이터포털 익일 오전 반영 대응)
	@Scheduled(cron = "0 0 8 * * MON-FRI", zone = "Asia/Seoul")
	public void collectYesterday() {
		LocalDate prev = LocalDate.now(SEOUL).minusDays(1);
		if (prev.getDayOfWeek() == DayOfWeek.SUNDAY)
			prev = prev.minusDays(2);
		else if (prev.getDayOfWeek() == DayOfWeek.SATURDAY)
			prev = prev.minusDays(1);
		log.info("[스케줄러] 전일 데이터 보완: {}", prev.format(FMT));
		collect(prev.format(FMT));
	}

	// 당일 데이터 수집 (18시 — API 반영 여유)
	@Scheduled(cron = "0 0 18 * * MON-FRI", zone = "Asia/Seoul")
	public void collectToday() {
		String today = LocalDate.now(SEOUL).format(FMT);
		log.info("[스케줄러] 오늘 데이터 수집: {}", today);
		collect(today);
	}

	// 서버 시작 시 최근 5 영업일 보완
	@Scheduled(initialDelay = 5000, fixedDelay = Long.MAX_VALUE)
	public void collectMissingOnStartup() {
		log.info("[스케줄러] 누락 데이터 보완 시작");
		LocalDate cursor = LocalDate.now(SEOUL);
		LocalTime nowTime = LocalTime.now(SEOUL);

		if (nowTime.isBefore(COLLECT_TIME)) {
			log.info("[스케줄러] 현재 {}시 — 오늘 데이터는 18:00 스케줄러에서 수집", nowTime.getHour());
			cursor = cursor.minusDays(1);
		}

		int collected = 0;
		while (collected < 5) {
			if (cursor.getDayOfWeek() != DayOfWeek.SATURDAY && cursor.getDayOfWeek() != DayOfWeek.SUNDAY) {
				collect(cursor.format(FMT));
				collected++;
			}
			cursor = cursor.minusDays(1);
		}
		log.info("[스케줄러] 누락 데이터 보완 완료");
	}

	@SuppressWarnings("null") // Map.of() is always non-null; Eclipse null analysis false positive
	private void collect(String basDt) {
		try {
			stockService.saveStockPrices(basDt);
			log.info("[스케줄러] 수집 완료: {}", basDt);
			// 연결된 클라이언트에 가격 갱신 알림 브로드캐스트
			Object payload = Map.of("basDt", basDt);
			messagingTemplate.convertAndSend("/topic/prices", payload);
		} catch (Exception e) {
			log.warn("[스케줄러] 수집 실패: {} - {}", basDt, e.getMessage());
		}
	}
}