package com.stock.dashboard.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

/**
 * 주가 조회와 수집.
 * 조회는 DB 값을 캐시해서 돌려주고, 수집은 공공데이터포털 "주식시세정보" API 를 불러 DB 에 저장한다.
 * (공공데이터는 전일 종가 기준이라 실시간 시세가 아니다.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

	private static final DateTimeFormatter FMT           = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final List<String>     MARKETS       = List.of("KOSPI", "KOSDAQ");
	// 공공데이터 API 는 응답이 느릴 때가 있어 공용 HttpClient 의 기본(10초)보다 길게 기다린다.
	private static final Duration         API_TIMEOUT   = Duration.ofSeconds(20);
	private static final int              MAX_RETRY     = 3;
	private static final long             RETRY_DELAY   = 2000L; // ms, 시도할 때마다 2초·4초로 늘어난다
	private static final int              ROWS_BY_DATE  = 1000;  // 날짜별 전체 종목 조회의 한 페이지 크기
	private static final int              ROWS_BY_TICKER = 9999; // 종목별 기간 조회의 한 페이지 크기

	// 전체 수집 진행 상태 (0 = 대기, 1~N = 처리한 종목 수)
	private final AtomicInteger bulkProgress  = new AtomicInteger(0);
	private volatile int        bulkTotal     = 0;
	private final AtomicReference<String> bulkStatus = new AtomicReference<>("idle"); // idle | running | done

	public record BulkStatusDto(String status, int current, int total) {}

	public BulkStatusDto getBulkStatus() {
		return new BulkStatusDto(bulkStatus.get(), bulkProgress.get(), bulkTotal);
	}

	private final StockDao stockDao;
	private final EmailService emailService;
	private final HttpClient httpClient;
	private final ObjectMapper mapper = new ObjectMapper();

	@Value("${api.data.serviceKey}")
	private String serviceKey;
	@Value("${api.stock.price.url}")
	private String priceUrl;
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

	/** 하루치(basDt, yyyyMMdd) 코스피·코스닥 전체 종목 시세를 저장한다. 저장 후 관련 캐시를 모두 비운다. */
	@Caching(evict = { @CacheEvict(value = "latestPrices", allEntries = true),
			@CacheEvict(value = "allItems", allEntries = true),
			@CacheEvict(value = "priceByTicker", allEntries = true) })
	public void saveStockPrices(String basDt) throws Exception {
		for (String market : MARKETS) {
			saveStockPricesByMarket(basDt, market);
		}
	}

	private void saveStockPricesByMarket(String basDt, String market) throws Exception {
		List<StockPriceDto> list = fetchStockPriceFromApi(basDt, market);

		if (list.isEmpty()) {
			log.info("[저장] API 데이터 없음 — 휴장일이거나 아직 미반영: {} ({})", basDt, market);
			return;
		}

		int inserted = 0;
		int skipped = 0;

		for (StockPriceDto dto : list) {
			try {
				// 처음 보는 종목이면 종목 목록에도 넣는다. 시세가 이미 있으면 0건이 저장된다(중복 무시).
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

	/** startDate~endDate 사이 평일마다 saveStockPrices 를 실행한다(주말은 휴장이라 건너뜀). */
	public void saveStockPricesRange(String startDate, String endDate) throws Exception {
		LocalDate cursor = LocalDate.parse(startDate, FMT);
		LocalDate end = LocalDate.parse(endDate, FMT);

		while (!cursor.isAfter(end)) {
			if (isWeekday(cursor)) {
				saveStockPrices(cursor.format(FMT));
			}
			cursor = cursor.plusDays(1);
		}
	}

	/**
	 * 전체 종목의 과거 시세를 종목별로 수집한다(비동기, 관리자 전용).
	 * fromIndex 부터 시작할 수 있고, skipExisting 이면 이미 그 기간 데이터가 있는 종목은 건너뛴다.
	 * 진행 상황은 getBulkStatus 로 확인한다. 동시에 두 번 실행되지 않는다.
	 */
	@Async
	public void collectAllHistory(String startDate, String endDate, int fromIndex, boolean skipExisting) {
		// 이전 상태가 무엇이든 running 으로 바꾸고, 이미 running 이었으면 그냥 끝낸다.
		if ("running".equals(bulkStatus.getAndSet("running"))) {
			log.warn("[일괄수집] 이미 실행 중");
			return;
		}
		List<StockItemDto> items = stockDao.selectAllItems();

		Set<String> collected = skipExisting
				? new HashSet<>(stockDao.selectCollectedTickers(startDate, endDate))
				: new HashSet<>();

		if (skipExisting) {
			log.info("[일괄수집] 기수집 종목 {}개 스킵 예정", collected.size());
		}

		bulkTotal = items.size();
		bulkProgress.set(fromIndex);
		log.info("[일괄수집] 시작 — 총 {}종목, {}~{}, {}번부터, skipExisting={}", bulkTotal, startDate, endDate, fromIndex, skipExisting);

		for (int i = fromIndex; i < items.size(); i++) {
			StockItemDto item = items.get(i);
			if (!(skipExisting && collected.contains(item.getTicker()))) {
				try {
					collectTickerHistory(item.getTicker(), startDate, endDate);
				} catch (Exception e) {
					log.warn("[일괄수집] 실패 - {}: {}", item.getTicker(), e.getMessage());
				}
			}
			bulkProgress.incrementAndGet();
		}
		bulkStatus.set("done");
		log.info("[일괄수집] 완료 — {}종목", bulkTotal);
		try {
			emailService.sendBulkCollectCompleteEmail(notifyEmail, bulkTotal, startDate, endDate);
		} catch (Exception e) {
			log.warn("[일괄수집] 완료 알림 메일 발송 실패: {}", e.getMessage());
		}
	}

	/** 한 종목의 기간(startDate~endDate) 시세를 수집해 저장하고, 새로 저장한 건수를 돌려준다. */
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

	private static boolean isWeekday(LocalDate date) {
		return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
	}

	// 실패하면 2초, 4초 쉬고 최대 3번까지 다시 시도한다.
	private String sendWithRetry(URI uri) throws Exception {
		Exception last = new RuntimeException("외부 주식 API 호출에 실패했습니다.");
		for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
			try {
				HttpRequest request = HttpRequest.newBuilder()
						.uri(uri)
						.timeout(API_TIMEOUT)
						.GET()
						.build();
				HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() >= 200 && response.statusCode() < 300) {
					return response.body();
				}
				throw new IllegalStateException("외부 주식 API가 HTTP " + response.statusCode() + " 응답을 반환했습니다.");
			} catch (Exception e) {
				last = e;
				log.warn("[API] 요청 실패 (시도 {}/{}): {}", attempt, MAX_RETRY, e.getClass().getSimpleName());
				if (attempt < MAX_RETRY) Thread.sleep(RETRY_DELAY * attempt);
			}
		}
		throw last;
	}

	// 한 종목의 기간 시세 (likeSrtnCd = 종목코드, beginBasDt~endBasDt)
	private List<StockPriceDto> fetchTickerHistoryFromApi(String ticker, String startDate, String endDate) throws Exception {
		UriComponentsBuilder query = UriComponentsBuilder.fromHttpUrl(priceUrl)
				.queryParam("likeSrtnCd", ticker)
				.queryParam("beginBasDt", startDate)
				.queryParam("endBasDt", endDate);
		List<StockPriceDto> result = fetchAllPages(query, ROWS_BY_TICKER);
		log.debug("[히스토리] ticker={} 최종 {}건 수신 ({}~{})", ticker, result.size(), startDate, endDate);
		return result;
	}

	// 하루치 한 시장(KOSPI/KOSDAQ) 전체 종목 시세
	private List<StockPriceDto> fetchStockPriceFromApi(String basDt, String market) throws Exception {
		UriComponentsBuilder query = UriComponentsBuilder.fromHttpUrl(priceUrl)
				.queryParam("basDt", basDt)
				.queryParam("mrktCls", market);
		List<StockPriceDto> result = fetchAllPages(query, ROWS_BY_DATE);
		log.debug("[{}] 최종 수신: {}건 ({})", market, result.size(), basDt);
		return result;
	}

	/**
	 * 공공데이터 API 는 한 번에 numOfRows 건까지만 준다. totalCount 만큼 모일 때까지 pageNo 를 올려 가며 부른다.
	 * 응답 모양: { response: { body: { totalCount, items: { item: [ ... ] } } } }
	 */
	private List<StockPriceDto> fetchAllPages(UriComponentsBuilder query, int rowsPerPage) throws Exception {
		List<StockPriceDto> result = new ArrayList<>();
		int pageNo = 1;

		while (true) {
			// serviceKey 는 이미 인코딩된 값이라 build(true) 로 다시 인코딩하지 않는다.
			URI uri = query.cloneBuilder()
					.queryParam("serviceKey", serviceKey)
					.queryParam("numOfRows", rowsPerPage)
					.queryParam("pageNo", pageNo)
					.queryParam("resultType", "json")
					.build(true).toUri();

			JsonNode body = mapper.readTree(sendWithRetry(uri)).path("response").path("body");
			JsonNode items = body.path("items").path("item");

			if (!items.isArray() || items.size() == 0) break;

			for (JsonNode item : items) {
				result.add(mapper.treeToValue(item, StockPriceDto.class));
			}

			int totalCount = body.path("totalCount").asInt(0);
			log.debug("[API] page {} 수신: {}건 / 전체: {}건", pageNo, items.size(), totalCount);
			if (result.size() >= totalCount) break;
			pageNo++;
		}
		return result;
	}
}
