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
import com.stock.dashboard.dto.NewsDto;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NewsService {

    @Value("${naver.client-id}")     private String clientId;
    @Value("${naver.client-secret}") private String clientSecret;
    @Value("${naver.news-url}")      private String newsUrl;

    public List<NewsDto> getStockNews(String query, int display) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        URI uri = URI.create(newsUrl + "?query=" + encoded + "&display=" + display + "&sort=date");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("X-Naver-Client-Id", clientId)
                .header("X-Naver-Client-Secret", clientSecret)
                .GET().build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode items = new ObjectMapper().readTree(response.body()).path("items");

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