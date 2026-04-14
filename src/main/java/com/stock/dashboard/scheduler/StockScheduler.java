package com.stock.dashboard.scheduler;

import java.time.DayOfWeek;
import java.time.LocalDate;
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
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final int BACKFILL_DAYS = 5;

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

	// 매일 11:00 KST — 최근 5영업일 역순 백필
	// 공공데이터 API 가 전일 종가를 오전 늦게 게시하므로 08/18시 단발 호출은 매번 공수표였음.
	// 11시 시점에는 T-1 확정 게시가 끝나 있고, 하루 놓쳐도 다음날 자동 복구됨.
	@Scheduled(cron = "0 0 11 * * MON-SAT", zone = "Asia/Seoul")
	public void collectRecent() {
		log.info("[스케줄러] 최근 {}영업일 백필 시작 (cron)", BACKFILL_DAYS);
		backfillRecentBusinessDays(LocalDate.now(SEOUL));
		log.info("[스케줄러] 최근 {}영업일 백필 완료 (cron)", BACKFILL_DAYS);
	}

	// 서버 시작 시 최근 5영업일 보완 — 기동 직후 즉시 동기화
	@Scheduled(initialDelay = 5000, fixedDelay = Long.MAX_VALUE)
	public void collectMissingOnStartup() {
		log.info("[스케줄러] 기동 백필 시작");
		backfillRecentBusinessDays(LocalDate.now(SEOUL));
		log.info("[스케줄러] 기동 백필 완료");
	}

	private void backfillRecentBusinessDays(LocalDate from) {
		LocalDate cursor = from;
		int collected = 0;
		while (collected < BACKFILL_DAYS) {
			if (cursor.getDayOfWeek() != DayOfWeek.SATURDAY && cursor.getDayOfWeek() != DayOfWeek.SUNDAY) {
				collect(cursor.format(FMT));
				collected++;
			}
			cursor = cursor.minusDays(1);
		}
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