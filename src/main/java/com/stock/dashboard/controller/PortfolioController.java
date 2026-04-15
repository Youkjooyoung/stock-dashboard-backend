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

import com.stock.dashboard.dto.PortfolioDto;
import com.stock.dashboard.service.PortfolioService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping
    public ResponseEntity<List<PortfolioDto>> getMyPortfolio(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(portfolioService.getMyPortfolio(email));
    }

    @PostMapping
    public ResponseEntity<Void> addPortfolio(
            @AuthenticationPrincipal String email,
            @RequestBody PortfolioDto dto) {
        portfolioService.addPortfolio(email, dto);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePortfolio(
            @AuthenticationPrincipal String email,
            @PathVariable("id") int portfolioId) {
        portfolioService.deletePortfolio(email, portfolioId);
        return ResponseEntity.ok().build();
    }
}
