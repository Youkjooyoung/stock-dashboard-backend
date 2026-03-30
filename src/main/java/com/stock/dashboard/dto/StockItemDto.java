package com.stock.dashboard.dto;

import lombok.Data;

@Data
public class StockItemDto {
	private int itemId;
	private String itemNm;
	private String market;
	private String ticker;
}