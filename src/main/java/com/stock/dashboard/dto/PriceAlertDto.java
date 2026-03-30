package com.stock.dashboard.dto;

import java.util.Date;

import lombok.Data;

@Data
public class PriceAlertDto {
	private int alertId;
	private String alertType;
	private Date createdAt;
	private String isTriggered;
	private int itemId;
	private String stockName;
	private long targetPrice;
	private String ticker;
	private Date triggeredAt;
	private int userId;
}