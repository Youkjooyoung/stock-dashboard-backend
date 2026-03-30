package com.stock.dashboard.dto;

import lombok.Data;

@Data
public class PortfolioDto {
    private int    portfolioId;
    private int    userId;
    private String ticker;
    private String stockName;
    private double quantity;   // 보유 수량 (소수점 가능)
    private long   buyPrice;   // 매수가 (원)
    private String buyDate;    // yyyyMMdd
    private String createdAt;
}
