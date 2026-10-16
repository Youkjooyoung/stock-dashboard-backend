package com.stock.dashboard.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.dashboard.config.HttpClientConfig;
import com.stock.dashboard.dto.NewsDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 네이버 뉴스 검색 API 를 불러 최신순 뉴스 목록을 돌려준다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsService {

    private static final int MAX_DISPLAY = 20;

    private final HttpClient   httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${naver.client-id}")     private String clientId;
    @Value("${naver.client-secret}") private String clientSecret;
    @Value("${naver.news-url}")      private String newsUrl;

    public List<NewsDto> getStockNews(String query, int display) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        int safeDisplay = Math.max(1, Math.min(display, MAX_DISPLAY));
        URI uri = URI.create(newsUrl + "?query=" + encoded + "&display=" + safeDisplay + "&sort=date");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(HttpClientConfig.REQUEST_TIMEOUT)
                .header("X-Naver-Client-Id", clientId)
                .header("X-Naver-Client-Secret", clientSecret)
                .GET().build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        // 뉴스는 부가 정보라 실패해도 화면 전체를 오류로 만들지 않고 빈 목록을 돌려준다.
        if (response.statusCode() != 200) {
            log.warn("[뉴스] 네이버 검색 API 실패: status={}", response.statusCode());
            return List.of();
        }

        JsonNode items = mapper.readTree(response.body()).path("items");

        List<NewsDto> list = new ArrayList<>();
        if (items.isArray()) {
            for (JsonNode item : items) {
                NewsDto dto = new NewsDto();
                dto.setTitle(cleanHtml(item.path("title").asText()));
                dto.setOriginallink(item.path("originallink").asText());
                dto.setLink(item.path("link").asText());
                dto.setDescription(cleanHtml(item.path("description").asText()));
                dto.setPubDate(item.path("pubDate").asText());
                list.add(dto);
            }
        }

        log.debug("뉴스 조회: {} - {}건", query, list.size());
        return list;
    }

    private String cleanHtml(String text) {
        return text.replaceAll("<[^>]*>", "")
                   .replaceAll("&quot;", "\"").replaceAll("&amp;", "&")
                   .replaceAll("&lt;",   "<").replaceAll("&gt;",   ">")
                   .replaceAll("&apos;", "'");
    }
}