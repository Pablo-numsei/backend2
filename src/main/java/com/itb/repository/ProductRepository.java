package com.itb.repository;

import com.itb.model.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository
        extends JpaRepository<Product, Long> {
                List<Product> findByActiveTrue();

    Optional<Product> findByIdAndActiveTrue(Long id);
    }