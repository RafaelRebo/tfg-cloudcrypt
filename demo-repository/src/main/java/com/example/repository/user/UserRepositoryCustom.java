package com.example.repository.user;

import com.example.model.UserEntity;

public interface UserRepositoryCustom {
    public UserEntity createUser(String username, String encodedPassword, String fullName, String email, String avatar);
}
