package com.example.repository;

import com.example.model.FileEntity;
import com.example.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, Long> {

    // Spring generará: SELECT * FROM files WHERE user_id = ?
    List<FileEntity> findByOwner(UserEntity owner);
}