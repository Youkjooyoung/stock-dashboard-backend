package com.stock.dashboard.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AiAnalysisService {

    private static final String OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses";
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
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public String analyze(String prompt) throws Exception {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("OPENAI_API_KEY environment variable is not configured.");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("instructions", SYSTEM_PROMPT);
        body.put("input", prompt);
        body.put("max_output_tokens", 1024);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_RESPONSES_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("OpenAI API error: {} - {}", response.statusCode(), response.body());
            throw new RuntimeException("AI analysis request failed. HTTP " + response.statusCode());
        }

        String analysis = extractOutputText(response.body());
        if (!StringUtils.hasText(analysis)) {
            log.error("OpenAI API response did not include text output: {}", response.body());
            throw new RuntimeException("AI analysis response did not include text output.");
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
