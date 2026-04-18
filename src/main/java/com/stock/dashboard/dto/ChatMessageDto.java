package com.stock.dashboard.dto;

import lombok.Data;

@Data
public class ChatMessageDto {
	private Long msgId;
	private String content;
	private String createdAt;
	private String nickname;
	private String ticker;
	private String userEmail;
}