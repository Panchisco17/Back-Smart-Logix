package com.smartlogix.auth.strategy;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Resolutor de estrategias de autenticación.
 * 
 * Recibe todas las implementaciones de AuthenticationStrategy inyectadas
 * por Spring (gracias al patrón Strategy + IoC) y selecciona la adecuada
 * según la credencial proporcionada.
 * 
 * Similar al ShipmentPlanFactoryResolver del shipment-service.
 */
@Component
public class AuthStrategyResolver {

    private final List<AuthenticationStrategy> strategies;

    public AuthStrategyResolver(List<AuthenticationStrategy> strategies) {
        this.strategies = strategies;
    }

    /**
     * Busca la primera estrategia que soporte la credencial dada.
     * Si ninguna la soporta, lanza excepción.
     */
    public AuthenticationStrategy resolve(String credential) {
        return strategies.stream()
                .filter(s -> s.supports(credential))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró una estrategia de autenticación para la credencial proporcionada."));
    }
}
