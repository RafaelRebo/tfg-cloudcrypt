package com.example.repository;

import com.example.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    // Este método es mágico: Spring Data JPA genera la consulta SQL por ti
    // SELECT * FROM users WHERE username = ?
    UserEntity findByUsername(String username);
}