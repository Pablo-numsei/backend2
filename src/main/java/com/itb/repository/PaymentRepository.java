package com.itb.repository;

import com.itb.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrder_Id(Long orderId);

    Optional<Payment> findTopByOrder_IdOrderByCreatedAtDesc(Long orderId);
}