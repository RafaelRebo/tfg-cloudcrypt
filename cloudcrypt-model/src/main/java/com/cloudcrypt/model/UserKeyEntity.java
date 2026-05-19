package com.cloudcrypt.model;

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
    @Lob
    @Column(name = "public_key", columnDefinition = "LONGTEXT")
    private String publicKey;

    @Lob
    @Column(name = "encrypted_private_key", columnDefinition = "LONGTEXT")
    private String encryptedPrivateKey;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private UserEntity user;
}