package com.cloudcrypt.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "files", indexes = {
        @Index(name = "idx_owner_path", columnList = "user_id, folder_path"),
        @Index(name = "idx_owner_deleted", columnList = "user_id, deleted_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_type")
    private String fileType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "folder_path")
    private String folderPath;

    @Column(name = "storage_path")
    private String storagePath;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private String salt;

    @Column(name = "checksum", length = 255, nullable = false)
    private String checksum;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonBackReference
    private UserEntity owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private FileEntity parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FileEntity> children = new ArrayList<>();

    @OneToMany(mappedBy = "file", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<FileKeyEntity> fileKeys = new ArrayList<>();

    @PrePersist
    @PreUpdate
    public void preUpdateLifecycle() {
        this.updatedAt = LocalDateTime.now();

        if (this.checksum == null) {
            this.checksum = "";
        }
    }
}