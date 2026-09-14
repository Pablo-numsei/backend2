
package com.itb.repository;

import com.itb.model.StatusPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StatusPedidoRepository
        extends JpaRepository<StatusPedido, Long> {

    Optional<StatusPedido> findByNameIgnoreCase(String name);
}