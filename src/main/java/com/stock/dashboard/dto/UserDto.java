package com.stock.dashboard.dto;

import java.util.Date;

import lombok.Data;

@Data
public class UserDto {
	private int userId;
	private String email;
	private String password;
	private String name;
	private String nickname;
	private String phone;
	private String address;
	private String addressDetail;
	private String residentNo;
	private String emailVerified;
	private String emailVerifyToken;
	private int loginFailCnt;
	private String accountLocked;
	private Date createdAt;
}