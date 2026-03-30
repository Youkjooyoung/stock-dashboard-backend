package com.stock.dashboard.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.stock.dashboard.JwtUtil;
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
    private final JwtUtil      jwtUtil;

    public List<PortfolioDto> getMyPortfolio(String token) {
        UserDto user = getUser(token);
        return portfolioDao.selectByUserId(user.getUserId());
    }

    public void addPortfolio(String token, PortfolioDto dto) {
        UserDto user = getUser(token);
        dto.setUserId(user.getUserId());
        portfolioDao.insertPortfolio(dto);
    }

    public void deletePortfolio(String token, int portfolioId) {
        UserDto user = getUser(token);
        portfolioDao.deletePortfolio(portfolioId, user.getUserId());
    }

    private UserDto getUser(String token) {
        return userDao.findByEmail(jwtUtil.getEmailFromAccess(token));
    }
}
