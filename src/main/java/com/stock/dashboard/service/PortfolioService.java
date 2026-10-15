package com.stock.dashboard.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.stock.dashboard.dao.PortfolioDao;
import com.stock.dashboard.dao.UserDao;
import com.stock.dashboard.dto.PortfolioDto;
import com.stock.dashboard.dto.UserDto;
import com.stock.dashboard.exception.BusinessException;

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
        UserDto user = email != null ? userDao.findByEmail(email) : null;
        if (user == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "사용자 정보를 찾을 수 없습니다. 다시 로그인해주세요.");
        return user;
    }
}
