package com.example.repository.file;

import com.example.model.FileEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;


public interface FileRepository extends JpaRepository<FileEntity, Long>, FileRepositoryCustom {

    Optional<FileEntity> findByIdAndOwner_Username(Long id, String username);
    Page<FileEntity> findByOwner_UsernameAndFolderPathAndDeletedAtIsNull(String username, String folderPath, Pageable pageable);
    @Query("SELECT f FROM FileEntity f WHERE f.owner.username = :username " +
            "AND (f.folderPath = :path OR f.folderPath LIKE CONCAT(:path, '/%'))")
    Stream<FileEntity> findAllByOwnerAndRecursivePath(
            @Param("username") String username,
            @Param("path") String path
    );
    Page<FileEntity> findByOwner_UsernameAndFolderPathAndDeletedAtIsNotNull(String username, String folderPath, Pageable pageable);
    boolean existsByOwner_UsernameAndFileNameAndFolderPathAndFileType(String username, String fileName, String folderPath, String fileType);
    Optional<FileEntity> findByOwner_UsernameAndFileNameAndFolderPathAndFileType(
            String username, String fileName, String folderPath, String fileType);

    @Query("SELECT f FROM FileEntity f WHERE f.owner.username = :username " +
            "AND f.deletedAt IS NULL " +
            "AND f.fileType LIKE :mimePattern " +
            "AND f.fileType <> 'application/x-directory'")
    Page<FileEntity> findByCategory(@Param("username") String username,
                                    @Param("mimePattern") String mimePattern,
                                    Pageable pageable);


    @Query("SELECT f FROM FileEntity f WHERE f.owner.username = :username " +
            "AND f.fileName LIKE CONCAT('%', :query, '%') " +
            "AND f.deletedAt IS NULL")
    Page<FileEntity> searchByName(@Param("username") String username,
                                  @Param("query") String query,
                                  Pageable pageable);

    // En FileRepository.java
    @Query("SELECT f FROM FileEntity f WHERE f.owner.username = :username " +
            "AND f.deletedAt IS NOT NULL " +
            "AND NOT EXISTS ( " +
            "  SELECT p FROM FileEntity p WHERE p.owner.username = :username " +
            "  AND p.fileType = 'application/x-directory' " +
            "  AND p.deletedAt IS NOT NULL " +
            "  AND f.folderPath = CASE " +
            "      WHEN p.folderPath = '/' THEN CONCAT('/', p.fileName) " +
            "      ELSE CONCAT(p.folderPath, '/', p.fileName) END " +
            ")")
    Page<FileEntity> findTrashRoot(@Param("username") String username, Pageable pageable);
}