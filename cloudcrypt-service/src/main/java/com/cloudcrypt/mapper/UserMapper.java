package com.cloudcrypt.mapper;

import com.cloudcrypt.dto.user.UserDto;
import com.cloudcrypt.model.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserDto toDto(UserEntity entity) {
        if (entity == null) return null;
        return new UserDto(entity.getId(), entity.getUsername(), entity.getEmail(), entity.getFullName(), entity.getAvatarUrl());
    }
}