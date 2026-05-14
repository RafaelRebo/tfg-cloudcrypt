package com.example.repository.keys;

import com.example.model.FileKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileKeyRepository extends JpaRepository<FileKeyEntity, Long> {

    // NUEVO: Buscar llave por ID de archivo y NOMBRE de usuario
    Optional<FileKeyEntity> findByFileIdAndUser_Username(Long fileId, String username);

    // NUEVO: Comprobar existencia por nombre de usuario
    boolean existsByFileIdAndUser_Username(Long fileId, String username);

    // El que ya tenías (mantenlo si quieres, pero usaremos el de arriba)
    Optional<FileKeyEntity> findByFileIdAndUserId(Long fileId, Long userId);

    @Modifying
    @Transactional
    void deleteByFileIdAndUser_Username(Long fileId, String username);

    @Query("SELECT fk.user.username FROM FileKeyEntity fk WHERE fk.file.id = :fileId")
    List<String> findUsernamesByFileId(@Param("fileId") Long fileId);
}