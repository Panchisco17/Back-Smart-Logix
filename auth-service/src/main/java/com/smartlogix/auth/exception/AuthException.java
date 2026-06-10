package com.smartlogix.auth.exception;

/**
 * Excepción de autenticación personalizada.
 */
public class AuthException extends RuntimeException {

    public AuthException(String message) {
        super(message);
    }
}
