package com.stock.dashboard.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stock.dashboard.exception.BusinessException;
import com.stock.dashboard.service.AiAnalysisService;
import com.stock.dashboard.service.AiUsageLimiter;

import lombok.RequiredArgsConstructor;

/** AI 분석 API. 요청 길이와 사용자별 사용 횟수(AiUsageLimiter)를 제한한 뒤 OpenAI 로 보낸다. */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiAnalysisController {

    // 포트폴리오 분석 프롬프트(종목 수십 개)까지 담을 수 있는 길이. 그 이상은 요금 남용으로 본다.
    private static final int MAX_PROMPT_LENGTH = 6000;

    private final AiAnalysisService aiAnalysisService;
    private final AiUsageLimiter    aiUsageLimiter;

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, String>> analyze(
            @AuthenticationPrincipal String email,
            @RequestBody Map<String, String> body) throws Exception {
        String prompt = body.get("prompt");
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException("분석할 내용을 입력해주세요.");
        }
        if (prompt.length() > MAX_PROMPT_LENGTH) {
            throw new BusinessException("분석 요청은 " + MAX_PROMPT_LENGTH + "자 이하로 보내주세요.");
        }
        aiUsageLimiter.acquire(email);
        return ResponseEntity.ok(Map.of("analysis", aiAnalysisService.analyze(prompt)));
    }
}
