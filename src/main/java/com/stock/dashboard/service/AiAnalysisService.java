package com.stock.dashboard.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stock.dashboard.exception.BusinessException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AiAnalysisService {

    private static final String OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final String SYSTEM_PROMPT = """
            You are an AI assistant for a Korean stock dashboard.
            Analyze the user's stock or portfolio data in Korean.
            Keep the response practical, concise, and educational.
            Do not present the analysis as guaranteed investment advice.
            Always include a brief risk note that market data can change and users should make final decisions themselves.
            """;

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.model:gpt-5.4-mini}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public String analyze(String prompt) throws Exception {
        if (!StringUtils.hasText(apiKey)) {
            log.error("OPENAI_API_KEY environment variable is not configured.");
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "AI 분석을 사용할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("instructions", SYSTEM_PROMPT);
        body.put("input", prompt);
        body.put("max_output_tokens", 1024);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_RESPONSES_URL))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("OpenAI API error: {} - {}", response.statusCode(), response.body());
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 분석 요청에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }

        String analysis = extractOutputText(response.body());
        if (!StringUtils.hasText(analysis)) {
            log.error("OpenAI API response did not include text output: {}", response.body());
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI 분석 결과를 받지 못했습니다. 잠시 후 다시 시도해주세요.");
        }

        log.info("AI analysis completed - responseLength={}", analysis.length());
        return analysis;
    }

    private String extractOutputText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        String outputText = root.path("output_text").asText();
        if (StringUtils.hasText(outputText)) {
            return outputText;
        }

        ArrayNode textParts = objectMapper.createArrayNode();
        for (JsonNode output : root.path("output")) {
            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").asText()) && StringUtils.hasText(content.path("text").asText())) {
                    textParts.add(content.path("text").asText());
                }
            }
        }

        StringBuilder builder = new StringBuilder();
        for (JsonNode part : textParts) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(part.asText());
        }
        return builder.toString();
    }
}
