package com.stock.dashboard.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.stock.dashboard.dao.AdminDao;
import com.stock.dashboard.dao.UserDao;
import com.stock.dashboard.dto.AdminUserDto;
import com.stock.dashboard.dto.UserDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminDao     adminDao;
    private final EmailService emailService;
    private final UserDao      userDao;

    public List<Map<String, Object>> getAllAlerts() {
        return adminDao.selectAllAlerts();
    }

    public List<Map<String, Object>> getAllChats() {
        return adminDao.selectAllChats();
    }

    public List<Map<String, Object>> getAllStocks() {
        return adminDao.selectAllStocks();
    }

    public List<AdminUserDto> getAllUsers() {
        return adminDao.selectAllUsers();
    }

    public Map<String, Object> getStats() {
        return adminDao.selectStats();
    }

    public List<Map<String, Object>> getTopWatchlist() {
        return adminDao.selectTopWatchlist();
    }

    public void resendVerifyEmail(int userId) throws Exception {
        UserDto user = userDao.findById(userId);
        if (user == null) throw new RuntimeException("존재하지 않는 회원입니다.");
        if ("Y".equals(user.getEmailVerified())) throw new RuntimeException("이미 인증된 회원입니다.");
        emailService.sendVerificationEmail(user.getEmail(), user.getEmailVerifyToken());
    }

    public void unlockAccount(int userId) {
        adminDao.unlockAccount(userId);
    }

    public void updateUserRole(int userId) {
        adminDao.updateUserRole(userId);
    }
}
