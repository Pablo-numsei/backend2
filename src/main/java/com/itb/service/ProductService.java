package com.itb.service;

import com.itb.exception.ResourceNotFoundException;
import com.itb.model.Category;
import com.itb.model.Product;
import com.itb.repository.CategoryRepository;
import com.itb.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    // =========================
    // LISTAR PRODUTOS ATIVOS
    // =========================
    public List<Product> findAll() {

        return productRepository.findByActiveTrue();
    }

    // =========================
    // BUSCAR PRODUTO POR ID
    // =========================
    public Optional<Product> findById(Long id) {

        return productRepository.findByIdAndActiveTrue(id);
    }

    // =========================
    // CRIAR PRODUTO
    // =========================
    @Transactional
    public Product save(Product product) {

        Category category =
                resolveCategory(product);

        product.setCategory(category);

        return productRepository.save(product);
    }

    // =========================
    // ATUALIZAR PRODUTO
    // =========================
    @Transactional
    public Optional<Product> update(
            Long id,
            Product product
    ) {

        return productRepository
                .findByIdAndActiveTrue(id)
                .map(existingProduct -> {

                    Category category =
                            resolveCategory(product);

                    existingProduct.setCategory(
                            category
                    );

                    existingProduct.setName(
                            product.getName()
                    );

                    existingProduct.setDescription(
                            product.getDescription()
                    );

                    existingProduct.setPrice(
                            product.getPrice()
                    );

                    existingProduct.setStockQuantity(
                            product.getStockQuantity()
                    );

                    /*
                     * updatedAt não precisa ser definido aqui.
                     * O @PreUpdate do Product já cuida disso.
                     */

                    return productRepository.save(
                            existingProduct
                    );
                });
    }

    // =========================
    // DESATIVAR PRODUTO
    // =========================
    @Transactional
    public boolean delete(Long id) {

        Product product = productRepository
                .findByIdAndActiveTrue(id)
                .orElse(null);

        if (product == null) {
            return false;
        }

        /*
         * Soft delete.
         *
         * Não removemos fisicamente a linha.
         * Apenas marcamos ativo = false.
         */
        product.setActive(false);

        productRepository.save(product);

        return true;
    }

    // =========================
    // VALIDAR CATEGORIA
    // =========================
    private Category resolveCategory(
            Product product
    ) {

        if (product.getCategory() == null
                || product.getCategory().getId() == null) {

            throw new IllegalArgumentException(
                    "A categoria do produto é obrigatória"
            );
        }

        Long categoryId =
                product.getCategory().getId();

        return categoryRepository
                .findById(categoryId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Categoria não encontrada"
                        )
                );
    }
}