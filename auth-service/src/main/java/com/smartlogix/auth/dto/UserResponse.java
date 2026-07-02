package com.smartlogix.auth.dto;

import com.smartlogix.auth.domain.UserEntity;
import java.time.LocalDateTime;

// No incluye el hash de password: el listado de usuarios lo consume el
// front del admin y no debe filtrar credenciales en la respuesta HTTP.
public record UserResponse(
        Long id,
        String username,
        String email,
        String role,
        boolean enabled,
        String discountCode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static UserResponse from(UserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.isEnabled(),
                user.getDiscountCode(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
