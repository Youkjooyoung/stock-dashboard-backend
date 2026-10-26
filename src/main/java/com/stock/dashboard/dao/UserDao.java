package com.stock.dashboard.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.stock.dashboard.dto.UserDto;
import com.stock.dashboard.dto.UserIdentityResultDto;

@Mapper
public interface UserDao {
	int checkEmailExists(String email);

	UserIdentityResultDto findActiveMemberByIdentity(
		@Param("name") String name,
		@Param("phoneNormalized") String phoneNormalized);

	int checkNicknameExists(String nickname);

	void deleteWatchlist(int userId, int itemId);

	UserDto findByEmail(String email);

	UserDto findByEmailVerifyToken(String token);

	UserDto findByPasswordResetToken(String token);

	void updatePasswordResetToken(UserDto dto);

	void clearPasswordResetToken(int userId);

	int insertUser(UserDto dto);

	void insertWatchlist(int userId, int itemId);

	void resetLoginFail(String email);

	List<String> selectUserEmailsByWatchTicker(String ticker);

	List<Integer> selectWatchlist(int userId);

	void updateEmailVerified(String token);

	void updateEmailVerifyToken(UserDto dto);

	void updateLoginFail(String email);

	void updateNickname(UserDto dto);

	void updatePassword(UserDto dto);

	UserDto findById(long userId);

	String findProfileImageUrl(long userId);

	void updateProfileImageUrl(@Param("userId") long userId, @Param("imageUrl") String imageUrl);

	UserDto findByEmailIncludeDeleted(String email);

	void softDeleteUser(@Param("userId") int userId, @Param("deleteReason") String deleteReason);

	void restoreUser(int userId);

	void updateIdentity(@Param("userId") int userId, @Param("name") String name, @Param("phone") String phone);

	void updateForcePwChange(@Param("userId") int userId, @Param("forcePwChange") String forcePwChange);
}
