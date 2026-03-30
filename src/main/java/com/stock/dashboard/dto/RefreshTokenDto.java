package com.stock.dashboard.dto;

import java.util.Date;

import lombok.Data;

@Data
public class RefreshTokenDto {
	private Date expiredAt;
	private String token;
	private int tokenId;
	private int userId;
}