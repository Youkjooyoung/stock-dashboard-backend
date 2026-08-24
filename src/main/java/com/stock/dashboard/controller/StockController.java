package com.stock.dashboard.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import com.stock.dashboard.dto.StockItemDto;
import com.stock.dashboard.dto.StockPriceDto;
import com.stock.dashboard.service.StockService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final long MAX_RANGE_DAYS = 3660;
    private static final String TICKER_PATTERN = "^[0-9A-Z]{6,12}$";

    private final StockService stockService;

    private static LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date, DATE_FORMATTER);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("날짜는 YYYYMMDD 형식의 실제 날짜여야 합니다.");
        }
    }

    private static void validateRange(String startDate, String endDate) {
        LocalDate start = parseDate(startDate);
        LocalDate end = parseDate(endDate);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("시작일은 종료일보다 늦을 수 없습니다.");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("조회 기간은 최대 10년까지 선택할 수 있습니다.");
        }
    }

    private static void validateTicker(String ticker) {
        if (ticker == null || !ticker.matches(TICKER_PATTERN)) {
            throw new IllegalArgumentException("종목코드 형식이 올바르지 않습니다.");
        }
    }

    // 단일 날짜 수동 수집
    @PostMapping("/collect")
    public ResponseEntity<String> collectData(@RequestParam String basDt) throws Exception {
        parseDate(basDt);
        stockService.saveStockPrices(basDt);
        return ResponseEntity.ok("수집 완료: " + basDt);
    }

    // 날짜 범위 수동 수집
    @PostMapping("/collect/range")
    public ResponseEntity<String> collectRange(
            @RequestParam String startDate,
            @RequestParam String endDate) throws Exception {
        validateRange(startDate, endDate);
        stockService.saveStockPricesRange(startDate, endDate);
        return ResponseEntity.ok("범위 수집 완료: " + startDate + " ~ " + endDate);
    }

    // 전체 종목 일괄 과거 데이터 수집 (비동기 시작)
    @PostMapping("/collect/history/all")
    public ResponseEntity<String> collectAllHistory(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "0") int fromIndex,
            @RequestParam(defaultValue = "false") boolean skipExisting) {
        validateRange(startDate, endDate);
        if (fromIndex < 0) {
            throw new IllegalArgumentException("시작 인덱스는 0 이상이어야 합니다.");
        }
        stockService.collectAllHistory(startDate, endDate, fromIndex, skipExisting);
        String msg = "일괄 수집 시작: " + startDate + "~" + endDate
                + (skipExisting ? " (기수집 종목 스킵)" : "");
        return ResponseEntity.accepted().body(msg);
    }

    // 일괄 수집 진행 상태 조회
    @GetMapping("/collect/history/status")
    public ResponseEntity<Map<String, Object>> collectStatus() {
        var s = stockService.getBulkStatus();
        return ResponseEntity.ok(Map.of(
            "status",  s.status(),
            "current", s.current(),
            "total",   s.total()
        ));
    }

    // 종목 전체 조회
    @GetMapping("/items")
    public ResponseEntity<List<StockItemDto>> getAllItems() {
        return ResponseEntity.ok(stockService.getAllItems());
    }

    // 최신 주가 목록
    @GetMapping("/prices")
    public ResponseEntity<List<StockPriceDto>> getLatestPrices() {
        return ResponseEntity.ok(stockService.getLatestPrices());
    }

    // 특정 종목 과거 데이터 수집
    @PostMapping("/prices/{ticker}/collect")
    public ResponseEntity<String> collectTickerHistory(
            @PathVariable String ticker,
            @RequestParam String startDate,
            @RequestParam String endDate) throws Exception {
        validateTicker(ticker);
        validateRange(startDate, endDate);
        int count = stockService.collectTickerHistory(ticker, startDate, endDate);
        return ResponseEntity.ok("수집 완료: " + count + "건 (" + startDate + "~" + endDate + ")");
    }

    // 특정 종목 전체 기간 주가
    @GetMapping("/prices/{ticker}")
    public ResponseEntity<List<StockPriceDto>> getPriceByTicker(@PathVariable String ticker) {
        validateTicker(ticker);
        return ResponseEntity.ok(stockService.getPriceByTicker(ticker));
    }

    // 특정 종목 날짜 범위 주가
    @GetMapping("/prices/{ticker}/range")
    public ResponseEntity<List<StockPriceDto>> getPriceByRange(
            @PathVariable String ticker,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        validateTicker(ticker);
        validateRange(startDate, endDate);
        return ResponseEntity.ok(stockService.getPriceByTickerAndDate(ticker, startDate, endDate));
    }
}
