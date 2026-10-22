# TableHub — Observações para a cozinha e horário correto (novos pedidos)

## Defeitos encontrados
- O campo "Observações" do checkout era gravado somente no sessionStorage do navegador do cliente; a cozinha nunca recebia a informação.
- `dbo.Pedidos.criado_em` tem DEFAULT `GETDATE()`, dependente do fuso do host Somee, sem offset na coluna `DATETIME2`. O navegador interpretava a string ISO sem fuso como hora local.

## Solução implementada
- Frontend envia `observacoes` (até 255 caracteres) em `POST /api/pedidos`.
- Backend valida `@Size(max=255)`, remove espaços de borda, passa argumento parametrizado para `sp_CriarPedido`.
- `dbo.Pedidos.observacoes` é escrita atomicamente com o pedido e retorna como `observacoes` no `OrderResponse`.
- Painel da equipe mostra observações na lista e em "Ver detalhes"; também mostra o nome do cliente autenticado.
- Campo novo `dbo.Pedidos.criado_em_utc` armazena UTC (`SYSUTCDATETIME()`) somente em pedidos novos.
- `OrderResponse.createdAtUtc` é um instante ISO 8601 com `Z`, sem ambiguidades.
- Frontend converte esse instante para o fuso do restaurante (`America/Sao_Paulo` por padrão; pode configurar `VITE_RESTAURANT_TIME_ZONE` com fuso IANA).
- Pedido antigo sem `criado_em_utc` exibe `HH:mm (servidor)`; não presumimos um deslocamento de 2 horas.

## Migração SQL — executar APENAS após backup RECENTE

**A migração anterior foi feita antes do pedido #7. É necessário um novo backup para preservar os pedidos gerados desde então.**

Execute em uma cópia de testes antes do banco operacional. Pare o backend antes de aplicar.

Pré-requisitos já realizados no Somee:
- `sql/patch_clientes_pin.sql`
- `sql/patch_pedidos_cliente_pin.sql`

Patch novo:

- `sql/patch_pedidos_observacoes_utc.sql`

Ele cria duas colunas opcionais e atualiza `sp_CriarPedido` preservando estoque, total e transação. **Revise a versão real da procedure antes de aplicar**, pois mudanças feitas diretamente no Somee após a migração anterior não devem ser sobrescritas.

Consultas de verificação (somente leitura):
```sql
SELECT DB_NAME() AS BancoAtual;
SELECT COLUMN_NAME, IS_NULLABLE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = 'Pedidos'
  AND COLUMN_NAME IN ('observacoes', 'criado_em_utc');
SELECT name FROM sys.parameters
WHERE object_id = OBJECT_ID('dbo.sp_CriarPedido')
  AND name = '@observacoes';
SELECT GETDATE() AS HorarioServidor,
       SYSUTCDATETIME() AS HorarioUTC,
       SYSDATETIMEOFFSET() AS HorarioServidorComOffset;
```

### Limitações importantes

O pedido **#7** já foi criado sem observação no banco e sem fuso: a migração NÃO consegue recuperar esses dados por conta própria. Se ainda estiver em preparo, informe "carne bem passada" diretamente à equipe. Se for necessário corrigir o histórico, faça somente após verificação do relógio do servidor e confirmação do horário real; não execute correções em massa em `criado_em`.

Não haverá alteração automática em pedidos existentes nem na autenticação por PIN.

## Verificações após aplicar o SQL

1. Backend: `.\\mvnw.cmd test`; iniciar `.\\mvnw.cmd spring-boot:run`.
2. Frontend: `npm test`, `npm run build`, `npm run dev`.
3. Registrar pedido novo com observação `carne bem passada`.
4. Conferir a observação na lista e em "Ver detalhes" do painel de cozinha.
5. Conferir horário local na equipe e `criado_em_utc` no SQL.
6. Confirmar que o estoque e o total continuam corretos.
