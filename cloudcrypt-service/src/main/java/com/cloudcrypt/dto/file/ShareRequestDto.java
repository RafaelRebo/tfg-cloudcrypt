package com.cloudcrypt.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShareRequestDto {
    private Long fileId;
    private String targetUsername;
    private String encryptedKey;
}
