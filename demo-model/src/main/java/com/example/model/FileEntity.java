package com.example.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "files", indexes = {
        @Index(name = "idx_owner_path", columnList = "user_id, folderPath"),
        @Index(name = "idx_owner_deleted", columnList = "user_id, deletedAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;
    private String fileType;
    private Long fileSize;
    private String folderPath;
    private String storagePath;
    private LocalDateTime deletedAt;
    private String salt;

    @Column(length = 64)
    private String checksum;

    // @JsonBackReference indica que este enlace no se debe serializar
    // hacia atrás para evitar el bucle infinito.
    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonBackReference
    private UserEntity owner;
}