package com.stock.dashboard.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stock.dashboard.dto.PriceAlertDto;

@Mapper
public interface PriceAlertDao {
	int deleteAlert(@Param("alertId") int alertId, @Param("userId") int userId);

	int insertAlert(PriceAlertDto dto);

	List<PriceAlertDto> selectActiveAlerts();

	List<PriceAlertDto> selectByUserId(int userId);

	int triggerAlert(int alertId);
}