package com.stock.dashboard.dto;

import java.util.Date;

import lombok.Data;

@Data
public class UserSocialDto {
    private int    socialId;
    private int    userId;
    private String provider;
    private String providerEmail;
    private Date   createdAt;
}
