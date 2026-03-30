package com.stock.dashboard.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stock.dashboard.service.AiAnalysisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;

    @PostMapping("/analyze/stock")
    public ResponseEntity<Map<String, String>> analyzeStock(@RequestBody Map<String, String> body) {
        try {
            String prompt = body.get("prompt");
            if (prompt == null || prompt.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "prompt가 필요합니다."));
            }
            String result = aiAnalysisService.analyzeStock(prompt);
            return ResponseEntity.ok(Map.of("analysis", result));
        } catch (Exception e) {
            log.error("종목 AI 분석 오류", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/analyze/portfolio")
    public ResponseEntity<Map<String, String>> analyzePortfolio(@RequestBody Map<String, String> body) {
        try {
            String prompt = body.get("prompt");
            if (prompt == null || prompt.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "prompt가 필요합니다."));
            }
            String result = aiAnalysisService.analyzePortfolio(prompt);
            return ResponseEntity.ok(Map.of("analysis", result));
        } catch (Exception e) {
            log.error("포트폴리오 AI 분석 오류", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
