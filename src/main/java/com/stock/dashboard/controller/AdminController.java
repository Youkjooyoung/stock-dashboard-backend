package com.stock.dashboard.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stock.dashboard.dto.AdminUserDto;
import com.stock.dashboard.service.AdminService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    private String extractToken(String bearer) {
        return bearer != null && bearer.startsWith("Bearer ") ? bearer.substring(7) : bearer;
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserDto>> getUsers(@RequestHeader("Authorization") String bearer) {
        return ResponseEntity.ok(adminService.getAllUsers(extractToken(bearer)));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(@RequestHeader("Authorization") String bearer) {
        return ResponseEntity.ok(adminService.getStats(extractToken(bearer)));
    }

    @GetMapping("/watchlist/top")
    public ResponseEntity<List<Map<String, Object>>> getTopWatchlist(@RequestHeader("Authorization") String bearer) {
        return ResponseEntity.ok(adminService.getTopWatchlist(extractToken(bearer)));
    }

    @PostMapping("/users/{userId}/unlock")
    public ResponseEntity<Map<String, String>> unlockAccount(
            @RequestHeader("Authorization") String bearer,
            @PathVariable int userId) {
        adminService.unlockAccount(extractToken(bearer), userId);
        return ResponseEntity.ok(Map.of("message", "계정 잠금을 해제했습니다."));
    }

    @PostMapping("/users/{userId}/resend-verify")
    public ResponseEntity<Map<String, String>> resendVerify(
            @RequestHeader("Authorization") String bearer,
            @PathVariable int userId) throws Exception {
        adminService.resendVerifyEmail(extractToken(bearer), userId);
        return ResponseEntity.ok(Map.of("message", "인증 메일을 재발송했습니다."));
    }

    @PostMapping("/users/{userId}/role")
    public ResponseEntity<Map<String, String>> updateRole(
            @RequestHeader("Authorization") String bearer,
            @PathVariable int userId) {
        adminService.updateUserRole(extractToken(bearer), userId);
        return ResponseEntity.ok(Map.of("message", "권한을 변경했습니다."));
    }
}
