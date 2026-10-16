package com.stock.dashboard.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stock.dashboard.dto.StockItemDto;
import com.stock.dashboard.dto.StockPriceDto;

@Mapper
public interface StockDao {

	int insertStockItemIfNotExists(StockPriceDto dto);

	int insertStockPrice(StockPriceDto dto);

	List<StockPriceDto> selectLatestPrices();

	List<StockPriceDto> selectLatestPricesByItemIds(@Param("itemIds") List<Integer> itemIds);

	List<StockItemDto> selectAllItems();


	List<StockPriceDto> selectPriceByTicker(String ticker);

	List<StockPriceDto> selectPriceByTickerAndDate(@Param("ticker") String ticker, @Param("startDate") String startDate,
			@Param("endDate") String endDate);

	// startDate~endDate 범위 내에 데이터가 있는 티커 목록 (= 이미 과거 수집된 것으로 간주)
	List<String> selectCollectedTickers(@Param("startDate") String startDate, @Param("endDate") String endDate);
}