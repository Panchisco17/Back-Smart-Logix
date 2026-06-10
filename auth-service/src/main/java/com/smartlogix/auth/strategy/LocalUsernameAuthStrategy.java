package com.smartlogix.auth.strategy;

import com.smartlogix.auth.domain.UserEntity;
import com.smartlogix.auth.repository.UserRepository;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Strategy Pattern — Estrategia de autenticación local (username + contraseña).
 * 
 * Busca al usuario por username en la base de datos (Repository Pattern)
 * y verifica la contraseña hasheada con BCrypt.
 * 
 * La anotación @Order(1) le da prioridad como primera estrategia a evaluar.
 */
@Component
@Order(1)
public class LocalUsernameAuthStrategy implements AuthenticationStrategy {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public LocalUsernameAuthStrategy(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Acepta credenciales que NO contienen '@' (se interpreta como username).
     */
    @Override
    public boolean supports(String credential) {
        return credential != null && !credential.contains("@");
    }

    @Override
    public UserEntity authenticate(String credential, String password) {
        UserEntity user = userRepository.findByUsername(credential)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + credential));

        if (!user.isEnabled()) {
            throw new RuntimeException("La cuenta del usuario está deshabilitada.");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Contraseña incorrecta.");
        }

        return user;
    }
}
