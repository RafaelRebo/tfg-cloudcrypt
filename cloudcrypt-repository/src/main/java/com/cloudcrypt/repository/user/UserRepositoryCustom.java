package com.cloudcrypt.repository.user;

import com.cloudcrypt.model.UserEntity;

public interface UserRepositoryCustom {
    public UserEntity createUser(String username, String encodedPassword, String fullName, String email, String avatar);
}
