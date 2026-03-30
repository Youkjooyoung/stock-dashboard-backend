package com.stock.dashboard.controller;

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

    private final StockService stockService;

    private static void validateDate(String date) {
        if (date == null || !date.matches("\\d{8}"))
            throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다. YYYYMMDD 형식으로 입력해주세요: " + date);
    }

    // 단일 날짜 수동 수집
    @GetMapping("/collect")
    public ResponseEntity<String> collectData(@RequestParam String basDt) throws Exception {
        validateDate(basDt);
        stockService.saveStockPrices(basDt);
        return ResponseEntity.ok("수집 완료: " + basDt);
    }

    // 날짜 범위 수동 수집
    @GetMapping("/collect/range")
    public ResponseEntity<String> collectRange(
            @RequestParam String startDate,
            @RequestParam String endDate) throws Exception {
        validateDate(startDate);
        validateDate(endDate);
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
        validateDate(startDate);
        validateDate(endDate);
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
        int count = stockService.collectTickerHistory(ticker, startDate, endDate);
        return ResponseEntity.ok("수집 완료: " + count + "건 (" + startDate + "~" + endDate + ")");
    }

    // 특정 종목 전체 기간 주가
    @GetMapping("/prices/{ticker}")
    public ResponseEntity<List<StockPriceDto>> getPriceByTicker(@PathVariable String ticker) {
        return ResponseEntity.ok(stockService.getPriceByTicker(ticker));
    }

    // 특정 종목 날짜 범위 주가
    @GetMapping("/prices/{ticker}/range")
    public ResponseEntity<List<StockPriceDto>> getPriceByRange(
            @PathVariable String ticker,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        return ResponseEntity.ok(stockService.getPriceByTickerAndDate(ticker, startDate, endDate));
    }
}