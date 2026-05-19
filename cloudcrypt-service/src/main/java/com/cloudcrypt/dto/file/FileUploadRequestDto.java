package com.cloudcrypt.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;


@Data
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