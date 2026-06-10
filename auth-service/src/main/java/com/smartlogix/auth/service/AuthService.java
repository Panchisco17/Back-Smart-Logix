package com.smartlogix.auth.service;

import com.smartlogix.auth.domain.Role;
import com.smartlogix.auth.domain.UserEntity;
import com.smartlogix.auth.dto.*;
import com.smartlogix.auth.exception.AuthException;
import com.smartlogix.auth.repository.UserRepository;
import com.smartlogix.auth.security.JwtProvider;
import com.smartlogix.auth.strategy.AuthStrategyResolver;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de autenticación que orquesta el registro y el login.
 * Utiliza el AuthStrategyResolver (Strategy Pattern) para delegar
 * la autenticación a la estrategia adecuada según la credencial.
 */
@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final AuthStrategyResolver strategyResolver;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider,
                       AuthStrategyResolver strategyResolver) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.strategyResolver = strategyResolver;
    }

    /**
     * Registra un nuevo usuario con contraseña hasheada (BCrypt).
     */
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new AuthException("El nombre de usuario ya está en uso: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new AuthException("El email ya está registrado: " + request.email());
        }

        UserEntity user = new UserEntity();
        user.setUsername(request.username().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.ROLE_USER);
        user.setEnabled(true);

        userRepository.save(user);

        return new RegisterResponse(
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                "Usuario registrado exitosamente."
        );
    }

    /**
     * Autentica al usuario usando el Strategy Pattern.
     * El AuthStrategyResolver selecciona automáticamente la estrategia
     * correcta (por username o por email) según la credencial.
     */
    public AuthResponse login(LoginRequest request) {
        try {
            // Strategy Pattern: resuelve y ejecuta la estrategia adecuada
            UserEntity user = strategyResolver
                    .resolve(request.credential())
                    .authenticate(request.credential(), request.password());

            String token = jwtProvider.generateToken(user.getUsername(), user.getRole().name());

            return new AuthResponse(
                    token,
                    user.getUsername(),
                    user.getRole().name(),
                    jwtProvider.getExpirationMs()
            );
        } catch (RuntimeException e) {
            throw new AuthException("Credenciales invalidas.");
        }
    }

    /**
     * Valida un token JWT y devuelve su información.
     */
    @Transactional(readOnly = true)
    public AuthResponse validateToken(String token) {
        if (!jwtProvider.validateToken(token)) {
            throw new AuthException("Token inválido o expirado.");
        }

        String username = jwtProvider.getUsernameFromToken(token);
        String role = jwtProvider.getRoleFromToken(token);

        return new AuthResponse(token, username, role, jwtProvider.getExpirationMs());
    }
}
