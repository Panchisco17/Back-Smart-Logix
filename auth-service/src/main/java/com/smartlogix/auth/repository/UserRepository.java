package com.smartlogix.auth.repository;

import com.smartlogix.auth.domain.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Pattern — Capa de abstracción para el acceso a datos de usuarios.
 * Desacopla la lógica de negocio de la tecnología de persistencia subyacente.
 * Si se migra de H2 a PostgreSQL/MongoDB, solo se modifica la configuración,
 * no la lógica de autenticación.
 */
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUsername(String username);

    Optional<UserEntity> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
