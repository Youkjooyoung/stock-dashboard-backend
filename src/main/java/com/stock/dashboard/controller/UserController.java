package com.stock.dashboard.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.stock.dashboard.dto.StockPriceDto;
import com.stock.dashboard.dto.UserSocialDto;
import com.stock.dashboard.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @DeleteMapping("/account")
    public ResponseEntity<String> deleteAccount(
            @AuthenticationPrincipal String email,
            @RequestBody Map<String, String> body) {
        userService.deleteAccount(email, body.get("verifyToken"), body.get("deleteReason"));
        return ResponseEntity.ok("탈퇴 완료");
    }

    @PostMapping("/watchlist/{itemId}")
    public ResponseEntity<String> addWatchlist(
            @AuthenticationPrincipal String email,
            @PathVariable int itemId) {
        userService.addWatchlist(email, itemId);
        return ResponseEntity.ok("추가 완료");
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, String>> getUserInfo(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(userService.getUserInfo(email));
    }

    @GetMapping("/social")
    public ResponseEntity<List<UserSocialDto>> getSocialLinks(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(userService.getSocialLinks(email));
    }

    @PostMapping("/social/link")
    public ResponseEntity<String> linkSocial(
            @AuthenticationPrincipal String email,
            @RequestBody UserSocialDto dto) {
        userService.saveSocialLink(email, dto);
        return ResponseEntity.ok("연동 완료");
    }

    @DeleteMapping("/social/unlink/{provider}")
    public ResponseEntity<String> unlinkSocial(
            @AuthenticationPrincipal String email,
            @PathVariable String provider) {
        userService.unlinkSocial(email, provider);
        return ResponseEntity.ok("연동 해제 완료");
    }

    @GetMapping("/nickname/check")
    public ResponseEntity<Map<String, Boolean>> checkNickname(@RequestParam String nickname) {
        boolean available = !userService.checkNicknameExists(nickname);
        return ResponseEntity.ok(Map.of("available", available));
    }

    @GetMapping("/watchlist")
    public ResponseEntity<List<Integer>> getWatchlist(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(userService.getWatchlist(email));
    }

    @GetMapping("/watchlist/detail")
    public ResponseEntity<List<StockPriceDto>> getWatchlistDetail(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(userService.getWatchlistDetail(email));
    }

    @PutMapping("/nickname")
    public ResponseEntity<String> updateNickname(
            @AuthenticationPrincipal String email,
            @RequestBody Map<String, String> body) {
        userService.updateNickname(email, body.get("nickname"));
        return ResponseEntity.ok("닉네임 변경 완료");
    }

    @PutMapping("/password")
    public ResponseEntity<String> changePassword(
            @AuthenticationPrincipal String email,
            @RequestBody Map<String, String> body) {
        userService.changePassword(email, body.get("verifyToken"), body.get("newPassword"));
        return ResponseEntity.ok("비밀번호 변경 완료");
    }

    @DeleteMapping("/watchlist/{itemId}")
    public ResponseEntity<String> removeWatchlist(
            @AuthenticationPrincipal String email,
            @PathVariable int itemId) {
        userService.removeWatchlist(email, itemId);
        return ResponseEntity.ok("삭제 완료");
    }

    @PutMapping("/profile-image")
    public ResponseEntity<Map<String, String>> updateProfileImage(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal String email) throws java.io.IOException {
        long userId = userService.getUserId(email);
        String imageUrl = userService.updateProfileImage(userId, file);
        return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
    }
}
