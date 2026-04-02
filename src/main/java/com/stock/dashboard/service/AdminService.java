package com.stock.dashboard.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.stock.dashboard.JwtUtil;
import com.stock.dashboard.dao.AdminDao;
import com.stock.dashboard.dao.UserDao;
import com.stock.dashboard.dto.AdminUserDto;
import com.stock.dashboard.dto.UserDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminDao    adminDao;
    private final EmailService emailService;
    private final JwtUtil     jwtUtil;
    private final UserDao     userDao;

    public List<AdminUserDto> getAllUsers(String token) {
        checkAdmin(token);
        return adminDao.selectAllUsers();
    }

    public Map<String, Object> getStats(String token) {
        checkAdmin(token);
        return adminDao.selectStats();
    }

    public List<Map<String, Object>> getTopWatchlist(String token) {
        checkAdmin(token);
        return adminDao.selectTopWatchlist();
    }

    public void unlockAccount(String token, int userId) {
        checkAdmin(token);
        adminDao.unlockAccount(userId);
    }

    public void resendVerifyEmail(String token, int userId) throws Exception {
        checkAdmin(token);
        UserDto user = userDao.findById(userId);
        if (user == null) throw new RuntimeException("존재하지 않는 회원입니다.");
        if ("Y".equals(user.getEmailVerified())) throw new RuntimeException("이미 인증된 회원입니다.");
        emailService.sendVerificationEmail(user.getEmail(), user.getEmailVerifyToken());
    }

    public void updateUserRole(String token, int userId) {
        checkAdmin(token);
        adminDao.updateUserRole(userId);
    }

    private void checkAdmin(String token) {
        String email = jwtUtil.getEmailFromAccess(token);
        UserDto user = userDao.findByEmail(email);
        if (user == null || !"ADMIN".equals(user.getRole()))
            throw new SecurityException("관리자 권한이 필요합니다.");
    }
}
