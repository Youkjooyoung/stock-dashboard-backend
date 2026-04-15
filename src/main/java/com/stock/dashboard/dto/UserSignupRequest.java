package com.stock.dashboard.dto;

import lombok.Data;

@Data
public class UserSignupRequest {
    private String email;
    private String password;
    private String name;
    private String nickname;
    private String phone;
    private String address;
    private String addressDetail;
    private String residentNo;
    private String impUid;
}
