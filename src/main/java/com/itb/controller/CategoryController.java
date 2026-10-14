package com.itb.controller;

import com.itb.model.Category;
import com.itb.repository.CategoryRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/v1/categorias")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;

    @GetMapping
    public List<Category> list() {
        return categoryRepository.findAll().stream()
                .filter(category -> !Boolean.FALSE.equals(category.getActive()))
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public record CreateCategoryRequest(
            @NotBlank(message = "O nome da categoria é obrigatório")
            @Size(max = 100)
            String nome,
            @Size(max = 300)
            String descricao
    ) {}

    @PostMapping
    @Transactional
    public ResponseEntity<Category> create(@Valid @RequestBody CreateCategoryRequest request) {
        String name = request.nome().trim();
        boolean duplicate = categoryRepository.findAll().stream()
                .anyMatch(category -> category.getName().equalsIgnoreCase(name));

        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Categoria já cadastrada.");
        }

        Category category = new Category();
        category.setName(name);
        category.setDescription(request.descricao() == null ? null : request.descricao().trim());
        Category saved = categoryRepository.saveAndFlush(category);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
