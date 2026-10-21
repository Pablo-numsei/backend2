package com.itb.repository;

import com.itb.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
    Optional<Cliente> findByEmailIgnoreCase(String email);
    Optional<Cliente> findByUsernameIgnoreCase(String username);
}
