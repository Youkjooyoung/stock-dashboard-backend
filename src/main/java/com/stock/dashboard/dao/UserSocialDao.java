package com.stock.dashboard.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.stock.dashboard.dto.UserSocialDto;

@Mapper
public interface UserSocialDao {
    void deleteSocial(int userId, String provider);
    void insertSocial(UserSocialDto dto);
    List<UserSocialDto> selectByUserId(int userId);
}
