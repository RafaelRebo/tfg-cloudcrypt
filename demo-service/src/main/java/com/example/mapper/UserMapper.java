package com.example.mapper;

import com.example.dto.UserDto;
import com.example.model.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserDto toDto(UserEntity entity) {
        if (entity == null) return null;
        return new UserDto(entity.getId(), entity.getUsername(), entity.getEmail());
    }
}