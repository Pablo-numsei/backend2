package com.itb.controller;

import com.itb.model.Product;
import com.itb.service.ProductService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/produtos")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // LISTAR PRODUTOS ATIVOS
    @GetMapping
    public ResponseEntity<List<Product>> findAll() {

        return ResponseEntity.ok(
                productService.findAll()
        );
    }

    // BUSCAR PRODUTO POR ID
    @GetMapping("/{id}")
    public ResponseEntity<Product> findById(
            @PathVariable Long id
    ) {

        return productService
                .findById(id)
                .map(ResponseEntity::ok)
                .orElse(
                        ResponseEntity.notFound().build()
                );
    }

    // CRIAR PRODUTO
    @PostMapping
    public ResponseEntity<Product> save(
            @Valid @RequestBody Product product
    ) {

        Product savedProduct =
                productService.save(product);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(savedProduct);
    }

    // ATUALIZAR PRODUTO
    @PutMapping("/{id}")
    public ResponseEntity<Product> update(
            @PathVariable Long id,
            @Valid @RequestBody Product product
    ) {

        return productService
                .update(id, product)
                .map(ResponseEntity::ok)
                .orElse(
                        ResponseEntity.notFound().build()
                );
    }

    // EXCLUIR / DESATIVAR PRODUTO
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id
    ) {

        if (productService.delete(id)) {
            return ResponseEntity
                    .noContent()
                    .build();
        }

        return ResponseEntity
                .notFound()
                .build();
    }
}