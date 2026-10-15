# Controle de desperdicio alimentar — TableHub

A funcionalidade registra perdas de produtos do cardapio e reduz o estoque de forma atomica.

## Implantacao

1. Execute `sql/patch_desperdicios_produtos.sql` no **mesmo SQL Server** usado pelo backend (inclusive Somee, quando aplicavel). O patch e idempotente e nao apaga registros.
2. Atualize o backend e o frontend com os commits da main.
3. Inicie o backend, acesse **Produtos / Estoque** como **ADMINISTRADOR** e registre uma perda.

**Importante:** apenas enviar codigo ao GitHub **nao executa a migracao SQL**. Enquanto a tabela nao existir no banco, os endpoints retornarao erro. Nao e necessario criar uma tabela de produtos adicional.

## Endpoints

Todos exigem JWT com perfil `ADMINISTRADOR`:

- `GET /api/desperdicios`: ate 100 ultimos registros.
- `GET /api/desperdicios/resumo`: registros, unidades e custo estimado dos ultimos 30 dias (UTC).
- `POST /api/desperdicios`: registra uma perda e desconta estoque na mesma transacao.

Exemplo de POST:

```json
{
  "produtoId": 7,
  "quantidade": 2,
  "estoqueEsperado": 18,
  "motivo": "VENCIMENTO",
  "observacao": "Produtos vencidos"
}
```

Motivos aceitos: `VENCIMENTO`, `PREPARO`, `DANO`, `OUTROS`.
A quantidade deve ser positiva e nao ultrapassar o estoque. `estoqueEsperado` impede o uso de uma quantidade desatualizada (HTTP 409).
O custo e **estimado pelo preco de venda** no momento do registro, e nao pelo custo de aquisicao.

## Limites

- O historico da API retorna os ultimos 100 lancamentos; os indicadores resumem os ultimos 30 dias.
- Nao ha edicao ou exclusao de perdas (preserva auditoria).
- Nao ha testes de SQL Server real neste commit; teste a migracao e um registro num ambiente de demonstracao antes de usar em producao.
