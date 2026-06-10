package com.smartlogix.auth.config;

import com.smartlogix.auth.domain.Role;
import com.smartlogix.auth.domain.UserEntity;
import com.smartlogix.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Carga inicial de datos de prueba para el auth-service.
 * Crea un usuario admin y un usuario regular para testing.
 */
@Configuration
public class DataSeedConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSeedConfig.class);

    @Bean
    @ConditionalOnProperty(prefix = "smartlogix.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
    CommandLineRunner seedUsers(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${smartlogix.seed.admin-password:admin123}") String adminPassword,
            @Value("${smartlogix.seed.user-password:user123}") String userPassword,
            @Value("${smartlogix.seed.warehouse-password:bodega123}") String warehousePassword) {
        return args -> {
            if (userRepository.count() == 0) {
                UserEntity admin = new UserEntity();
                admin.setUsername("admin");
                admin.setEmail("admin@smartlogix.com");
                admin.setPassword(passwordEncoder.encode(adminPassword));
                admin.setRole(Role.ROLE_ADMIN);
                admin.setEnabled(true);
                userRepository.save(admin);

                UserEntity user = new UserEntity();
                user.setUsername("usuario");
                user.setEmail("usuario@smartlogix.com");
                user.setPassword(passwordEncoder.encode(userPassword));
                user.setRole(Role.ROLE_USER);
                user.setEnabled(true);
                userRepository.save(user);

                UserEntity manager = new UserEntity();
                manager.setUsername("bodeguero");
                manager.setEmail("bodeguero@smartlogix.com");
                manager.setPassword(passwordEncoder.encode(warehousePassword));
                manager.setRole(Role.ROLE_WAREHOUSE_MANAGER);
                manager.setEnabled(true);
                userRepository.save(manager);

                log.info(">> Usuarios de prueba creados: admin, usuario, bodeguero");
            }
        };
    }
}
