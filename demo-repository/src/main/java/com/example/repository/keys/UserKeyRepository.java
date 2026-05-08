package com.example.repository.keys;

import com.example.model.UserKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserKeyRepository extends JpaRepository<UserKeyEntity, Long> {
    // No necesitamos métodos extra por ahora, findById es suficiente
}