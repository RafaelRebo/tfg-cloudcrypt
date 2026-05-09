package com.example.repository.keys;

import com.example.model.FileKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileKeyRepository extends JpaRepository<FileKeyEntity, Long> {

    // Para que un usuario pueda recuperar su llave de un archivo concreto
    Optional<FileKeyEntity> findByFileIdAndUserId(Long fileId, Long userId);

    // Para comprobar si alguien tiene acceso (si hay llave, hay acceso)
    boolean existsByFileIdAndUserId(Long fileId, Long userId);
}