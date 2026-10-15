package com.stock.dashboard.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.dashboard.config.HttpClientConfig;
import com.stock.dashboard.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortoneService {

    private static final String  BASE_URL        = "https://api.iamport.kr";
    // imp_uid 는 URL 경로에 들어가므로 영문·숫자만 허용한다(경로 조작 방지).
    private static final Pattern IMP_UID_PATTERN = Pattern.compile("^imp_[A-Za-z0-9]{1,40}$");

    private final HttpClient   httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${portone.api.key}")
    private String apiKey;

    @Value("${portone.api.secret}")
    private String apiSecret;

    /**
     * 실제로 완료된 본인인증만 통과시킨다.
     * 예전에는 실패 응답에서도 빈 문자열("")을 돌려줘서, 이름·전화번호가 비어 있는 소셜 가입자의
     * 빈 값과 비교되어 본인인증이 통과되는 문제가 있었다.
     */
    public Map<String, String> getCertification(String impUid) throws Exception {
        if (impUid == null || !IMP_UID_PATTERN.matcher(impUid).matches()) {
            throw new BusinessException("본인인증 정보가 올바르지 않습니다.");
        }

        HttpResponse<String> response = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/certifications/" + impUid))
                .timeout(HttpClientConfig.REQUEST_TIMEOUT)
                .header("Authorization", getAccessToken())
                .GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );

        JsonNode root = mapper.readTree(response.body());
        JsonNode body = root.path("response");
        boolean certified = response.statusCode() == 200
                && root.path("code").asInt(-1) == 0
                && body.path("certified").asBoolean(false);
        if (!certified) {
            log.warn("[본인인증] 완료되지 않은 인증 조회: status={}, code={}", response.statusCode(), root.path("code").asText());
            throw new BusinessException("본인인증이 완료되지 않았습니다.");
        }

        String name  = body.path("name").asText("");
        String phone = body.path("phone").asText("");
        if (name.isBlank() || phone.isBlank()) {
            throw new BusinessException("본인인증 정보를 가져올 수 없습니다.");
        }
        return Map.of(
            "name",  name,
            "phone", phone,
            "birth", body.path("birthday").asText("")
        );
    }

    private String getAccessToken() throws Exception {
        String requestBody = mapper.writeValueAsString(
            Map.of("imp_key", apiKey, "imp_secret", apiSecret)
        );

        HttpResponse<String> response = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/users/getToken"))
                .timeout(HttpClientConfig.REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        String token = mapper.readTree(response.body()).path("response").path("access_token").asText("");
        if (response.statusCode() != 200 || token.isBlank()) {
            log.error("[본인인증] PortOne 토큰 발급 실패: status={}", response.statusCode());
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "본인인증 서버에 연결할 수 없습니다.");
        }
        return token;
    }
}
