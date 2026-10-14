package com.itb.service;

import com.itb.exception.ResourceNotFoundException;
import com.itb.dto.StockAdjustmentRequest;
import com.itb.model.Category;
import com.itb.model.Product;
import com.itb.repository.CategoryRepository;
import com.itb.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final EntityManager entityManager;

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

        if (product.getId() != null) {
            throw new IllegalArgumentException("O ID do produto é definido pelo servidor");
        }

        Category category =
                resolveCategory(product);

        Product created = new Product();
        created.setCategory(category);
        created.setName(product.getName());
        created.setDescription(product.getDescription());
        created.setPrice(product.getPrice());
        created.setStockQuantity(product.getStockQuantity());
        Product saved = productRepository.saveAndFlush(created);
        entityManager.refresh(saved);
        return saved;
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
                .findByIdForUpdate(id)
                .map(existingProduct -> {

                    if (!Objects.equals(existingProduct.getStockQuantity(), product.getStockQuantity())) {
                        throw new IllegalStateException(
                                "O estoque mudou ou foi alterado no formulário; atualize os dados e use o ajuste de estoque"
                        );
                    }

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

                    /*
                     * updatedAt não precisa ser definido aqui.
                     * O @PreUpdate do Product já cuida disso.
                     */

                    Product saved = productRepository.saveAndFlush(
                            existingProduct
                    );
                    entityManager.refresh(saved);
                    return saved;
                });
    }

    @Transactional
    public Product adjustStock(Long id, StockAdjustmentRequest request, String actor) {
        if (request.delta() == null || request.delta() == 0) {
            throw new IllegalArgumentException("O ajuste de estoque precisa ser diferente de zero");
        }
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        if (!Objects.equals(product.getStockQuantity(), request.expectedStockQuantity())) {
            throw new IllegalStateException("O estoque foi atualizado por outra operação; atualize os dados antes de ajustar");
        }
        int next;
        try {
            next = Math.addExact(product.getStockQuantity(), request.delta());
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("O ajuste excede o limite de estoque permitido");
        }
        if (next < 0) {
            throw new IllegalArgumentException("O ajuste não pode deixar o estoque negativo");
        }
        int previous = product.getStockQuantity();
        product.setStockQuantity(next);
        Product saved = productRepository.saveAndFlush(product);
        entityManager.refresh(saved);
        log.info("Ajuste manual de estoque: produto={}, anterior={}, delta={}, atual={}, operador={}",
                id, previous, request.delta(), next, actor);
        return saved;
    }

    // =========================
    // DESATIVAR PRODUTO
    // =========================
    @Transactional
    public boolean delete(Long id) {

        Product product = productRepository
                .findByIdForUpdate(id)
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

        productRepository.saveAndFlush(product);

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
