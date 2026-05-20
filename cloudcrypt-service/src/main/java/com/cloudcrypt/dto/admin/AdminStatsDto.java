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
    private List<UserDiskMetricDto> users;
}