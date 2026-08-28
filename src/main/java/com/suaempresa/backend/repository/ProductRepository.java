package com.suaempresa.backend.repository;

import com.suaempresa.backend.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Camada de acesso a dados dos produtos.
 * No Node.js isso ficava implicito dentro do Model (Mongoose/Sequelize).
 * No Spring, o Repository e uma camada separada e explicita.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
}
