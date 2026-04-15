package com.stock.dashboard.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.stock.dashboard.dao.PortfolioDao;
import com.stock.dashboard.dao.UserDao;
import com.stock.dashboard.dto.PortfolioDto;
import com.stock.dashboard.dto.UserDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioDao portfolioDao;
    private final UserDao      userDao;

    public List<PortfolioDto> getMyPortfolio(String email) {
        UserDto user = getUser(email);
        return portfolioDao.selectByUserId(user.getUserId());
    }

    public void addPortfolio(String email, PortfolioDto dto) {
        UserDto user = getUser(email);
        dto.setUserId(user.getUserId());
        portfolioDao.insertPortfolio(dto);
    }

    public void deletePortfolio(String email, int portfolioId) {
        UserDto user = getUser(email);
        portfolioDao.deletePortfolio(portfolioId, user.getUserId());
    }

    private UserDto getUser(String email) {
        return userDao.findByEmail(email);
    }
}
