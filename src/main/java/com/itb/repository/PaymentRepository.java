package com.itb.repository;

import com.itb.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrder_Id(Long orderId);

    List<Payment> findByOrder_IdOrderByIdDesc(Long orderId);

    /** Exibe um histórico limitado, sem pedidos excluídos, apenas para o caixa. */
    @Query("select p from Payment p join fetch p.order o where o.deletedAt is null order by p.id desc")
    List<Payment> findRecentVisiblePayments(Pageable pageable);
}
