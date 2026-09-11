package com.itb.service;

import com.itb.model.Product;
import com.itb.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    // LISTAR TODOS
    public List<Product> findAll() {
        return productRepository.findByActiveTrue();
    }

    // BUSCAR POR ID
    public Optional<Product> findById(Long id) {
        return productRepository.findByIdAndActiveTrue(id);
    }
    // SALVAR
    public Product save(Product product) {
        return productRepository.save(product);
    }

    // ATUALIZAR
    public Optional<Product> update(Long id, Product product) {

        return productRepository.findByIdAndActiveTrue(id)
                .map(existingProduct -> {

                    existingProduct.setCategory(product.getCategory());
                    existingProduct.setName(product.getName());
                    existingProduct.setDescription(product.getDescription());
                    existingProduct.setPrice(product.getPrice());
                    existingProduct.setStockQuantity(product.getStockQuantity());
                    existingProduct.setUpdatedAt(LocalDateTime.now());

                    return productRepository.save(existingProduct);
                });
    }

    // EXCLUIR
    public boolean delete(Long id) {

        if (!productRepository.existsById(id)) {
            return false;
        }

        productRepository.deleteById(id);

        return true;
    }
}