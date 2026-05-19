package com.cloudcrypt.repository.user;

import com.cloudcrypt.model.UserEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

public class UserRepositoryImpl implements UserRepositoryCustom {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public UserEntity createUser(String username, String encodedPassword, String fullName, String email, String avatar) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPassword(encodedPassword);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setAvatarUrl(avatar);
        entityManager.persist(user);
        return user;
    }
}
