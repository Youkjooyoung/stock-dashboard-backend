package com.stock.dashboard.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EmailService {

    private static final String RESEND_URL = "https://api.resend.com/emails";

    @Value("${resend.api.key}")
    private String apiKey;

    @Value("${resend.from.email}")
    private String fromEmail;

    @Value("${app.base.url}")
    private String baseUrl;

    private final ObjectMapper mapper = new ObjectMapper();

    public void sendVerificationEmail(String toEmail, String token) throws Exception {
        String verifyUrl = baseUrl + "/verify-email?token=" + token;

        String html = """
            <div style="font-family:sans-serif;max-width:480px;margin:0 auto">
              <h2 style="color:#00ff88">주식 대시보드 이메일 인증</h2>
              <p>아래 버튼을 클릭하여 이메일 인증을 완료해 주세요.</p>
              <a href="%s"
                 style="display:inline-block;padding:12px 24px;background:#00ff88;
                        color:#000;border-radius:8px;text-decoration:none;font-weight:bold">
                이메일 인증하기
              </a>
              <p style="color:#999;font-size:12px;margin-top:16px">
                본 링크는 24시간 동안 유효합니다.
              </p>
            </div>
            """.formatted(verifyUrl);

        String body = mapper.writeValueAsString(Map.of(
            "from",    fromEmail,
            "to",      new String[]{ toEmail },
            "subject", "[주식 대시보드] 이메일 인증을 완료해 주세요",
            "html",    html
        ));

        HttpResponse<String> response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder()
                .uri(URI.create(RESEND_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        log.info("[이메일] 인증 메일 발송: {} ({})", toEmail, response.statusCode());
    }

    public void sendPasswordResetEmail(String toEmail, String token) throws Exception {
        String resetUrl = baseUrl + "/reset-password?token=" + token;

        String html = """
            <div style="font-family:sans-serif;max-width:480px;margin:0 auto">
              <h2 style="color:#03C75A">주식 대시보드 비밀번호 재설정</h2>
              <p>아래 버튼을 클릭하여 비밀번호를 재설정해 주세요.</p>
              <a href="%s"
                 style="display:inline-block;padding:12px 24px;background:#03C75A;
                        color:#fff;border-radius:8px;text-decoration:none;font-weight:bold">
                비밀번호 재설정하기
              </a>
              <p style="color:#999;font-size:12px;margin-top:16px">
                본 링크는 1시간 동안 유효합니다. 본인이 요청하지 않은 경우 무시하세요.
              </p>
            </div>
            """.formatted(resetUrl);

        String body = mapper.writeValueAsString(Map.of(
            "from",    fromEmail,
            "to",      new String[]{ toEmail },
            "subject", "[주식 대시보드] 비밀번호 재설정 안내",
            "html",    html
        ));

        HttpResponse<String> response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder()
                .uri(URI.create(RESEND_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        log.info("[이메일] 비밀번호 재설정 메일 발송: {} ({})", toEmail, response.statusCode());
    }

    public void sendBulkCollectCompleteEmail(String toEmail, int total, String startDate, String endDate) throws Exception {
        String html = """
            <div style="font-family:sans-serif;max-width:480px;margin:0 auto">
              <h2 style="color:#00ff88">✅ 주식 과거 데이터 수집 완료</h2>
              <p>전체 종목 과거 데이터 수집이 완료되었습니다.</p>
              <table style="border-collapse:collapse;width:100%%">
                <tr><td style="padding:8px;color:#999">수집 기간</td><td style="padding:8px"><b>%s ~ %s</b></td></tr>
                <tr><td style="padding:8px;color:#999">수집 종목</td><td style="padding:8px"><b>%d 종목</b></td></tr>
              </table>
            </div>
            """.formatted(startDate, endDate, total);

        String body = mapper.writeValueAsString(Map.of(
            "from",    fromEmail,
            "to",      new String[]{ toEmail },
            "subject", "[주식 대시보드] 과거 데이터 수집 완료",
            "html",    html
        ));

        HttpResponse<String> response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder()
                .uri(URI.create(RESEND_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        log.info("[이메일] 수집 완료 알림 발송: {} ({})", toEmail, response.statusCode());
    }

    public void sendTempPasswordEmail(String toEmail, String tempPassword) throws Exception {
        String html = """
            <div style="font-family:sans-serif;max-width:480px;margin:0 auto">
              <h2 style="color:#4f8ef7">주식 대시보드 계정 복구</h2>
              <p>탈퇴된 계정이 복구되었습니다. 아래 임시 비밀번호로 로그인해주세요.</p>
              <div style="background:#f5f7fa;border-radius:8px;padding:16px 20px;margin:16px 0;text-align:center">
                <p style="margin:0 0 4px;color:#999;font-size:12px">임시 비밀번호</p>
                <p style="margin:0;font-size:20px;font-weight:bold;letter-spacing:2px;color:#333">%s</p>
              </div>
              <p style="color:#e24c4b;font-size:13px;font-weight:bold">
                로그인 후 반드시 비밀번호를 변경해주세요.
              </p>
              <p style="color:#999;font-size:12px;margin-top:16px">
                본인이 요청하지 않은 경우 이 이메일을 무시하세요.
              </p>
            </div>
            """.formatted(tempPassword);

        String body = mapper.writeValueAsString(Map.of(
            "from",    fromEmail,
            "to",      new String[]{ toEmail },
            "subject", "[주식 대시보드] 계정 복구 - 임시 비밀번호 안내",
            "html",    html
        ));

        HttpResponse<String> response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder()
                .uri(URI.create(RESEND_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        log.info("[이메일] 임시 비밀번호 발송: {} ({})", toEmail, response.statusCode());
    }
}