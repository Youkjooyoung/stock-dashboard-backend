package com.stock.dashboard.dao;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

import com.stock.dashboard.dto.AdminUserDto;

@Mapper
public interface AdminDao {
    List<AdminUserDto> selectAllUsers();
    void unlockAccount(int userId);
    void updateUserRole(int userId);
    Map<String, Object> selectStats();
    List<Map<String, Object>> selectTopWatchlist();
}
