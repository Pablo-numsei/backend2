package com.itb.repository;

import com.itb.model.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByDeletedAtIsNullOrderByCreatedAtAsc();

    List<Order> findByStatus_NameAndDeletedAtIsNullOrderByCreatedAtAsc(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pedido from Order pedido where pedido.id = :id and pedido.deletedAt is null")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);
}
