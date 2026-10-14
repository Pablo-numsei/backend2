package com.itb.repository;

import com.itb.model.Product;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository
        extends JpaRepository<Product, Long> {
                List<Product> findByActiveTrue();

    Optional<Product> findByIdAndActiveTrue(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select produto from Product produto where produto.id = :id and produto.active = true")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
    }
