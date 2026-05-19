package com.cloudcrypt.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeyRequestDto {
    private String publicKey;
    private String encryptedPrivateKey;
}