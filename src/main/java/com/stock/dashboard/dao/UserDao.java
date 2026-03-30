package com.stock.dashboard.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.stock.dashboard.dto.UserDto;

@Mapper
public interface UserDao {
	int checkEmailExists(String email);

	int checkNicknameExists(String nickname);

	void deleteUser(int userId);

	void deleteWatchlist(int userId, int itemId);

	UserDto findByEmail(String email);

	UserDto findByEmailVerifyToken(String token);

	int insertUser(UserDto dto);

	void insertWatchlist(int userId, int itemId);

	void lockAccount(String email);

	void resetLoginFail(String email);

	List<Integer> selectUserIdsByWatchTicker(String ticker);

	List<Integer> selectWatchlist(int userId);

	void updateEmailVerified(String token);

	void updateLoginFail(String email);

	void updateNickname(UserDto dto);

	void updatePassword(UserDto dto);
}