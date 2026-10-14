package com.itb.service;

import com.itb.dto.StockAdjustmentRequest;
import com.itb.exception.ResourceNotFoundException;
import com.itb.model.Category;
import com.itb.model.Product;
import com.itb.repository.CategoryRepository;
import com.itb.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private EntityManager entityManager;
    @InjectMocks private ProductService productService;

    private Product product;
    private Category category;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setId(3L);
        category.setActive(true);
        product = new Product();
        product.setId(7L);
        product.setName("Produto");
        product.setCategory(category);
        product.setPrice(new BigDecimal("10.00"));
        product.setStockQuantity(18);
        product.setActive(true);
    }

    @Test
    void stockAdjustmentLocksProductAndAppliesDeltaToCurrentQuantity() {
        when(productRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(product));
        when(productRepository.saveAndFlush(product)).thenReturn(product);

        Product result = productService.adjustStock(7L, new StockAdjustmentRequest(2, 18), "admin");

        assertEquals(20, result.getStockQuantity());
        verify(productRepository).findByIdForUpdate(7L);
        verify(entityManager).refresh(product);
    }

    @Test
    void staleQuantityCannotRestoreStockConsumedByOrder() {
        when(productRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(product));

        assertThrows(IllegalStateException.class,
                () -> productService.adjustStock(7L, new StockAdjustmentRequest(1, 20), "admin"));

        assertEquals(18, product.getStockQuantity());
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void adjustmentCannotMakeStockNegative() {
        when(productRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(product));

        assertThrows(IllegalArgumentException.class,
                () -> productService.adjustStock(7L, new StockAdjustmentRequest(-19, 18), "admin"));
        assertEquals(18, product.getStockQuantity());
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void adjustmentDetectsIntegerOverflowBeforeWriting() {
        product.setStockQuantity(Integer.MAX_VALUE);
        when(productRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(product));

        assertThrows(IllegalArgumentException.class,
                () -> productService.adjustStock(7L, new StockAdjustmentRequest(1, Integer.MAX_VALUE), "admin"));
        assertEquals(Integer.MAX_VALUE, product.getStockQuantity());
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void zeroAdjustmentIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> productService.adjustStock(7L, new StockAdjustmentRequest(0, 18), "admin"));
        verifyNoInteractions(productRepository);
    }

    @Test
    void missingProductCannotBeAdjusted() {
        when(productRepository.findByIdForUpdate(7L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> productService.adjustStock(7L, new StockAdjustmentRequest(1, 18), "admin"));
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void catalogUpdateCannotWriteOldOrArbitraryStock() {
        Product request = new Product();
        request.setStockQuantity(20);
        when(productRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(product));

        assertThrows(IllegalStateException.class, () -> productService.update(7L, request));

        assertEquals(18, product.getStockQuantity());
        verify(productRepository, never()).saveAndFlush(any());
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void catalogUpdateKeepsCurrentStockAndRefreshesGeneratedFields() {
        Product request = new Product();
        request.setCategory(category);
        request.setName("Nome atualizado");
        request.setPrice(new BigDecimal("12.00"));
        request.setStockQuantity(18);
        when(productRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category));
        when(productRepository.saveAndFlush(product)).thenReturn(product);

        Product updated = productService.update(7L, request).orElseThrow();

        assertEquals("Nome atualizado", updated.getName());
        assertEquals(18, updated.getStockQuantity());
        verify(entityManager).refresh(updated);
    }

    @Test
    void createCannotMergeIntoExistingProduct() {
        assertThrows(IllegalArgumentException.class, () -> productService.save(product));
        verifyNoInteractions(productRepository, categoryRepository);
    }

    @Test
    void createAcceptsOnlyCatalogAndInitialStockFields() {
        product.setId(null);
        product.setActive(false);
        product.setAvailable(true);
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category));
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product created = productService.save(product);

        assertNotSame(product, created);
        assertNull(created.getId());
        assertNull(created.getActive());
        assertNull(created.getAvailable());
        assertEquals(18, created.getStockQuantity());
        verify(entityManager).refresh(created);
    }

    @Test
    void deactivationAlsoLocksProductBeforeWriting() {
        when(productRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(product));

        assertTrue(productService.delete(7L));

        assertFalse(product.getActive());
        verify(productRepository).saveAndFlush(product);
    }
}
