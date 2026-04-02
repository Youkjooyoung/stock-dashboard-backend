package com.stock.dashboard.dto;

import java.util.Date;

import lombok.Data;

@Data
public class AdminUserDto {
    private int userId;
    private String email;
    private String name;
    private String nickname;
    private String phone;
    private String emailVerified;
    private String accountLocked;
    private int loginFailCnt;
    private String role;
    private Date createdAt;
}
