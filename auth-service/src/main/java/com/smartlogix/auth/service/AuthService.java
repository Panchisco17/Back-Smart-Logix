package com.smartlogix.auth.service;

import com.smartlogix.auth.domain.Role;
import com.smartlogix.auth.domain.UserEntity;
import com.smartlogix.auth.dto.*;
import com.smartlogix.auth.exception.AuthException;
import com.smartlogix.auth.exception.UserNotFoundException;
import com.smartlogix.auth.repository.UserRepository;
import com.smartlogix.auth.security.JwtProvider;
import com.smartlogix.auth.strategy.AuthStrategyResolver;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID; // <-- Importación necesaria para generar el código único

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

        // --- LÓGICA DE GENERACIÓN DE CÓDIGO DE DESCUENTO ---
        String generatedCode = null;
        if (user.getEmail().endsWith("@duocuc.cl")) {
            // Genera un código único tipo "DUOC25-XYZ123"
            generatedCode = "DUOC25-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            user.setDiscountCode(generatedCode);
        }

        userRepository.save(user);

        return new RegisterResponse(
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                generatedCode, // <-- Se envía el código generado en la respuesta
                generatedCode != null 
                    ? "¡Usuario registrado exitosamente! Tienes un código de 25% de descuento: " + generatedCode 
                    : "Usuario registrado exitosamente."
        );
    }

    /**
     * Autentica al usuario usando el Strategy Pattern.
     */
    public AuthResponse login(LoginRequest request) {
        try {
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

    /**
     * Obtiene la lista de todos los usuarios registrados.
     */
    @Transactional(readOnly = true)
    public java.util.List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    /**
     * Cambia el rol de un usuario. Un admin no puede cambiar su propio rol
     * para evitar que se quede sin permisos accidentalmente (auto-bloqueo).
     */
    public UserResponse updateRole(Long id, String roleValue, String currentUsername) {
        UserEntity user = findUserOrThrow(id);

        if (user.getUsername().equalsIgnoreCase(currentUsername)) {
            throw new IllegalStateException("No puedes cambiar tu propio rol.");
        }

        Role role;
        try {
            role = Role.valueOf(roleValue);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Rol invalido: " + roleValue);
        }

        user.setRole(role);
        return UserResponse.from(userRepository.save(user));
    }

    /**
     * Habilita o suspende una cuenta. Un admin no puede suspenderse a si mismo.
     */
    public UserResponse updateEnabled(Long id, boolean enabled, String currentUsername) {
        UserEntity user = findUserOrThrow(id);

        if (user.getUsername().equalsIgnoreCase(currentUsername)) {
            throw new IllegalStateException("No puedes suspender tu propia cuenta.");
        }

        user.setEnabled(enabled);
        return UserResponse.from(userRepository.save(user));
    }

    private UserEntity findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }
}