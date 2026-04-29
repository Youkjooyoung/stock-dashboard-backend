package com.stock.dashboard.dto;

import lombok.Data;

@Data
public class UserIdentityResultDto {
    private long   userId;
    private String email;
    private String provider;
}
