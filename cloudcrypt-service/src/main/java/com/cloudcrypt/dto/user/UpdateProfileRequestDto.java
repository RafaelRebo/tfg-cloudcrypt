package com.cloudcrypt.dto.user;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UpdateProfileRequestDto {
    private String fullName;
    private String removeAvatar;
    private String newUsername;
    private String newPassword;
    private String newEncryptedPrivateKey;
    private String email;
    private MultipartFile avatar;
}