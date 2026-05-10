package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShareRequestDto {
    private String targetUsername;
    private String encryptedKey; // La AES del archivo cifrada con la pública del destinatario
}
