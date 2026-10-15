package com.stock.dashboard.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
	// 알림 대상(웹소켓 사용자) 식별용. 응답에는 넣지 않는다.
	@JsonIgnore
	private String userEmail;
}