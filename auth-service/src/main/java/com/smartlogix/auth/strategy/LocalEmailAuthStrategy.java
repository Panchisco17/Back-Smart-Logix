package com.smartlogix.auth.strategy;

import com.smartlogix.auth.domain.UserEntity;
import com.smartlogix.auth.repository.UserRepository;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Strategy Pattern — Estrategia de autenticación local por email + contraseña.
 * 
 * Se activa cuando la credencial contiene '@'.
 * Demuestra la extensibilidad del patrón: se puede añadir una nueva estrategia
 * (ej: GoogleAuthStrategy, LdapAuthStrategy) sin modificar código existente.
 */
@Component
@Order(2)
public class LocalEmailAuthStrategy implements AuthenticationStrategy {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public LocalEmailAuthStrategy(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Acepta credenciales que contienen '@' (se interpreta como email).
     */
    @Override
    public boolean supports(String credential) {
        return credential != null && credential.contains("@");
    }

    @Override
    public UserEntity authenticate(String credential, String password) {
        UserEntity user = userRepository.findByEmail(credential)
                .orElseThrow(() -> new RuntimeException("No existe usuario con email: " + credential));

        if (!user.isEnabled()) {
            throw new RuntimeException("La cuenta del usuario está deshabilitada.");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Contraseña incorrecta.");
        }

        return user;
    }
}
