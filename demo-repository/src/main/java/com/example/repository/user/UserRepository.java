package com.example.repository.user;

import com.example.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long>, UserRepositoryCustom {
    UserEntity findByUsername(String username);
    boolean existsByUsername(String username);

    List<UserEntity> findByUsernameContainingIgnoreCase(String username);
}