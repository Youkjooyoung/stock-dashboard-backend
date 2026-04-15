package com.stock.dashboard.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stock.dashboard.dto.PriceAlertDto;
import com.stock.dashboard.service.PriceAlertService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/alert")
@RequiredArgsConstructor
public class PriceAlertController {

    private final PriceAlertService alertService;

    @PostMapping
    public ResponseEntity<String> addAlert(
            @AuthenticationPrincipal String email,
            @RequestBody PriceAlertDto dto) {
        alertService.addAlert(email, dto);
        return ResponseEntity.ok("알림 등록 완료");
    }

    @DeleteMapping("/{alertId}")
    public ResponseEntity<String> deleteAlert(
            @AuthenticationPrincipal String email,
            @PathVariable int alertId) {
        alertService.deleteAlert(email, alertId);
        return ResponseEntity.ok("알림 삭제 완료");
    }

    @GetMapping
    public ResponseEntity<List<PriceAlertDto>> getMyAlerts(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(alertService.getMyAlerts(email));
    }
}
