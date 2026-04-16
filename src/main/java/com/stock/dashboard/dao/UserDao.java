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

	UserDto findByPasswordResetToken(String token);

	void updatePasswordResetToken(UserDto dto);

	void clearPasswordResetToken(int userId);

	int insertUser(UserDto dto);

	void insertWatchlist(int userId, int itemId);

	void lockAccount(String email);

	void resetLoginFail(String email);

	List<Integer> selectUserIdsByWatchTicker(String ticker);

	List<Integer> selectWatchlist(int userId);

	void updateEmailVerified(String token);

	void updateEmailVerifyToken(UserDto dto);

	void updateLoginFail(String email);

	void updateNickname(UserDto dto);

	void updatePassword(UserDto dto);

	UserDto findById(long userId);

	String findProfileImageUrl(long userId);

        void updateProfileImageUrl(@org.apache.ibatis.annotations.Param("userId") long userId, @org.apache.ibatis.annotations.Param("imageUrl") String imageUrl);

	UserDto findByEmailIncludeDeleted(String email);

	void softDeleteUser(@org.apache.ibatis.annotations.Param("userId") int userId, @org.apache.ibatis.annotations.Param("deleteReason") String deleteReason);

	void restoreUser(int userId);

	void updateForcePwChange(@org.apache.ibatis.annotations.Param("userId") int userId, @org.apache.ibatis.annotations.Param("forcePwChange") String forcePwChange);
}
