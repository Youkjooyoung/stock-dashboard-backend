package com.stock.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockPriceDto {
	private String basDt;
	private long clpr;
	private long hipr;
	private int itemId;
	private String itmsNm;
	private long lopr;
	private long mkp;
	private String mrktCtg;
	private String srtnCd;
	private long trqu;
}