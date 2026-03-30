package com.stock.dashboard.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stock.dashboard.dto.UserDto;
import com.stock.dashboard.service.UserService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    @Value("${kakao.client-id}")     private String kakaoClientId;
    @Value("${kakao.redirect-uri}")  private String kakaoRedirectUri;
    @Value("${google.client-id}")    private String googleClientId;
    @Value("${google.redirect-uri}") private String googleRedirectUri;
    @Value("${app.base.url}")        private String appBaseUrl;

    private final UserService userService;

    @PostMapping("/certify")
    public ResponseEntity<Map<String, String>> certify(@RequestBody Map<String, String> body) throws Exception {
        return ResponseEntity.ok(userService.verifyCertification(body.get("impUid")));
    }

    @PostMapping("/check-email")
    public ResponseEntity<Map<String, Boolean>> checkEmail(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(Map.of("exists", userService.checkEmailExists(body.get("email"))));
    }

    @GetMapping("/google/callback")
    public void googleCallback(@RequestParam String code, HttpServletResponse response) throws Exception {
        response.sendRedirect(buildOAuthRedirect(userService.googleLogin(code), "google"));
    }

    @GetMapping("/google/login")
    public void googleLogin(HttpServletResponse response) throws Exception {
        response.sendRedirect(String.format(
            "https://accounts.google.com/o/oauth2/v2/auth?client_id=%s&redirect_uri=%s&response_type=code&scope=openid%%20email%%20profile&access_type=offline",
            googleClientId, googleRedirectUri
        ));
    }

    @GetMapping("/kakao/callback")
    public void kakaoCallback(@RequestParam String code, HttpServletResponse response) throws Exception {
        response.sendRedirect(buildOAuthRedirect(userService.kakaoLogin(code), "kakao"));
    }

    @GetMapping("/kakao/exchange")
    public ResponseEntity<Map<String, String>> kakaoExchange(@RequestParam String code) throws Exception {
        return ResponseEntity.ok(userService.kakaoLogin(code));
    }

    @GetMapping("/google/exchange")
    public ResponseEntity<Map<String, String>> googleExchange(@RequestParam String code) throws Exception {
        return ResponseEntity.ok(userService.googleLogin(code));
    }

    @GetMapping("/kakao/login")
    public void kakaoLogin(HttpServletResponse response) throws Exception {
        response.sendRedirect(String.format(
            "https://kauth.kakao.com/oauth/authorize?client_id=%s&redirect_uri=%s&response_type=code",
            kakaoClientId, kakaoRedirectUri
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody UserDto dto) {
        Map<String, String> tokens = userService.login(dto);
        UserDto user = userService.findByEmail(dto.getEmail());
        Map<String, Object> res = new HashMap<>(tokens);
        res.put("userId", user.getUserId());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestBody Map<String, String> body) {
        userService.logout(body.get("refreshToken"));
        return ResponseEntity.ok("로그아웃 완료");
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(userService.refresh(body.get("refreshToken")));
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody UserDto dto) {
        return ResponseEntity.ok(userService.register(dto));
    }

    @PostMapping("/signup")
    public ResponseEntity<Map<String, String>> signup(@RequestBody UserDto dto) throws Exception {
        userService.signup(dto);
        return ResponseEntity.ok(Map.of("message", "이메일 인증 메일을 발송했습니다."));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        boolean result = userService.verifyEmail(token);
        if (!result) return ResponseEntity.badRequest().body(Map.of("message", "유효하지 않은 토큰입니다."));
        return ResponseEntity.ok(Map.of("message", "이메일 인증이 완료되었습니다."));
    }

    private String buildOAuthRedirect(Map<String, String> tokens, String provider) throws Exception {
        // 토큰을 URL 쿼리가 아닌 fragment(#)로 전달 → 서버 로그/리퍼러에 노출되지 않음
        return String.format(
            "%s/oauth#accessToken=%s&refreshToken=%s&email=%s&nickname=%s&provider=%s",
            appBaseUrl,
            tokens.get("accessToken"),
            tokens.get("refreshToken"),
            tokens.get("email"),
            URLEncoder.encode(tokens.getOrDefault("nickname", ""), StandardCharsets.UTF_8),
            provider
        );
    }
}