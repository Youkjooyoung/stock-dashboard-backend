package com.stock.dashboard.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @GetMapping("/alerts")
    public ResponseEntity<List<Map<String, Object>>> getAlerts() {
        return ResponseEntity.ok(adminService.getAllAlerts());
    }

    @GetMapping("/chats")
    public ResponseEntity<List<Map<String, Object>>> getChats() {
        return ResponseEntity.ok(adminService.getAllChats());
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    @GetMapping("/stocks")
    public ResponseEntity<List<Map<String, Object>>> getStocks() {
        return ResponseEntity.ok(adminService.getAllStocks());
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserDto>> getUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @GetMapping("/watchlist/top")
    public ResponseEntity<List<Map<String, Object>>> getTopWatchlist() {
        return ResponseEntity.ok(adminService.getTopWatchlist());
    }

    @PostMapping("/users/{userId}/resend-verify")
    public ResponseEntity<Map<String, String>> resendVerify(@PathVariable int userId) throws Exception {
        adminService.resendVerifyEmail(userId);
        return ResponseEntity.ok(Map.of("message", "인증 메일을 재발송했습니다."));
    }

    @PostMapping("/users/{userId}/role")
    public ResponseEntity<Map<String, String>> updateRole(@PathVariable int userId) {
        adminService.updateUserRole(userId);
        return ResponseEntity.ok(Map.of("message", "권한을 변경했습니다."));
    }

    @PostMapping("/users/{userId}/unlock")
    public ResponseEntity<Map<String, String>> unlockAccount(@PathVariable int userId) {
        adminService.unlockAccount(userId);
        return ResponseEntity.ok(Map.of("message", "계정 잠금을 해제했습니다."));
    }
}
