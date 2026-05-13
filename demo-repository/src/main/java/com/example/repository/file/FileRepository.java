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

    // --- BÚSQUEDAS BÁSICAS ---
    Optional<FileEntity> findByIdAndOwner_Username(Long id, String username);

    // --- NAVEGACIÓN POR JERARQUÍA (ID) ---
    Page<FileEntity> findByOwner_UsernameAndParentIdAndDeletedAtIsNull(String username, Long parentId, Pageable pageable);

    Page<FileEntity> findByOwner_UsernameAndParentIsNullAndDeletedAtIsNull(String username, Pageable pageable);

    // --- NAVEGACIÓN POR RUTA (STRING) - Necesario para Breadcrumbs y compatibilidad ---
    Page<FileEntity> findByOwner_UsernameAndFolderPathAndDeletedAtIsNull(String username, String folderPath, Pageable pageable);

    // --- GESTIÓN DE DUPLICADOS ---
    // Buscar por ID de padre (Ficheros)
    Optional<FileEntity> findByOwner_UsernameAndFileNameAndParentIdAndDeletedAtIsNull(String username, String fileName, Long parentId);

    // Buscar en la raíz (Ficheros)
    Optional<FileEntity> findByOwner_UsernameAndFileNameAndParentIsNullAndDeletedAtIsNull(String username, String fileName);

    // Buscar por Objeto Parent y Tipo (Para FolderService.ensureExists)
    Optional<FileEntity> findByOwner_UsernameAndFileNameAndParentAndDeletedAtIsNull(String username, String fileName, FileEntity parent);

    // Buscar por String de ruta y Tipo (Para lógica antigua de carpetas)
    boolean existsByOwner_UsernameAndFileNameAndFolderPathAndFileType(String username, String fileName, String folderPath, String fileType);

    Optional<FileEntity> findByOwner_UsernameAndFileNameAndFolderPathAndFileType(String username, String fileName, String folderPath, String fileType);

    // Obtener todos para borrado masivo (Overwrite)
    List<FileEntity> findAllByOwner_UsernameAndFileNameAndParentIdAndDeletedAtIsNull(String username, String fileName, Long parentId);

    List<FileEntity> findAllByOwner_UsernameAndFileNameAndParentIsNullAndDeletedAtIsNull(String username, String fileName);

    // Reemplazo de findAllByOwner_UsernameAndFileNameAndFolderPathAndDeletedAtIsNull para FileService
    List<FileEntity> findAllByOwner_UsernameAndFileNameAndFolderPathAndDeletedAtIsNull(String username, String fileName, String folderPath);

    // --- VALIDACIÓN DE EXISTENCIA ---
    boolean existsByOwner_UsernameAndFileNameAndParentIdAndDeletedAtIsNull(String username, String fileName, Long parentId);

    boolean existsByOwner_UsernameAndFileNameAndParentIsNullAndDeletedAtIsNull(String username, String fileName);

    boolean existsByOwner_UsernameAndFileNameAndFolderPathAndDeletedAtIsNull(String username, String fileName, String folderPath);

    boolean existsByOwner_UsernameAndFileNameAndFolderPathAndFileTypeNotAndDeletedAtIsNull(String username, String fileName, String folderPath, String fileTypeNot);

    // Para navegar dentro de carpetas borradas
    Page<FileEntity> findByOwner_UsernameAndParentId(String username, Long parentId, Pageable pageable);
    // --- RECURSIVIDAD Y CATEGORÍAS ---
    @Query("SELECT f FROM FileEntity f WHERE f.owner.username = :username " +
            "AND (f.folderPath = :path OR f.folderPath LIKE CONCAT(:path, '/%'))")
    Stream<FileEntity> findAllByOwnerAndRecursivePath(
            @Param("username") String username,
            @Param("path") String path
    );

    // --- NUEVO: Para compartir carpetas recursivamente ---
    @Query("SELECT f FROM FileEntity f WHERE f.owner.username = :username " +
            "AND (f.folderPath = :parentFullPath OR f.folderPath LIKE CONCAT(:parentFullPath, '/%') OR f.id = :folderId) " +
            "AND f.deletedAt IS NULL")
    List<FileEntity> findAllByOwnerAndRecursivePathList(
            @Param("username") String username,
            @Param("parentFullPath") String parentFullPath,
            @Param("folderId") Long folderId
    );

    Page<FileEntity> findByOwner_UsernameAndFolderPathAndDeletedAtIsNotNull(String username, String folderPath, Pageable pageable);

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

    // --- PAPELERA ---
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

    // En FileRepository.java
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

    // En FileRepository.java
    @Query("SELECT DISTINCT f FROM FileEntity f " +
            "LEFT JOIN f.fileKeys fk " +
            "WHERE f.id = :fileId " +
            "AND (f.owner.username = :username OR fk.user.username = :username)")
    Optional<FileEntity> findByIdAndHasAccess(@Param("fileId") Long fileId, @Param("username") String username);
}