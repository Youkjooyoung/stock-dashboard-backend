package com.stock.dashboard.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.stock.dashboard.dto.PortfolioDto;

@Mapper
public interface PortfolioDao {
    void insertPortfolio(PortfolioDto dto);
    List<PortfolioDto> selectByUserId(int userId);
    void deletePortfolio(int portfolioId, int userId);
}
