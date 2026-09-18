package com.itb.repository;

import com.itb.model.Mesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MesaRepository extends JpaRepository<Mesa, Long> {

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