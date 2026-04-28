package com.example.repository.file;

import com.example.model.FileEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;


public interface FileRepository extends JpaRepository<FileEntity, Long>, FileRepositoryCustom {

    Page<FileEntity> findByOwner_Username(String username, Pageable pageable);
    Page<FileEntity> findByOwner_UsernameAndFolderPathAndDeletedAtIsNull(String username, String folderPath, Pageable pageable);
    @Query("SELECT f FROM FileEntity f WHERE f.owner.username = :username " +
            "AND (f.folderPath = :path OR f.folderPath LIKE CONCAT(:path, '/%'))")
    List<FileEntity> findAllByOwnerAndRecursivePath(
            @Param("username") String username,
            @Param("path") String path
    );
    boolean existsByOwner_UsernameAndFileNameAndFolderPathAndFileType(String username, String fileName, String folderPath, String fileType);
    Optional<FileEntity> findByOwner_UsernameAndFileNameAndFolderPathAndFileType(
            String username, String fileName, String folderPath, String fileType);
}