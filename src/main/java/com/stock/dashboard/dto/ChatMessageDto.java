package com.stock.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

@Data
public class ChatMessageDto {
	private Long msgId;
	private String content;
	private String createdAt;
	private String nickname;
	private String ticker;
	// 내 메시지 구분용 공개 ID. 이메일 대신 사용한다.
	private Integer senderId;
	// DB 저장용. 다른 사용자에게 이메일이 노출되지 않도록 JSON 응답에서는 뺀다.
	@JsonIgnore
	private String userEmail;
}