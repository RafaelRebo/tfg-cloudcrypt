package com.example.repository.file;

import com.example.model.FileEntity;
import com.example.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileRepository extends JpaRepository<FileEntity, Long>, FileRepositoryCustom {
    List<FileEntity> findByOwner_Username(String username);
}