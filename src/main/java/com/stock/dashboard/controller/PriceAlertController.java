package com.stock.dashboard.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.stock.dashboard.dto.PriceAlertDto;
import com.stock.dashboard.service.PriceAlertService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/alert")
@RequiredArgsConstructor
public class PriceAlertController {

    private final PriceAlertService alertService;

    private String extractToken(String header) {
        return header.replace("Bearer ", "");
    }

    @PostMapping
    public ResponseEntity<String> addAlert(
            @RequestHeader("Authorization") String token,
            @RequestBody PriceAlertDto dto) {
        alertService.addAlert(extractToken(token), dto);
        return ResponseEntity.ok("알림 등록 완료");
    }

    @DeleteMapping("/{alertId}")
    public ResponseEntity<String> deleteAlert(
            @RequestHeader("Authorization") String token,
            @PathVariable int alertId) {
        alertService.deleteAlert(extractToken(token), alertId);
        return ResponseEntity.ok("알림 삭제 완료");
    }

    @GetMapping
    public ResponseEntity<List<PriceAlertDto>> getMyAlerts(
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(alertService.getMyAlerts(extractToken(token)));
    }
}