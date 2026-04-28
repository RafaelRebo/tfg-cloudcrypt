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
                                 String checksum, String storagePath, String username) {

        // El repositorio busca al dueño internamente para establecer la relación
        UserEntity owner = entityManager.createQuery(
                        "SELECT u FROM UserEntity u WHERE u.username = :username", UserEntity.class)
                .setParameter("username", username)
                .getSingleResult();

        FileEntity entity = new FileEntity();
        entity.setFileName(name);
        entity.setFolderPath(folderPath);
        entity.setFileType(type);
        entity.setFileSize(size);
        entity.setChecksum(checksum);
        entity.setStoragePath(storagePath);
        entity.setOwner(owner); // Aquí se establece la Foreign Key

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
                String folderPathForChildren = (entity.getFolderPath().endsWith("/") ?
                        entity.getFolderPath() : entity.getFolderPath() + "/")
                        + entity.getFileName();

                entityManager.createQuery(
                                "UPDATE FileEntity f SET f.deletedAt = :now " +
                                        "WHERE f.owner = :owner AND (f.folderPath = :path OR f.folderPath LIKE :subPath)")
                        .setParameter("now", now)
                        .setParameter("owner", entity.getOwner())
                        .setParameter("path", folderPathForChildren)
                        .setParameter("subPath", folderPathForChildren + "/%")
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
                // Generamos la ruta que deben tener los hijos
                String folderPathForChildren = (entity.getFolderPath().endsWith("/") ?
                        entity.getFolderPath() : entity.getFolderPath() + "/")
                        + entity.getFileName();

                entityManager.createQuery(
                                "UPDATE FileEntity f SET f.deletedAt = NULL " +
                                        "WHERE f.owner = :owner AND (f.folderPath = :path OR f.folderPath LIKE :subPath)")
                        .setParameter("owner", entity.getOwner())
                        .setParameter("path", folderPathForChildren)
                        .setParameter("subPath", folderPathForChildren + "/%")
                        .executeUpdate();
            }
        }
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