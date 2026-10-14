# Arquivos alterados nesta entrega

> Histórico de 13/10/2026. Para a rodada de finalização de 14/10/2026, consulte [ARQUIVOS_FINALIZACAO.md](ARQUIVOS_FINALIZACAO.md).

Lista de alterações e arquivos novos desta sessão; inclui testes e documentação. As alterações SQL que já existiam foram verificadas contra o snapshot e são publicadas em commit separado, sem executar migrações.

## Backend — D:\backend2

- `.gitignore`
- `pom.xml`
- `src/main/java/com/itb/config/CorsConfiguration.java`
- `src/main/java/com/itb/config/SecurityConfig.java`
- `src/main/java/com/itb/config/WebSocketConfig.java`
- `src/main/java/com/itb/controller/AtendimentoController.java`
- `src/main/java/com/itb/controller/ItemPedidoController.java`
- `src/main/java/com/itb/controller/OrderController.java`
- `src/main/java/com/itb/controller/PaymentController.java`
- `src/main/java/com/itb/controller/ProductController.java`
- `src/main/java/com/itb/controller/PushNotificationController.java`
- `src/main/java/com/itb/controller/UsuarioController.java`
- `src/main/java/com/itb/dto/AtendimentoCreateRequest.java`
- `src/main/java/com/itb/dto/ItemPedidoRequest.java`
- `src/main/java/com/itb/dto/LoginRequest.java`
- `src/main/java/com/itb/dto/PaymentRequest.java`
- `src/main/java/com/itb/dto/PedidoCreateRequest.java`
- `src/main/java/com/itb/dto/UsuarioCreateRequest.java`
- `src/main/java/com/itb/exception/GlobalExceptionHandler.java`
- `src/main/java/com/itb/repository/OrderRepository.java`
- `src/main/java/com/itb/repository/PaymentRepository.java`
- `src/main/java/com/itb/repository/ProductRepository.java`
- `src/main/java/com/itb/service/AtendimentoService.java`
- `src/main/java/com/itb/service/AuthService.java`
- `src/main/java/com/itb/service/JwtService.java`
- `src/main/java/com/itb/service/PaymentService.java`
- `src/main/java/com/itb/service/PedidoService.java`
- `src/main/java/com/itb/service/ProductService.java`
- `src/main/java/com/itb/service/PushNotificationService.java`
- `src/main/java/com/itb/service/SocketService.java`
- `src/main/resources/application.properties`
- `src/test/java/com/itb/BackendApplicationTests.java`
- `ENTREGA_SEGURANCA.md`
- `scripts/verify-recovery.mjs`
- `src/main/java/com/itb/config/RequestRateLimitFilter.java`
- `src/main/java/com/itb/config/WebSocketAuthenticationInterceptor.java`
- `src/main/java/com/itb/dto/ItemPedidoResponse.java`
- `src/main/java/com/itb/dto/OrderResponse.java`
- `src/main/java/com/itb/dto/PaymentResponse.java`
- `src/main/java/com/itb/dto/StockAdjustmentRequest.java`
- `src/main/java/com/itb/service/CurrentUserService.java`
- `src/main/java/com/itb/service/OrderAccessService.java`
- `src/main/java/com/itb/service/PushEndpointValidator.java`
- `src/test/java/com/itb/config/RequestRateLimitFilterTest.java`
- `src/test/java/com/itb/config/SecurityHttpTest.java`
- `src/test/java/com/itb/config/WebSocketAuthenticationInterceptorTest.java`
- `src/test/java/com/itb/controller/PushNotificationControllerTest.java`
- `src/test/java/com/itb/dto/PublicOrderResponseTest.java`
- `src/test/java/com/itb/dto/RequestValidationTest.java`
- `src/test/java/com/itb/service/AuthServiceTest.java`
- `src/test/java/com/itb/service/CurrentUserServiceTest.java`
- `src/test/java/com/itb/service/JwtServiceTest.java`
- `src/test/java/com/itb/service/OrderAccessServiceTest.java`
- `src/test/java/com/itb/service/PaymentServiceTest.java`
- `src/test/java/com/itb/service/ProductServiceTest.java`
- `src/test/java/com/itb/service/PushEndpointValidatorTest.java`
- `src/test/java/com/itb/service/PushNotificationServiceTest.java`
- `ARQUIVOS_ALTERADOS.md`

## Frontend — D:\tablehub-front-v2

- `package.json`
- `src/components/layout/ManagementSidebar.css`
- `src/components/layout/ManagementSidebar.jsx`
- `src/components/layout/Navbar.jsx`
- `src/main.jsx`
- `src/pages/AcompanharPedido/AcompanharPedido.jsx`
- `src/pages/CarrinhoCliente/CarrinhoCliente.jsx`
- `src/pages/ChamarGarcom/ChamarGarcom.jsx`
- `src/pages/ComprovanteCliente/ComprovanteCliente.jsx`
- `src/pages/ConfirmarPedido/ConfirmarPedido.jsx`
- `src/pages/Estoque/Estoque.jsx`
- `src/pages/Home/Home.jsx`
- `src/pages/Login/Login.jsx`
- `src/pages/MenuCliente/MenuCliente.jsx`
- `src/pages/PagamentoCliente/PagamentoCliente.jsx`
- `src/pages/Pedidos/Pedidos.jsx`
- `src/pages/Recursos/Recursos.jsx`
- `src/pages/Register/Register.jsx`
- `src/pages/SolicitarConta/SolicitarConta.jsx`
- `src/routes/AppRoutes.jsx`
- `src/services/api.js`
- `src/utils/customerSession.js`
- `.gitignore`
- `src/components/auth/AuthProvider.jsx`
- `src/hooks/useCustomerCatalog.js`
- `src/hooks/useCustomerOrder.js`
- `src/services/authSession.js`
- `src/services/customerCatalog.js`
- `src/services/orderAccess.js`
- `src/services/orderPayments.js`
- `tests/apiAccess.test.js`
- `tests/authSession.test.js`
- `tests/browserSmoke.mjs`
- `tests/customerFlow.test.js`

## Arquivos pré-existentes preservados

- Exclusão de `database/TableHub.sql` e novo `database/table_hub_db.sql`.
- Alterações em `sql/patch_atendimentos_mesa.sql`, `sql/patch_push_subscriptions.sql` e `sql/seed_cardapio_tablehub.sql`.
- `package-lock.json` e `src/tablehub-front-v2.code-workspace`.
- Dependências locais `node_modules`; agora ignoradas pelo Git.

A publicação foi autorizada pelo usuário: backend em `Pablo-numsei/backend2` e frontend em `Pablo-numsei/tablehub-front-v2`, nas branches `main`. O lockfile é incluído para reproduzir as dependências verificadas; workspace, dependências locais, builds e snapshots de recuperação ficam fora dos commits. Nenhuma alteração de schema foi aplicada.
