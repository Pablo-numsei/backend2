# TableHub — backend

API REST em Java 21 / Spring Boot, autenticação JWT, JPA/JDBC e SQL Server. O frontend React fica no repositório [tablehub-front-v2](https://github.com/Pablo-numsei/tablehub-front-v2).

## Executar

Requisitos: JDK 21, SQL Server acessível e Maven Wrapper com acesso inicial às dependências. Antes de iniciar, configure as variáveis no processo, IDE ou gerenciador de segredos; o Spring Boot deste projeto **não carrega `.env` automaticamente**.

| Variável | Finalidade |
| --- | --- |
| `DB_URL` | JDBC SQL Server, incluindo nome do banco, criptografia e validação do certificado |
| `DB_USER`, `DB_PASSWORD` | Conta do banco com os privilégios necessários às consultas/procedures; evitar conta administrativa |
| `JWT_SECRET` | Segredo aleatório de pelo menos 32 bytes, codificado em Base64; não reutilizar senha humana |
| `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT` | Opcionais para Web Push; chave privada somente no servidor |
| `SHOW_SQL` | Padrão `false`; não habilitar logs detalhados em produção |

Para configurar origens sem editar código, use a propriedade `app.cors.allowed-origins`, por exemplo no argumento `--app.cors.allowed-origins=https://seu-frontend`. Os defaults locais são `http://localhost:5173` e `http://localhost:8686`.

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

O servidor usa a porta 8080 por padrão. Depois de `verify`, o artefato executável fica em `target/backend-0.0.1-SNAPSHOT.jar`; iniciar com `java -jar` exige as mesmas variáveis.

Para produção, DB_URL deve usar `encrypt=true;trustServerCertificate=false` e um certificado confiável cujo nome corresponda ao host. O default de desenvolvimento aceita certificado não confiável; não é uma configuração recomendada para produção.

## Banco e primeiro administrador

O modelo existente usa Mesas, Produtos, Categorias, Pedidos, Itens_Pedido, Status_Pedido, Historico_Status_Pedido, Pagamentos, Usuarios, Perfis, Atendimentos_Mesa e inscrições push. Pedidos e transições dependem das procedures/triggers já previstas nos scripts; `ddl-auto=none` impede criação automática do schema.

Nenhum script de banco é executado automaticamente pelo aplicativo ou pelos testes desta entrega. Antes de instalar do zero, um responsável deve revisar o SQL, escolher o banco correto, validar o backup e conferir procedures/triggers/constraints. Não execute scripts de estrutura ou seed sobre o banco operacional sem esse planejamento.

`Usuarios` representa a equipe. A primeira conta Administrador exige provisionamento controlado pelo responsável pelo banco; não há cadastro público para criar administradores. A conta real já existente no banco não foi alterada. O e-mail de exemplo no SQL é `admin@example.invalid`; seu hash fictício foi gerado a partir de senha aleatória descartada e **não oferece uma senha de demonstração para login**. Em instalação nova, gere sua própria senha/hash BCrypt localmente por ferramenta confiável e faça o provisionamento fora do Git. Após o primeiro acesso, a API administrativa permite cadastrar funcionários.

Novas senhas precisam de pelo menos 12 caracteres e no máximo 72 bytes UTF-8. Não registrar senha, JWT, chave privada ou hash de credencial real em commits ou logs.

## Arquitetura

| Camada | Responsabilidade |
| --- | --- |
| `config` | Spring Security, CORS, limitação de requisições e STOMP |
| `controller` / `dto` | Contratos HTTP, validação e respostas mínimas |
| `service` | Autorização por recurso, regras de negócio, transações e notificações |
| `repository` / `model` | Acesso JPA, locks e mapeamento do schema existente |
| `JdbcTemplate` / SQL Server | Procedures de pedido/status, relatórios e atendimento |

O backend calcula preços/valores a partir da persistência. O navegador não determina o total cobrável nem a identidade do funcionário que altera o pedido.

## Perfis e endpoints

`Equipe` abaixo significa Administrador, Garçom ou Cozinha. O backend verifica o perfil, mesmo quando a interface esconde a ação.

| Método e rota | Acesso / uso |
| --- | --- |
| `POST /api/auth/login` | Público, limitado por IP; retorna JWT de equipe |
| `GET /api/v1/produtos[/{id}]` | Público, catálogo |
| `POST /api/v1/produtos`, `PUT/DELETE /api/v1/produtos/{id}` | Administrador |
| `PATCH /api/v1/produtos/{id}/estoque` | Administrador; `{delta, expectedStockQuantity}` |
| `GET/POST /api/v1/categorias` | Administrador |
| `GET /api/mesas`, `GET /api/mesas/{id}` | Público; somente mesas ativas, sem QR interno |
| `POST /api/mesas` | Administrador; somente `{number}`; QR interno gerado no servidor |
| `PUT/DELETE /api/mesas/{id}` | Administrador; alterar número/desativar |
| `GET /api/mesas/painel` | Administrador/Garçom; todas as mesas, situação e pedidos em aberto |
| `GET /api/mesas/{id}/qr` | Administrador/Garçom; obtém QR assinado |
| `POST /api/mesas/contexto` | Público com `{qrToken}` válido; retorna contexto curto da mesa |
| `GET /api/status-pedidos` | Público |
| `GET /api/pedidos`, `GET /api/pedidos/status/{status}` | Equipe |
| `POST /api/pedidos` | Visitante com `X-Table-Token` da mesa ou equipe ativa |
| `GET /api/pedidos/{id}` | `X-Order-Token` correspondente ou equipe |
| `GET /api/itens-pedido/pedido/{id}` | `X-Order-Token` correspondente ou equipe |
| `PATCH /api/pedidos/{id}/status` | Equipe; Cozinha prepara/pronto, Garçom entrega, Administrador administra o fluxo |
| `GET /api/payments/{orderId}` | Token do pedido ou equipe |
| `POST /api/payments/{orderId}/process` | Token do pedido ou equipe; cria registro pendente |
| `POST /api/payments/{orderId}/confirm` | Administrador/Garçom |
| `POST /api/payments/{orderId}/cancel` | Administrador/Garçom; cancela registro, sem estorno bancário |
| `GET /api/atendimentos`, `PATCH /api/atendimentos/{id}/status` | Administrador/Garçom |
| `POST /api/atendimentos` | Token do pedido quando há pedido; caso contrário contexto da mesa/equipe |
| `GET /api/dashboard/resumo` | Administrador/Garçom |
| `GET /api/usuarios[/{id}]`, `POST /api/usuarios`, `GET /api/perfis` | Administrador |
| `GET /api/push/public-key` | Público; somente chave pública |
| `POST /api/push/subscriptions` | Token do pedido correspondente |
| `POST /api/push/staff-subscriptions` | Administrador/Garçom |
| `/ws` | Transporte WebSocket/STOMP; CONNECT/assinatura exigem JWT de equipe |

Respostas principais: 400 (entrada inválida), 401 (login necessário), 403 (sem autorização/capacidade), 404 (recurso indisponível), 409 (duplicidade/conflito), 413 (corpo acima de 256 KiB), 429 (frequência excedida).

## QR, pedidos, mesas e atendimento

O QR impresso contém link público com número e assinatura; o frontend troca esse QR por um token de mesa de duas horas, guardado na aba. Criar pedido ou atendimento pré-pedido exige esse contexto, validado novamente contra mesa ativa e versão atual do QR. Um link com apenas `?mesa=1` permite consultar o cardápio, sem habilitar novas ações. **Os QR antigos precisam ser substituídos.**

Esse contexto não dá acesso a pedidos anteriores da mesa. Cada pedido criado recebe seu próprio token de acesso. QR público pode ser fotografado/compartilhado e não prova presença física. Trocar o segredo de assinatura invalida QRs e sessões que dependem dele; planeje nova emissão.

Uma mesa fica operacionalmente ocupada enquanto existir pedido confirmado/não excluído ainda não entregue **ou** sem pagamento confirmado. Só fica livre quando todos os pedidos estiverem entregues e pagos. Não há reserva, presença física, sessão de consumo ou comanda persistida.

Atendimento tem `ENVIADO → EM_ATENDIMENTO → CONCLUIDO`. Mesma mesa/tipo/pedido/detalhe normalizado reutiliza chamado ainda aberto; detalhes distintos continuam separados. A criação é serializada pela linha da mesa. Repetir uma transição já concluída não altera datas novamente. A validação desse lock em SQL Server real continua pendente.

## Pagamento e notificações

As telas desta entrega identificam o pagamento como **simulado**: nenhuma chamada efetua PIX, cobrança de cartão ou estorno. O cliente solicita registro `PENDENTE`; Administrador/Garçom confirma a simulação como `PAGO`. O schema não separa demonstração de operação real: não execute simulações no banco operacional para homologar o sistema.

Push é agendado após commit, com fila e timeouts, mas sem persistência durável. Falha/reinício pode perder notificações; consultar o estado oficial continua funcionando. Inscrições antigas de equipe precisam de revisão, pois o modelo atual não permite revogação por funcionário.

## Entrega e limites

- [Diagnóstico, evidências e plano](AUDITORIA_FINALIZACAO.md).
- [Resultados e roteiro de testes](RELATORIO_TESTES_FINALIZACAO.md).
- [Roteiro de demonstração e implantação](GUIA_ENTREGA.md).

Sem banco de teste nesta rodada, testes unitários/HTTP/navegador usam persistência e API simuladas. Homologação SQL Server, scanner físico de QR, notificações reais, atualização coordenada do framework e implantação externa continuam verificações separadas. Não houve mudança de schema ou publicação em produção.
