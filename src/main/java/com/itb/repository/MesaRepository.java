package com.itb.repository;

import com.itb.model.Mesa;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MesaRepository extends JpaRepository<Mesa, Long> {

    List<Mesa> findByActiveTrueOrderByNumberAsc();
    Optional<Mesa> findByIdAndActiveTrue(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select mesa from Mesa mesa where mesa.id = :id")
    Optional<Mesa> findByIdForUpdate(@Param("id") Long id);

    boolean existsByNumber(Integer number);

    boolean existsByQrCode(String qrCode);

    boolean existsByNumberAndIdNot(
            Integer number,
            Long id
    );

    boolean existsByQrCodeAndIdNot(
            String qrCode,
            Long id
    );
}
