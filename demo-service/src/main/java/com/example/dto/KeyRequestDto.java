package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data // Esto genera Getters, Setters, toString, equals y hashCode automáticamente
@NoArgsConstructor
@AllArgsConstructor
public class KeyRequestDto {
    private String publicKey;
    private String encryptedPrivateKey;
}