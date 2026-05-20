package com.cloudcrypt.repository.keys;

import com.cloudcrypt.model.FileEntity;
import com.cloudcrypt.model.FileKeyEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Optional<FileKeyEntity> findByFileIdAndUser_Username(Long fileId, String username);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("DELETE FROM FileKeyEntity fk WHERE fk.file.id = :fileId AND fk.user.username = :username")
    void deleteByFileIdAndUser_Username(@Param("fileId") Long fileId, @Param("username") String username);

    @Query("SELECT fk.user.username FROM FileKeyEntity fk WHERE fk.file.id = :fileId")
    List<String> findUsernamesByFileId(@Param("fileId") Long fileId);
}