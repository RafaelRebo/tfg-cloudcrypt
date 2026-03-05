package com.example.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "files")
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

    // @JsonBackReference indica que este enlace no se debe serializar
    // hacia atrás para evitar el bucle infinito.
    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonBackReference
    private User owner;
}