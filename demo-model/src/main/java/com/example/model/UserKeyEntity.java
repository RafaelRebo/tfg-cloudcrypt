package com.example.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "user_keys")
@Getter
@Setter
public class UserKeyEntity {

    @Id
    private Long userId;

    // Usamos ColumnDefinition TEXT para que quepan las llaves RSA
    @Column(columnDefinition = "TEXT")
    private String publicKey;

    @Column(columnDefinition = "TEXT")
    private String encryptedPrivateKey;

    @OneToOne
    @MapsId // Esto hace que el id de esta tabla sea el mismo que el de User
    @JoinColumn(name = "user_id")
    private UserEntity user;
    // ^ Cambia UserEntity por el nombre de tu clase de usuario
}