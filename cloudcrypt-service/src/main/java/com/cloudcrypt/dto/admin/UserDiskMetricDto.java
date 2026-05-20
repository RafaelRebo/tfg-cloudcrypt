package com.cloudcrypt.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDiskMetricDto {
    private Long userId;
    private String username;
    private String fullName;
    private long fileCount;
    private long usedBytes;
    private Long quotaBytes;
    private String role;
    private String avatarUrl;
}