package com.smartlogix.auth.controller;

import com.smartlogix.auth.dto.*;
import com.smartlogix.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para operaciones de autenticación.
 * Expone endpoints para registro, login y validación de tokens.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/register — Registra un nuevo usuario.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /**
     * POST /api/auth/login — Autentica un usuario y devuelve un JWT.
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * GET /api/auth/validate — Valida un token JWT existente.
     * Utilizado internamente por el API Gateway.
     */
    @GetMapping("/validate")
    public AuthResponse validateToken(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(name = "token", required = false) String token) {
        return authService.validateToken(resolveToken(authorization, token));
    }

    private String resolveToken(String authorization, String token) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return token;
    }
    /**
     *
     * GET /api/auth/users — Obtiene todos los usuarios. Solo administradores.
     */
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public java.util.List<UserResponse> getAllUsers() {
        return authService.getAllUsers();
    }

    /**
     * PATCH /api/auth/users/{id}/role — Cambia el rol de un usuario. Solo administradores.
     */
    @PatchMapping("/users/{id}/role")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public UserResponse updateUserRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest request,
            Authentication authentication) {
        return authService.updateRole(id, request.role(), authentication.getName());
    }

    /**
     * PATCH /api/auth/users/{id}/status — Habilita o suspende una cuenta. Solo administradores.
     */
    @PatchMapping("/users/{id}/status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public UserResponse updateUserStatus(
            @PathVariable Long id,
            @RequestBody UpdateStatusRequest request,
            Authentication authentication) {
        return authService.updateEnabled(id, request.enabled(), authentication.getName());
    }
}
