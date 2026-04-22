package com.example.repository.user;

import com.example.model.UserEntity;

public interface UserRepositoryCustom {
    UserEntity createUser(String username, String encodedPassword, String email);
}
