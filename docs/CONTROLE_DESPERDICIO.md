# Controle de desperdicio alimentar — TableHub

Este modulo usa a tabela **dbo.Desperdicios_Produtos que ja existe no SQL Server do projeto**. 
O esquema foi confirmado pela listagem de colunas em 15/10/2026:
`id_desperdicio`, `produto_id`, `quantidade`, `motivo`, `observacao`,
`custo_unitario_informado`, `registrado_em`, `registrado_por`, `estoque_antes`, `estoque_depois`.

**NAO execute o patch SQL se a tabela ja existir.** O arquivo
`sql/patch_desperdicios_produtos.sql` serve apenas para bancos novos, sem
dados. Nenhuma migracao destrutiva e necessaria para a tabela informada.

## Funcionamento

- Acesso restrito ao perfil ADMINISTRADOR.
- Registro de produto, quantidade, motivo, observacao e custo unitario informado (em R$).
- O backend bloqueia a linha do produto, confirma o estoque esperado,
  desconta as unidades e insere o registro em **uma transacao**.
- O backend usa `CurrentUserService.idOrNull()` para registrar o ID real de
  `dbo.Usuarios` na coluna BIGINT `registrado_por`.
- `GET /api/desperdicios`: ate 100 ultimos registros, com nomes de produto e usuario obtidos por JOIN.
- `GET /api/desperdicios/resumo`: numero de registros, unidades e soma de
  `quantidade * custo_unitario_informado` dos ultimos 30 dias.
- `POST /api/desperdicios`: registra a perda e desconta estoque na mesma transacao.

Exemplo:
```json
{
  "produtoId": 7,
  "quantidade": 2,
  "estoqueEsperado": 18,
  "motivo": "VENCIMENTO",
  "observacao": "Produtos vencidos",
  "custoUnitarioInformado": 3.50
}
```

Motivos aceitos pelo CHECK existente no SQL Server: OUTRO, PREPARO, DANIFICADO e VENCIMENTO. A API usa exatamente estes valores.
O custo informado representa o custo do produto, **nao o preco de venda**.
A coluna `registrado_por` recebe BIGINT, e nao email/nome.
O estoque esperado protege contra saldos desatualizados (HTTP 409).

## Validacao pendente

As restricoes de CHECK para motivo foram verificadas em 15/10/2026. A listagem de colunas nao mostra todas as restricoes de FK, nullability,
precisao/escala nem defaults da tabela existente. Confirme essas regras
com SSMS e valide um registro de teste antes da demonstracao final.
Codigo atualizado no GitHub nao modifica automaticamente o banco.
