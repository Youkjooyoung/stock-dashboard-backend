package com.stock.dashboard.dto;

import lombok.Data;

@Data
public class NewsDto {
	private String description;
	private String link;
	private String originallink;
	private String pubDate;
	private String title;
}