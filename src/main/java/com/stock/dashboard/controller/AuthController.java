package com.stock.dashboard.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stock.dashboard.dto.UserLoginRequest;
import com.stock.dashboard.dto.UserSignupRequest;
import com.stock.dashboard.exception.BusinessException;
import com.stock.dashboard.service.OAuthLinkStateStore;
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

    private final UserService         userService;
    private final OAuthLinkStateStore linkStateStore;

    @PostMapping("/certify")
    public ResponseEntity<Map<String, Object>> certify(@RequestBody Map<String, String> body) throws Exception {
        return ResponseEntity.ok(userService.verifyCertification(body.get("impUid")));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody Map<String, String> body) throws Exception {
        userService.forgotPassword(body.get("email"));
        return ResponseEntity.ok(Map.of("message", "비밀번호 재설정 메일을 발송했습니다."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody Map<String, String> body) {
        userService.resetPassword(body.get("token"), body.get("password"));
        return ResponseEntity.ok(Map.of("message", "비밀번호가 변경되었습니다."));
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

    /**
     * 소셜 계정 연동 시작. 로그인한 사용자에게만 일회용 state 를 발급하고 이동할 URL 을 돌려준다.
     * (/api/auth/link/** 는 SecurityConfig 의 permitAll 목록에 없으므로 인증이 필요하다.)
     */
    @PostMapping("/link/{provider}/start")
    public ResponseEntity<Map<String, String>> startSocialLink(
            @AuthenticationPrincipal String email,
            @PathVariable String provider) {
        String normalized = provider.toUpperCase();
        String redirectUri = URLEncoder.encode(appBaseUrl + "/oauth/link", StandardCharsets.UTF_8);
        String authorizeUrl = switch (normalized) {
            case "GOOGLE" -> "https://accounts.google.com/o/oauth2/v2/auth?client_id=" + googleClientId
                    + "&redirect_uri=" + redirectUri + "&response_type=code&scope=openid%20email%20profile";
            case "KAKAO"  -> "https://kauth.kakao.com/oauth/authorize?client_id=" + kakaoClientId
                    + "&redirect_uri=" + redirectUri + "&response_type=code";
            default -> throw new BusinessException("지원하지 않는 소셜 서비스입니다.");
        };
        String state = linkStateStore.issue(email, normalized);
        return ResponseEntity.ok(Map.of("url", authorizeUrl + "&state=" + state));
    }

    /** 소셜 서비스에서 돌아온 code/state 로 연동을 마친다. state 를 발급받은 사용자 본인만 완료할 수 있다. */
    @PostMapping("/link/callback")
    public ResponseEntity<Map<String, Object>> completeSocialLink(
            @AuthenticationPrincipal String email,
            @RequestBody Map<String, String> body) throws Exception {
        OAuthLinkStateStore.LinkRequest request = linkStateStore.consume(body.get("state"));
        if (request == null || !request.email().equals(email)) {
            throw new BusinessException("연동 요청이 만료되었거나 올바르지 않습니다. 다시 시도해주세요.");
        }
        userService.linkSocial(request.email(), request.provider(), body.get("code"));
        return ResponseEntity.ok(Map.of("success", true, "provider", request.provider().toLowerCase()));
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
    public ResponseEntity<Map<String, Object>> login(@RequestBody UserLoginRequest req) {
        Map<String, String> tokens = userService.login(req);
        Map<String, Object> res = new HashMap<>(tokens);
        if ("Y".equals(tokens.get("forcePwChange"))) {
            res.put("forcePwChange", true);
        }
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

    @PostMapping("/resend-verify")
    public ResponseEntity<Map<String, String>> resendVerify(@RequestBody Map<String, String> body) throws Exception {
        userService.resendVerifyEmail(body.get("email"));
        return ResponseEntity.ok(Map.of("message", "인증 메일을 재발송했습니다."));
    }

    @PostMapping("/signup")
    public ResponseEntity<Map<String, String>> signup(@RequestBody UserSignupRequest dto) throws Exception {
        userService.signup(dto);
        return ResponseEntity.ok(Map.of("message", "이메일 인증 메일을 발송했습니다."));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        boolean result = userService.verifyEmail(token);
        if (!result) return ResponseEntity.badRequest().body(Map.of("message", "유효하지 않은 토큰입니다."));
        return ResponseEntity.ok(Map.of("message", "이메일 인증이 완료되었습니다."));
    }

    @PostMapping("/verify-identity")
    public ResponseEntity<Map<String, String>> verifyIdentity(
            @AuthenticationPrincipal String email,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(userService.verifyIdentityForAction(email, body.get("impUid")));
    }

    @PostMapping("/recover-account")
    public ResponseEntity<Map<String, String>> recoverAccount(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(userService.recoverAccount(
            body.get("email"), body.get("name"), body.get("phone")
        ));
    }

    @PostMapping("/check-deleted")
    public ResponseEntity<Map<String, Object>> checkDeleted(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(userService.checkDeletedAccount(body.get("email")));
    }

    private String buildOAuthRedirect(Map<String, String> tokens, String provider) throws Exception {
        return String.format(
            "%s/oauth#accessToken=%s&refreshToken=%s&email=%s&nickname=%s&provider=%s&role=%s&userId=%s",
            appBaseUrl,
            tokens.get("accessToken"),
            tokens.get("refreshToken"),
            tokens.get("email"),
            URLEncoder.encode(tokens.getOrDefault("nickname", ""), StandardCharsets.UTF_8),
            provider,
            tokens.getOrDefault("role", "USER"),
            tokens.getOrDefault("userId", "")
        );
    }
}