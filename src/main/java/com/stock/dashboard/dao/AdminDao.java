package com.stock.dashboard.dao;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

import com.stock.dashboard.dto.AdminUserDto;

@Mapper
public interface AdminDao {
    List<Map<String, Object>> selectAllAlerts();
    List<Map<String, Object>> selectAllChats();
    List<Map<String, Object>> selectAllStocks();
    List<AdminUserDto> selectAllUsers();
    Map<String, Object> selectStats();
    List<Map<String, Object>> selectTopWatchlist();
    void unlockAccount(int userId);
    void updateUserRole(int userId);
}
