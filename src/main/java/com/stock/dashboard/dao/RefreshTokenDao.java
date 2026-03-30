package com.stock.dashboard.dao;

import org.apache.ibatis.annotations.Mapper;

import com.stock.dashboard.dto.RefreshTokenDto;

@Mapper
public interface RefreshTokenDao {
	int deleteByUserId(int userId);

	RefreshTokenDto findByToken(String token);

	int insertRefreshToken(RefreshTokenDto dto);
}