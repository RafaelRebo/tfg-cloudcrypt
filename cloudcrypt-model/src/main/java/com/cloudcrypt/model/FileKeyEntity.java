package com.cloudcrypt.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "file_keys", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"file_id", "user_id"})
})
@Getter
@Setter
public class FileKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "file_id", nullable = false)
    private FileEntity file;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String encryptedKey;

    @Column(name = "is_starred", nullable = false, columnDefinition = "boolean default false")
    private boolean starred = false;

    public boolean isStarred() { return starred; }
    public void setStarred(boolean starred) { this.starred = starred; }
}