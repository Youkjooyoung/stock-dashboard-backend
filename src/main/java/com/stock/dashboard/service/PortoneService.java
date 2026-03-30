package com.stock.dashboard.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PortoneService {

    private static final String BASE_URL = "https://api.iamport.kr";

    @Value("${portone.api.key}")
    private String apiKey;

    @Value("${portone.api.secret}")
    private String apiSecret;

    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, String> getCertification(String impUid) throws Exception {
        String token = getAccessToken();

        HttpResponse<String> response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/certifications/" + impUid))
                .header("Authorization", token)
                .GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );

        JsonNode body = mapper.readTree(response.body()).path("response");
        return Map.of(
            "name",  body.path("name").asText(),
            "phone", body.path("phone").asText(),
            "birth", body.path("birthday").asText()
        );
    }

    private String getAccessToken() throws Exception {
        String requestBody = mapper.writeValueAsString(
            Map.of("imp_key", apiKey, "imp_secret", apiSecret)
        );

        HttpResponse<String> response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/users/getToken"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        return mapper.readTree(response.body())
            .path("response").path("access_token").asText();
    }
}