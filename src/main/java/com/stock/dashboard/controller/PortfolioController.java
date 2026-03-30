package com.stock.dashboard.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

    /** 내 포트폴리오 조회 */
    @GetMapping
    public ResponseEntity<List<PortfolioDto>> getMyPortfolio(
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return ResponseEntity.ok(portfolioService.getMyPortfolio(token));
    }

    /** 보유 종목 추가 */
    @PostMapping
    public ResponseEntity<Void> addPortfolio(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody PortfolioDto dto) {
        String token = authHeader.replace("Bearer ", "");
        portfolioService.addPortfolio(token, dto);
        return ResponseEntity.ok().build();
    }

    /** 보유 종목 삭제 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePortfolio(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable("id") int portfolioId) {
        String token = authHeader.replace("Bearer ", "");
        portfolioService.deletePortfolio(token, portfolioId);
        return ResponseEntity.ok().build();
    }
}
