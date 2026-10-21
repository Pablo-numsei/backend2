package com.itb.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itb.model.*;
import java.time.LocalDateTime;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublicOrderResponseTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void orderResponsePreservesScreenFieldsAndExcludesStaffAndQrData() {
        Order order = new Order();
        order.setId(12L);
        Mesa mesa = new Mesa();
        mesa.setId(3L);
        mesa.setNumber(3);
        mesa.setQrCode("private-qr");
        order.setMesa(mesa);
        StatusPedido status = new StatusPedido();
        status.setName("Recebido");
        order.setStatus(status);
        Usuario staff = new Usuario();
        staff.setEmail("private@example.test");
        order.setUpdatedBy(staff);

        JsonNode json = mapper.valueToTree(OrderResponse.from(order));
        assertEquals(12, json.get("id").asInt());
        assertEquals(3, json.get("mesa").get("number").asInt());
        assertEquals("Recebido", json.get("status").get("name").asText());
        assertFalse(json.has("updatedBy"));
        assertFalse(json.get("mesa").has("qrCode"));
        assertFalse(json.has("accessToken"));
        assertEquals("guest-token", mapper.valueToTree(OrderResponse.from(order, "guest-token"))
                .get("accessToken").asText());
    }

    @Test
    void createdAtUtcIsProjectedAsRealUtcInstantWithKitchenNotes() {
        Order order = new Order();
        order.setId(7L);
        order.setCustomerName("pabloteste01");
        order.setObservacoes("carne bem passada");
        order.setCreatedAt(LocalDateTime.of(2026, 10, 21, 18, 45));
        order.setCreatedAtUtc(LocalDateTime.of(2026, 10, 21, 23, 45));

        OrderResponse response = OrderResponse.from(order);
        assertEquals(Instant.parse("2026-10-21T23:45:00Z"), response.createdAtUtc());
        assertEquals("carne bem passada", response.observacoes());
        assertEquals("pabloteste01", response.nomeCliente());
        // Original server-local timestamp is never silently rewritten.
        assertEquals(LocalDateTime.of(2026, 10, 21, 18, 45), response.createdAt());
    }

    @Test
    void itemResponseDoesNotIncludeInternalStockOrParentOrder() {
        ItemPedido item = new ItemPedido();
        Product product = new Product();
        product.setId(8L);
        product.setName("Prato");
        product.setStockQuantity(200);
        item.setProduto(product);
        item.setQuantidade(2);
        item.setPedido(new Order());

        JsonNode json = mapper.valueToTree(ItemPedidoResponse.from(item));
        assertEquals("Prato", json.get("produto").get("name").asText());
        assertEquals(2, json.get("quantidade").asInt());
        assertFalse(json.get("produto").has("stockQuantity"));
        assertFalse(json.has("pedido"));
    }
}
