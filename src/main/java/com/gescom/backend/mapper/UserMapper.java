package com.gescom.backend.mapper;

import com.gescom.backend.dto.user.UserResponse;
import com.gescom.backend.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        if (user == null) return null;
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getRole() != null ? user.getRole().name() : null,
                user.getActive(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
