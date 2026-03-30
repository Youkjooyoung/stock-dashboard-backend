package com.stock.dashboard.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AiAnalysisService {

    @Value("${anthropic.api.key}")
    private String apiKey;

    private static final String ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-haiku-4-5-20251001";
    private static final String SYSTEM_PROMPT =
            "당신은 한국 주식 시장 전문가 AI 어시스턴트입니다. " +
            "사용자의 포트폴리오나 개별 종목에 대해 간결하고 실용적인 분석을 제공하세요. " +
            "분석은 반드시 한국어로 작성하세요. " +
            "응답 형식: 마크다운 없이 일반 텍스트로 작성하고, 항목 구분은 빈 줄로 하세요. " +
            "마지막에 '⚠️ 본 분석은 AI가 생성한 참고 정보이며, 실제 투자 결정은 전문가와 상담하세요.'를 추가하세요.";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String analyzeStock(String prompt) throws Exception {
        return callClaude(prompt);
    }

    public String analyzePortfolio(String prompt) throws Exception {
        return callClaude(prompt);
    }

    private String callClaude(String userPrompt) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", MODEL);
        body.put("max_tokens", 1024);
        body.put("system", SYSTEM_PROMPT);

        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", "user");
        message.put("content", userPrompt);
        messages.add(message);
        body.set("messages", messages);

        String requestBody = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ANTHROPIC_URL))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Anthropic API 오류: {} - {}", response.statusCode(), response.body());
            throw new RuntimeException("AI 분석 요청 실패 (HTTP " + response.statusCode() + "): " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String text = root.path("content").path(0).path("text").asText();
        log.info("AI 분석 완료 - 응답 길이: {}자", text.length());
        return text;
    }
}
