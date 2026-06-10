package com.smartlogix.auth.strategy;

import com.smartlogix.auth.domain.UserEntity;

/**
 * Strategy Pattern — Interfaz que define la estrategia de autenticación.
 * 
 * Permite intercambiar dinámicamente el método de autenticación
 * (local con contraseña, OAuth2/Google, LDAP, etc.) sin modificar
 * la lógica del controlador ni del servicio principal.
 * 
 * Cumple con el principio Open/Closed (SOLID): abierto para extensión,
 * cerrado para modificación.
 */
public interface AuthenticationStrategy {

    /**
     * Identifica si esta estrategia puede manejar la credencial proporcionada.
     * Ej: LocalAuthStrategy reconoce usernames, GoogleAuthStrategy reconoce tokens OAuth.
     */
    boolean supports(String credential);

    /**
     * Autentica al usuario con la credencial y contraseña dadas.
     * @return UserEntity autenticado
     * @throws RuntimeException si las credenciales son inválidas
     */
    UserEntity authenticate(String credential, String password);
}
