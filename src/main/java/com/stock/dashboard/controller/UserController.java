package com.stock.dashboard.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stock.dashboard.dto.StockPriceDto;
import com.stock.dashboard.dto.UserSocialDto;
import com.stock.dashboard.service.UserService;

import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    @Value("${app.base.url}") private String appBaseUrl;

    private final UserService userService;

    @GetMapping("/kakao/callback")
    public void kakaoCallback(@RequestParam String code, HttpServletResponse response) throws Exception {
        Map<String, String> tokens = userService.kakaoLogin(code);
        String redirect = String.format(
            "%s/oauth#accessToken=%s&refreshToken=%s&email=%s&nickname=%s&provider=kakao",
            appBaseUrl,
            tokens.get("accessToken"),
            tokens.get("refreshToken"),
            tokens.get("email"),
            URLEncoder.encode(tokens.getOrDefault("nickname", ""), StandardCharsets.UTF_8)
        );
        response.sendRedirect(redirect);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleException(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @DeleteMapping("/account")
    public ResponseEntity<String> deleteAccount(@RequestHeader("Authorization") String token) {
        userService.deleteAccount(extractToken(token));
        return ResponseEntity.ok("탈퇴 완료");
    }

    @PostMapping("/watchlist/{itemId}")
    public ResponseEntity<String> addWatchlist(
            @RequestHeader("Authorization") String token,
            @PathVariable int itemId) {
        userService.addWatchlist(extractToken(token), itemId);
        return ResponseEntity.ok("추가 완료");
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, String>> getUserInfo(@RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(userService.getUserInfo(extractToken(token)));
    }

    @GetMapping("/social")
    public ResponseEntity<List<UserSocialDto>> getSocialLinks(@RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(userService.getSocialLinks(extractToken(token)));
    }

    @PostMapping("/social/link")
    public ResponseEntity<String> linkSocial(
            @RequestHeader("Authorization") String token,
            @RequestBody UserSocialDto dto) {
        userService.saveSocialLink(extractToken(token), dto);
        return ResponseEntity.ok("연동 완료");
    }

    @DeleteMapping("/social/unlink/{provider}")
    public ResponseEntity<String> unlinkSocial(
            @RequestHeader("Authorization") String token,
            @PathVariable String provider) {
        userService.unlinkSocial(extractToken(token), provider);
        return ResponseEntity.ok("연동 해제 완료");
    }

    @GetMapping("/nickname/check")
    public ResponseEntity<Map<String, Boolean>> checkNickname(@RequestParam String nickname) {
        boolean available = !userService.checkNicknameExists(nickname);
        return ResponseEntity.ok(Map.of("available", available));
    }

    @GetMapping("/watchlist")
    public ResponseEntity<List<Integer>> getWatchlist(@RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(userService.getWatchlist(extractToken(token)));
    }

    @GetMapping("/watchlist/detail")
    public ResponseEntity<List<StockPriceDto>> getWatchlistDetail(@RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(userService.getWatchlistDetail(extractToken(token)));
    }

    @PutMapping("/nickname")
    public ResponseEntity<String> updateNickname(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> body) {
        userService.updateNickname(extractToken(token), body.get("nickname"));
        return ResponseEntity.ok("닉네임 변경 완료");
    }

    @PutMapping("/password")
    public ResponseEntity<String> changePassword(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> body) {
        userService.changePassword(extractToken(token), body.get("currentPassword"), body.get("newPassword"));
        return ResponseEntity.ok("비밀번호 변경 완료");
    }

    @DeleteMapping("/watchlist/{itemId}")
    public ResponseEntity<String> removeWatchlist(
            @RequestHeader("Authorization") String token,
            @PathVariable int itemId) {
        userService.removeWatchlist(extractToken(token), itemId);
        return ResponseEntity.ok("삭제 완료");
    }

    private String extractToken(String header) {
        return header.replace("Bearer ", "");
    }
}