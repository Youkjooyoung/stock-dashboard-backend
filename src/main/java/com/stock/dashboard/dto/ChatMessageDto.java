package com.stock.dashboard.dto;

import java.util.Date;

import lombok.Data;

@Data
public class ChatMessageDto {
	private Long msgId;
	private String content;
	private Date createdAt;
	private String nickname;
	private String ticker;
	private String userEmail;
}