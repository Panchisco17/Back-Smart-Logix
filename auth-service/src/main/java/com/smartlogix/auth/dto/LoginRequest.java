package com.smartlogix.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO genérico de login. El campo "credential" puede ser username o email
 * dependiendo de la estrategia de autenticación seleccionada.
 */
public record LoginRequest(
        @NotBlank(message = "Las credenciales son obligatorias")
        String credential,

        @NotBlank(message = "La contraseña es obligatoria")
        String password
) {}
