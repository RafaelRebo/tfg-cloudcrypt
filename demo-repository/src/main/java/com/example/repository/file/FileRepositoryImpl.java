package com.example.repository.file;

import com.example.model.FileEntity;
import com.example.model.UserEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public class FileRepositoryImpl implements FileRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public FileEntity createFile(String name, String folderPath, String type, long size,
                                 String checksum, String storagePath, UserEntity owner, String salt) {
        FileEntity entity = new FileEntity();
        entity.setFileName(name);
        entity.setFolderPath(folderPath);
        entity.setFileType(type);
        entity.setFileSize(size);
        entity.setChecksum(checksum);
        entity.setStoragePath(storagePath);
        entity.setOwner(owner); // Asignación directa
        entity.setSalt(salt);

        entityManager.persist(entity);
        return entity;
    }

    @Override
    @Transactional
    public FileEntity createFolder(String name, String folderPath, String username) {
        UserEntity owner = entityManager.createQuery(
                        "SELECT u FROM UserEntity u WHERE u.username = :username", UserEntity.class)
                .setParameter("username", username)
                .getSingleResult();

        FileEntity entity = new FileEntity();
        entity.setFileName(name);
        entity.setFolderPath(folderPath);
        entity.setFileType("application/x-directory");
        entity.setFileSize(0L);
        entity.setOwner(owner);

        entityManager.persist(entity);
        return entity;
    }

    @Override
    public long getTotalUsageByUser(String username) {
        String query = "SELECT COALESCE(SUM(f.fileSize), 0) FROM FileEntity f WHERE f.owner.username = :username";
        return entityManager.createQuery(query, Long.class)
                .setParameter("username", username)
                .getSingleResult();
    }

    @Override
    public long countFilesByUser(String username) {
        String query = "SELECT COUNT(f) FROM FileEntity f WHERE f.owner.username = :username";
        return entityManager.createQuery(query, Long.class)
                .setParameter("username", username)
                .getSingleResult();
    }

    @Override
    @Transactional
    public void markAsDeleted(Long id) {
        FileEntity entity = entityManager.find(FileEntity.class, id);
        if (entity != null) {
            LocalDateTime now = LocalDateTime.now();
            entity.setDeletedAt(now);

            if ("application/x-directory".equals(entity.getFileType())) {
                String folderFullPath = buildFullPath(entity);

                entityManager.createQuery(
                                "UPDATE FileEntity f SET f.deletedAt = :now " +
                                        "WHERE f.owner = :owner " +
                                        "AND (f.folderPath = :exactPath OR f.folderPath LIKE :likePath)")
                        .setParameter("now", now)
                        .setParameter("owner", entity.getOwner())
                        .setParameter("exactPath", folderFullPath)
                        .setParameter("likePath", folderFullPath + "/%")
                        .executeUpdate();
            }
        }
    }

    @Override
    @Transactional
    public void restoreFile(Long id) {
        FileEntity entity = entityManager.find(FileEntity.class, id);
        if (entity != null) {
            entity.setDeletedAt(null);

            if ("application/x-directory".equals(entity.getFileType())) {
                String folderFullPath = buildFullPath(entity);

                entityManager.createQuery(
                                "UPDATE FileEntity f SET f.deletedAt = NULL " +
                                        "WHERE f.owner = :owner " +
                                        "AND (f.folderPath = :exactPath OR f.folderPath LIKE :likePath)")
                        .setParameter("owner", entity.getOwner())
                        .setParameter("exactPath", folderFullPath)
                        .setParameter("likePath", folderFullPath + "/%")
                        .executeUpdate();
            }
        }
    }

    private String buildFullPath(FileEntity entity) {
        String path = entity.getFolderPath();
        String name = entity.getFileName();

        if ("/".equals(path)) {
            return "/" + name;
        }
        return path + "/" + name;
    }

    @Override
    @Transactional
    public void hardDelete(Long id) {
        FileEntity entity = entityManager.find(FileEntity.class, id);
        if (entity != null) {
            entityManager.remove(entity);
        }
    }
}