package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;


@Data // Esto genera Getters, Setters, toString, equals y hashCode automáticamente
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadRequestDto {
    private MultipartFile file;
    private String fileName;
    private Long parentId;
    private Long totalBatchSize;
    private String encryptedFileKey;
    private String checksum;
}