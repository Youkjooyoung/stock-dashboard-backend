package com.stock.dashboard.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stock.dashboard.dto.UserSocialDto;

@Mapper
public interface UserSocialDao {
    void deleteSocial(@Param("userId") int userId, @Param("provider") String provider);
    void insertSocial(UserSocialDto dto);
    List<UserSocialDto> selectByUserId(int userId);
    int checkSocialLink(@Param("userId") int userId, @Param("provider") String provider);
    UserSocialDto selectByProviderAndEmail(@Param("provider") String provider, @Param("providerEmail") String providerEmail);
}
