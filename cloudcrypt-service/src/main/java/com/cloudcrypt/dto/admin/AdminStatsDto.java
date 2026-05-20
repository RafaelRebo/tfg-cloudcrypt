package com.cloudcrypt.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsDto {
    private long globalUsedBytes;
    private List<UserDiskMetric> users;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDiskMetric {
        private Long userId;
        private String username;
        private String fullName;
        private long fileCount;
        private long usedBytes;
        private Long quotaBytes;
        private String role;
        private String avatarUrl;
    }
}