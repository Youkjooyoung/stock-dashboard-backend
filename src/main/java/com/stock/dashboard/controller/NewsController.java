package com.stock.dashboard.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stock.dashboard.dto.NewsDto;
import com.stock.dashboard.service.NewsService;

import lombok.RequiredArgsConstructor;

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