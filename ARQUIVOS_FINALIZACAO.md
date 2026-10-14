# TableHub — arquivos da finalização

Rodada de 14/10/2026. As alterações foram divididas por responsabilidade e preservam a estrutura do banco.

## Etapas

- Segurança: limite real de corpo e limitação por caminho normalizado, com retestes.
- Backend: contexto de QR, DTOs de mesa, painel completo, atendimento sem duplicações e notificação após commit.
- Frontend: preços/busca, pedidos anônimos, simulação explícita, mesas/QR, cliente e polling.
- SQL: duas substituições autorizadas de dados; demais alterações do SQL e exports já existiam no workspace.
- Documentação: README, auditoria, testes e guia de entrega.

## Backend

- `ARQUIVOS_ALTERADOS.md`
- `ARQUIVOS_FINALIZACAO.md`
- `AUDITORIA_FINALIZACAO.md`
- `database/table_hub_db.sql`
- `ENTREGA_SEGURANCA.md`
- `estrutura_tablehub.csv`
- `GUIA_ENTREGA.md`
- `README.md`
- `relacionamentos_tablehub.csv`
- `RELATORIO_TESTES_FINALIZACAO.md`
- `sql/TEST_CHAVES(FK).sql`
- `src/main/java/com/itb/config/RequestRateLimitFilter.java`
- `src/main/java/com/itb/config/SecurityConfig.java`
- `src/main/java/com/itb/controller/AtendimentoController.java`
- `src/main/java/com/itb/controller/MesaController.java`
- `src/main/java/com/itb/controller/MesaPanelController.java`
- `src/main/java/com/itb/controller/OrderController.java`
- `src/main/java/com/itb/dto/MesaCreateRequest.java`
- `src/main/java/com/itb/dto/MesaPanelResponse.java`
- `src/main/java/com/itb/dto/MesaQrResponse.java`
- `src/main/java/com/itb/dto/MesaResponse.java`
- `src/main/java/com/itb/dto/MesaSummaryResponse.java`
- `src/main/java/com/itb/dto/MesaUpdateRequest.java`
- `src/main/java/com/itb/dto/TableContextRequest.java`
- `src/main/java/com/itb/dto/TableContextResponse.java`
- `src/main/java/com/itb/model/Mesa.java`
- `src/main/java/com/itb/repository/MesaRepository.java`
- `src/main/java/com/itb/service/AtendimentoService.java`
- `src/main/java/com/itb/service/DashboardService.java`
- `src/main/java/com/itb/service/JwtService.java`
- `src/main/java/com/itb/service/MesaPanelService.java`
- `src/main/java/com/itb/service/MesaService.java`
- `src/main/java/com/itb/service/PaymentService.java`
- `src/main/java/com/itb/service/TableAccessService.java`
- `src/test/java/com/itb/config/MesaPanelHttpTest.java`
- `src/test/java/com/itb/config/RequestBoundaryHttpTest.java`
- `src/test/java/com/itb/config/RequestRateLimitFilterTest.java`
- `src/test/java/com/itb/config/SecurityHttpTest.java`
- `src/test/java/com/itb/controller/MesaContextHttpTest.java`
- `src/test/java/com/itb/service/AtendimentoServiceTest.java`
- `src/test/java/com/itb/service/DashboardServiceTest.java`
- `src/test/java/com/itb/service/MesaPanelServiceTest.java`
- `src/test/java/com/itb/service/MesaServiceTest.java`
- `src/test/java/com/itb/service/PaymentNotificationTransactionTest.java`
- `src/test/java/com/itb/service/PaymentServiceTest.java`
- `src/test/java/com/itb/service/TableAccessServiceTest.java`

## Frontend

- `.env.example`
- `README.md`
- `src/hooks/useTableContext.js`
- `src/pages/Atendimento/Atendimento.jsx`
- `src/pages/Cardapio/Cardapio.jsx`
- `src/pages/Cardapio/CatalogDialogs.jsx`
- `src/pages/CarrinhoCliente/CarrinhoCliente.jsx`
- `src/pages/ChamarGarcom/ChamarGarcom.jsx`
- `src/pages/Clientes/Clientes.css`
- `src/pages/Clientes/Clientes.jsx`
- `src/pages/ComprovanteCliente/ComprovanteCliente.jsx`
- `src/pages/ConfirmarPedido/ConfirmarPedido.jsx`
- `src/pages/Dashboard/Dashboard.jsx`
- `src/pages/Financeiro/Financeiro.jsx`
- `src/pages/MenuCliente/MenuCliente.jsx`
- `src/pages/Mesas/Mesas.css`
- `src/pages/Mesas/Mesas.jsx`
- `src/pages/PagamentoCliente/PagamentoCliente.jsx`
- `src/pages/PedidoConfirmado/PedidoConfirmado.jsx`
- `src/pages/Pedidos/Pedidos.jsx`
- `src/pages/SolicitarConta/SolicitarConta.jsx`
- `src/services/api.js`
- `src/services/tableAccess.js`
- `src/services/tableQr.js`
- `src/utils/anonymousOrders.js`
- `src/utils/catalogForm.js`
- `tests/adminCatalog.test.js`
- `tests/apiAccess.test.js`
- `tests/browserSmoke.mjs`
- `tests/tableAccess.test.js`

## Preservação e exclusões

Os CSVs e o helper SQL fornecidos pelo usuário permaneceram idênticos ao snapshot. No SQL principal, somente os literais das linhas 817 e 818 foram substituídos por exemplos fictícios; todas as demais linhas e bytes foram conferidos. Nenhum comando foi executado no banco.

Backups, arquivos .env, caches, dependências, artefatos de build e perfis de navegador ficam fora dos commits. O SQL mantém a linha em branco final já existente, pois não foi autorizada outra edição nesse arquivo.

Resultados, limites e commits por etapa: RELATORIO_TESTES_FINALIZACAO.md. Diagnóstico e decisões: AUDITORIA_FINALIZACAO.md.
