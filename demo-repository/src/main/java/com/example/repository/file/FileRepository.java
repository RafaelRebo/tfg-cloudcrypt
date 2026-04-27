package com.example.repository.file;

import com.example.model.FileEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


public interface FileRepository extends JpaRepository<FileEntity, Long>, FileRepositoryCustom {

    Page<FileEntity> findByOwner_Username(String username, Pageable pageable);
    Page<FileEntity> findByOwner_UsernameAndFolderPathAndDeletedAtIsNull(String username, String folderPath, Pageable pageable);
}