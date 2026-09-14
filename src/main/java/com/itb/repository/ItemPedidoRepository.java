package com.itb.repository;

import com.itb.model.ItemPedido;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemPedidoRepository
        extends JpaRepository<ItemPedido, Long> {

    List<ItemPedido> findByPedido_Id(Long pedidoId);
}