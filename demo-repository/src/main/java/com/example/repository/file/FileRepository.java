package com.example.repository.file;

import com.example.model.FileEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<FileEntity, Long>, FileRepositoryCustom {

    // --- BÚSQUEDAS BÁSICAS (Se quedan IGUAL, no son paginados) ---
    @EntityGraph(attributePaths = {"fileKeys", "owner"})
    Optional<FileEntity> findByIdAndOwner_Username(Long id, String username);

    // --- CORRECCIÓN 1: Cambiado a {"owner"} ---
    @EntityGraph(attributePaths = {"owner"})
    Page<FileEntity> findByOwner_UsernameAndParentIsNullAndDeletedAtIsNull(String username, Pageable pageable);

    Optional<FileEntity> findByOwner_UsernameAndFileNameAndParentIdAndDeletedAtIsNull(String username, String fileName, Long parentId);
    Optional<FileEntity> findByOwner_UsernameAndFileNameAndParentIsNullAndDeletedAtIsNull(String username, String fileName);
    Optional<FileEntity> findByOwner_UsernameAndFileNameAndParentAndDeletedAtIsNull(String username, String fileName, FileEntity parent);
    Optional<FileEntity> findByOwner_UsernameAndFileNameAndFolderPathAndFileType(String username, String fileName, String folderPath, String fileType);

    // --- CORRECCIÓN 2: Cambiado a {"owner"} ---
    @EntityGraph(attributePaths = {"owner"})
    Page<FileEntity> findByOwner_UsernameAndParentId(String username, Long parentId, Pageable pageable);

    @EntityGraph(attributePaths = {"fileKeys", "owner"})
    @Query("SELECT f FROM FileEntity f WHERE f.owner.username = :username " +
            "AND (f.folderPath = :parentFullPath OR f.folderPath LIKE CONCAT(:parentFullPath, '/%') OR f.id = :folderId) " +
            "AND f.deletedAt IS NULL")
    List<FileEntity> findAllByOwnerAndRecursivePathList(
            @Param("username") String username,
            @Param("parentFullPath") String parentFullPath,
            @Param("folderId") Long folderId
    );

    // --- CORRECCIÓN 3: Cambiado a {"owner"} ---
    @EntityGraph(attributePaths = {"owner"})
    @Query("SELECT f FROM FileEntity f WHERE f.owner.username = :username " +
            "AND f.deletedAt IS NULL " +
            "AND f.fileType LIKE :mimePattern " +
            "AND f.fileType <> 'application/x-directory'")
    Page<FileEntity> findByCategory(@Param("username") String username,
                                    @Param("mimePattern") String mimePattern,
                                    Pageable pageable);

    // --- CORRECCIÓN 4: Cambiado a {"owner"} ---
    @EntityGraph(attributePaths = {"owner"})
    @Query("SELECT f FROM FileEntity f WHERE f.owner.username = :username " +
            "AND f.fileName LIKE CONCAT('%', :query, '%') " +
            "AND f.deletedAt IS NULL")
    Page<FileEntity> searchByName(@Param("username") String username,
                                  @Param("query") String query,
                                  Pageable pageable);

    // --- CORRECCIÓN 5: Cambiado a {"owner"} ---
    @EntityGraph(attributePaths = {"owner"})
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

    // --- CORRECCIÓN 6: Cambiado a {"owner"} ---
    @EntityGraph(attributePaths = {"owner"})
    @Query("SELECT DISTINCT f FROM FileEntity f " +
            "JOIN f.fileKeys fk " +
            "WHERE fk.user.username = :username " +
            "AND f.owner.username <> :username " +
            "AND f.deletedAt IS NULL " +
            "AND (" +
            "  (:parentId IS NULL AND NOT EXISTS (" +
            "    SELECT 1 FROM FileKeyEntity fk2 " +
            "    WHERE fk2.file.id = f.parent.id " +
            "    AND fk2.user.username = :username" +
            "  )) " +
            "  OR (f.parent.id = :parentId)" +
            ")")
    Page<FileEntity> findSharedWithMe(@Param("username") String username, @Param("parentId") Long parentId, Pageable pageable);

    @EntityGraph(attributePaths = {"fileKeys", "owner"})
    @Query("SELECT DISTINCT f FROM FileEntity f " +
            "LEFT JOIN f.fileKeys fk " +
            "WHERE f.id = :fileId " +
            "AND (f.owner.username = :username OR fk.user.username = :username)")
    Optional<FileEntity> findByIdAndHasAccess(@Param("fileId") Long fileId, @Param("username") String username);

    // --- CORRECCIÓN 7: Cambiado a {"owner"} ---
    @EntityGraph(attributePaths = {"owner"})
    @Query("SELECT f FROM FileEntity f WHERE f.owner.username = :username " +
            "AND f.deletedAt IS NULL " +
            "AND EXISTS (SELECT 1 FROM FileKeyEntity fk WHERE fk.file = f AND fk.user.username = :username AND fk.starred = true)")
    Page<FileEntity> findStarred(@Param("username") String username, Pageable pageable);
}