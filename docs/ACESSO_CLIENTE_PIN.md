# TableHub — vitrine do cardápio + usuário e PIN

**Fluxo de produto acordado**

1. O visitante escaneia o QR Code e abre o cardápio digital da mesa **sem login**.
2. Fotos, categorias, nomes e preços permanecem visíveis: **modo vitrine**.
3. Para adicionar um produto ao carrinho, o visitante precisa de uma conta com **nome de usuário e PIN de quatro dígitos**.
4. Quem não tem conta escolhe usuário/PIN, confirma o PIN e entra automaticamente na própria sessão.
5. Quem já tem conta entra com usuário/PIN e volta para o cardápio da mesa.
6. A confirmação exige **token de cliente** + **token válido da mesa**, mesmo que o frontend seja burlado.
7. Pedidos guardam ID da conta + uma cópia do nome de usuário para a cozinha e o garçom.
8. Funcionários continuam usando contas e permissões independentes.
9. **WhatsApp, CPF, e-mail e telefone não fazem parte dessa etapa.**

## Migração do banco

**Não execute no banco Somee operacional sem backup e validação em banco de testes.**

Aplique nesta ordem:

1. `sql/patch_clientes_pin.sql`
2. `sql/patch_pedidos_cliente_pin.sql`

O primeiro patch cria/atualiza `dbo.Clientes.pin_hash`, deixa as colunas antigas `email` e `senha_hash` opcionais e mantém registros antigos.
O segundo associa `dbo.Pedidos` a uma conta de cliente e atualiza `dbo.sp_CriarPedido` mantendo as operações transacionais de estoque.
**Compare a procedure real do Somee antes de sobrescrevê-la**, pois ela pode ter mudanças que não estão no script versionado.

### Contas antigas

Contas cadastradas pelo sistema anterior de e-mail/senha **não têm PIN** e não poderão entrar com esse novo login.
Uma migração segura deve exigir que o titular defina um PIN por método verificado, sem atribuir PINs previsíveis.
**Não redefina a senha antiga para um PIN automaticamente e não apague contas sem backup.**

## Proteção de PIN

Quatro dígitos oferecem apenas 10 mil combinações.
O serviço usa BCrypt sobre HMAC-SHA256 com segredo armazenado no servidor; o segredo deriva de `JWT_SECRET` por padrão ou pode ser configurado separadamente com `CUSTOMER_PIN_PEPPER` (veja abaixo).
Existe limite de tentativas por IP no filtro HTTP e bloqueio por usuário após cinco PINs incorretos por quinze minutos.
O bloqueio atual usa memória local; em implantação com várias instâncias precisa de Redis ou banco compartilhado para proteção distribuída.
Não exponha contas PIN a ações financeiras sensíveis nem à listagem de pedidos de outros consumidores.
PIN sem e-mail **não possui recuperação automática**: é preciso definir procedimento verificado antes de produção.

No `application.properties` pode ser configurado:
```properties
customer.pin.pepper=${CUSTOMER_PIN_PEPPER:${JWT_SECRET}}
```
Nunca grave valores reais de `JWT_SECRET` ou `CUSTOMER_PIN_PEPPER` no Git. Trocar esse segredo invalida os PINs atuais.

## Testar

Backend: `.\\mvnw.cmd test`. Frontend: `npm test`, `npm run build`, `npm run dev`.

Verifique: QR abre o cardápio sem login; botão `Entrar para pedir` aparece antes de cadastro; filtros e fotos continuam disponíveis; usuário com PIN tem acesso ao carrinho; sem sessão, `POST /api/pedidos` recebe 401; token de outra finalidade não serve; QR de outra mesa não serve; funcionário mantém login próprio; PIN errado recebe limite de tentativas.

**Somente abrir PR após testes e revisão da migração; não mesclar com o PR de WhatsApp.**
