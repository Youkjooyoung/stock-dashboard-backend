package com.stock.dashboard.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stock.dashboard.dto.NewsDto;
import com.stock.dashboard.service.NewsService;

import lombok.RequiredArgsConstructor;

/** 네이버 뉴스 검색 API 를 대신 불러 주는 API. 네이버 키가 브라우저에 드러나지 않게 서버가 호출한다. */
@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    @GetMapping
    public ResponseEntity<List<NewsDto>> getNews(
            @RequestParam(defaultValue = "주식") String query,
            @RequestParam(defaultValue = "10") int display) throws Exception {
        return ResponseEntity.ok(newsService.getStockNews(query, display));
    }

    @GetMapping("/{stockName}")
    public ResponseEntity<List<NewsDto>> getStockNews(
            @PathVariable String stockName,
            @RequestParam(defaultValue = "5") int display) throws Exception {
        return ResponseEntity.ok(newsService.getStockNews(stockName + " 주식", display));
    }
}