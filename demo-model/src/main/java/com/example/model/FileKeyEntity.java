package com.example.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "file_keys")
@Getter
@Setter
public class FileKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "file_id", nullable = false)
    private FileEntity file; // El archivo al que pertenece la llave

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user; // El usuario que posee esta copia de la llave

    @Column(columnDefinition = "TEXT", nullable = false)
    private String encryptedKey; // La clave AES cifrada con la RSA Pública del usuario (Base64)

    @Column(name = "is_starred", nullable = false, columnDefinition = "boolean default false")
    private boolean starred = false;

    public boolean isStarred() { return starred; }
    public void setStarred(boolean starred) { this.starred = starred; }
}