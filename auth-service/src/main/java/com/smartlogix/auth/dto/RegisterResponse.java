package com.smartlogix.auth.dto;

/**
 * Respuesta tras un registro exitoso.
 */
public record RegisterResponse(
        String username,
        String email,
        String role,
        String message
) {}
