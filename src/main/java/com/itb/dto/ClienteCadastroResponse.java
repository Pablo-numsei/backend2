package com.itb.dto;

/** Não devolve hash, senha, permissões ou token de funcionário. */
public record ClienteCadastroResponse(Long id, String nomeUsuario, String message) {}
