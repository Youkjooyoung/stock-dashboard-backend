package com.stock.dashboard.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.dashboard.dao.StockDao;
import com.stock.dashboard.dto.StockItemDto;
import com.stock.dashboard.dto.StockPriceDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

	private static final DateTimeFormatter FMT        = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final List<String>     MARKETS     = List.of("KOSPI", "KOSDAQ");
	private static final HttpClient       HTTP_CLIENT = HttpClient.newHttpClient();
	private static final int              MAX_RETRY   = 3;
	private static final long             RETRY_DELAY = 2000L; // ms

	// 전체 수집 진행 상태 (0 = 대기, -1 = 완료, 1~N = 진행 중 종목 번호)
	private final AtomicInteger bulkProgress  = new AtomicInteger(0);
	private volatile int        bulkTotal     = 0;
	private volatile String     bulkStatus    = "idle"; // idle | running | done | error

	public record BulkStatusDto(String status, int current, int total) {}

	public BulkStatusDto getBulkStatus() {
		return new BulkStatusDto(bulkStatus, bulkProgress.get(), bulkTotal);
	}

	private final StockDao stockDao;
	private final EmailService emailService;

	@Value("${api.data.serviceKey}")
	private String serviceKey;
	@Value("${api.stock.price.url}")
	private String priceUrl;
	@Value("${api.stock.item.url}")
	private String itemUrl;
	@Value("${notify.complete.email}")
	private String notifyEmail;

	@Cacheable(value = "allItems")
	public List<StockItemDto> getAllItems() {
		return stockDao.selectAllItems();
	}

	@Cacheable(value = "latestPrices")
	public List<StockPriceDto> getLatestPrices() {
		return stockDao.selectLatestPrices();
	}

	@Cacheable(value = "priceByTicker", key = "#ticker")
	public List<StockPriceDto> getPriceByTicker(String ticker) {
		return stockDao.selectPriceByTicker(ticker);
	}

	public List<StockPriceDto> getPriceByTickerAndDate(String ticker, String startDate, String endDate) {
		return stockDao.selectPriceByTickerAndDate(ticker, startDate, endDate);
	}

	// 저장 후 관련 캐시 전체 무효화
	@Caching(evict = { @CacheEvict(value = "latestPrices", allEntries = true),
			@CacheEvict(value = "allItems", allEntries = true),
			@CacheEvict(value = "priceByTicker", allEntries = true) })
	public void saveStockPrices(String basDt) throws Exception {
		for (String market : MARKETS) {
			saveStockPricesByMarket(basDt, market);
		}
	}

	public void saveStockPricesByMarket(String basDt, String market) throws Exception {
		List<StockPriceDto> list = fetchStockPriceFromApi(basDt, market);

		if (list.isEmpty()) {
			log.info("[저장] API 데이터 없음 — 휴장일이거나 아직 미반영: {} ({})", basDt, market);
			return;
		}

		int inserted = 0;
		int skipped = 0;

		for (StockPriceDto dto : list) {
			try {
				stockDao.insertStockItemIfNotExists(dto);
				int affected = stockDao.insertStockPrice(dto);
				if (affected > 0)
					inserted++;
				else
					skipped++;
			} catch (Exception e) {
				log.warn("저장 실패 - {} ({}): {}", dto.getItmsNm(), market, e.getMessage());
			}
		}

		log.info("[저장] {} 신규: {}건 / 중복스킵: {}건 ({})", market, inserted, skipped, basDt);
	}

	public void saveStockPricesRange(String startDate, String endDate) throws Exception {
		LocalDate cursor = LocalDate.parse(startDate, FMT);
		LocalDate end = LocalDate.parse(endDate, FMT);

		while (!cursor.isAfter(end)) {
			if (cursor.getDayOfWeek() != DayOfWeek.SATURDAY && cursor.getDayOfWeek() != DayOfWeek.SUNDAY) {
				saveStockPrices(cursor.format(FMT));
			}
			cursor = cursor.plusDays(1);
		}
	}

	// 전체 종목 일괄 과거 데이터 수집 (비동기)
	@Async
	public void collectAllHistory(String startDate, String endDate) {
		collectAllHistory(startDate, endDate, 0, false);
	}

	@Async
	public void collectAllHistory(String startDate, String endDate, int fromIndex, boolean skipExisting) {
		if ("running".equals(bulkStatus)) {
			log.warn("[일괄수집] 이미 실행 중");
			return;
		}
		List<StockItemDto> items = stockDao.selectAllItems();

		// skipExisting=true 이면 이미 데이터가 있는 티커는 건너뜀
		Set<String> collected = skipExisting
				? new HashSet<>(stockDao.selectCollectedTickers(startDate, endDate))
				: new HashSet<>();

		if (skipExisting) {
			log.info("[일괄수집] 기수집 종목 {}개 스킵 예정", collected.size());
		}

		bulkTotal = items.size();
		bulkProgress.set(fromIndex);
		bulkStatus = "running";
		log.info("[일괄수집] 시작 — 총 {}종목, {}~{}, {}번부터, skipExisting={}", bulkTotal, startDate, endDate, fromIndex, skipExisting);

		for (int i = fromIndex; i < items.size(); i++) {
			StockItemDto item = items.get(i);
			if (skipExisting && collected.contains(item.getTicker())) {
				bulkProgress.incrementAndGet();
				continue;
			}
			try {
				collectTickerHistory(item.getTicker(), startDate, endDate);
				bulkProgress.incrementAndGet();
			} catch (Exception e) {
				log.warn("[일괄수집] 실패 - {}: {}", item.getTicker(), e.getMessage());
				bulkProgress.incrementAndGet();
			}
		}
		bulkStatus = "done";
		log.info("[일괄수집] 완료 — {}종목", bulkTotal);
		try {
			emailService.sendBulkCollectCompleteEmail(notifyEmail, bulkTotal, startDate, endDate);
		} catch (Exception e) {
			log.warn("[일괄수집] 완료 알림 메일 발송 실패: {}", e.getMessage());
		}
	}

	// 특정 종목 과거 데이터 수집 (beginBasDt ~ endBasDt + likeSrtnCd 필터)
	@Caching(evict = { @CacheEvict(value = "latestPrices", allEntries = true),
			@CacheEvict(value = "priceByTicker", allEntries = true) })
	public int collectTickerHistory(String ticker, String startDate, String endDate) throws Exception {
		List<StockPriceDto> list = fetchTickerHistoryFromApi(ticker, startDate, endDate);
		if (list.isEmpty()) {
			log.info("[히스토리] 데이터 없음 — ticker: {}, {}~{}", ticker, startDate, endDate);
			return 0;
		}
		int inserted = 0;
		for (StockPriceDto dto : list) {
			try {
				stockDao.insertStockItemIfNotExists(dto);
				if (stockDao.insertStockPrice(dto) > 0) inserted++;
			} catch (Exception e) {
				log.warn("[히스토리] 저장 실패 - {}: {}", dto.getItmsNm(), e.getMessage());
			}
		}
		log.info("[히스토리] ticker={} 신규 {}건 저장 ({}~{})", ticker, inserted, startDate, endDate);
		return inserted;
	}

	private String sendWithRetry(URI uri) throws Exception {
		Exception last = new RuntimeException("API 호출 실패: " + uri);
		for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
			try {
				return HTTP_CLIENT
						.send(HttpRequest.newBuilder().uri(uri).GET().build(), HttpResponse.BodyHandlers.ofString())
						.body();
			} catch (Exception e) {
				last = e;
				log.warn("[API] 요청 실패 (시도 {}/{}): {}", attempt, MAX_RETRY, e.getMessage());
				if (attempt < MAX_RETRY) Thread.sleep(RETRY_DELAY * attempt);
			}
		}
		throw last;
	}

	private List<StockPriceDto> fetchTickerHistoryFromApi(String ticker, String startDate, String endDate) throws Exception {
		List<StockPriceDto> result = new ArrayList<>();
		ObjectMapper mapper = new ObjectMapper();
		int pageNo = 1;

		while (true) {
			URI uri = UriComponentsBuilder.fromHttpUrl(priceUrl)
					.queryParam("serviceKey", serviceKey)
					.queryParam("numOfRows", 9999)
					.queryParam("pageNo", pageNo)
					.queryParam("resultType", "json")
					.queryParam("likeSrtnCd", ticker)
					.queryParam("beginBasDt", startDate)
					.queryParam("endBasDt", endDate)
					.build(true).toUri();

			log.debug("[히스토리] API 호출 page {}: {}", pageNo, uri);

			JsonNode body = mapper.readTree(sendWithRetry(uri)).path("response").path("body");
			JsonNode items = body.path("items").path("item");

			if (!items.isArray() || items.size() == 0) break;

			for (JsonNode item : items) {
				result.add(mapper.treeToValue(item, StockPriceDto.class));
			}

			int totalCount = body.path("totalCount").asInt(0);
			if (result.size() >= totalCount) break;
			pageNo++;
		}

		log.debug("[히스토리] ticker={} 최종 {}건 수신", ticker, result.size());
		return result;
	}

	private List<StockPriceDto> fetchStockPriceFromApi(String basDt, String market) throws Exception {
		List<StockPriceDto> result = new ArrayList<>();
		ObjectMapper mapper = new ObjectMapper();
		int pageNo = 1;

		while (true) {
			URI uri = UriComponentsBuilder.fromHttpUrl(priceUrl).queryParam("serviceKey", serviceKey)
					.queryParam("numOfRows", 1000).queryParam("pageNo", pageNo).queryParam("resultType", "json")
					.queryParam("basDt", basDt).queryParam("mrktCls", market).build(true).toUri();

			log.debug("[{}] API 호출 page {}: {}", market, pageNo, uri);

			JsonNode body = mapper.readTree(sendWithRetry(uri)).path("response").path("body");
			JsonNode items = body.path("items").path("item");

			if (!items.isArray() || items.size() == 0)
				break;

			for (JsonNode item : items) {
				result.add(mapper.treeToValue(item, StockPriceDto.class));
			}

			int totalCount = body.path("totalCount").asInt(0);
			log.debug("[{}] page {} 수신: {}건 / 전체: {}건", market, pageNo, items.size(), totalCount);

			if (result.size() >= totalCount)
				break;
			pageNo++;
		}

		log.debug("[{}] 최종 수신: {}건", market, result.size());
		return result;
	}
}