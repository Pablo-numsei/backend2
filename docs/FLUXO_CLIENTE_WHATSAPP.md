# TableHub — identificação rápida e WhatsApp (feature branch)

## Fluxo final

1. Cliente escaneia QR de uma mesa e abre diretamente `/menu?mesa=N&qr=...`.
2. Escolhe pratos e adiciona ao carrinho sem cadastro/login.
3. Ao continuar, preenche **nome obrigatório** em `/identificar-pedido`; telefone e WhatsApp são opcionais.
4. Confirma o pedido; o backend valida token da mesa, estoque e preços.
5. A equipe vê **nome do cliente + mesa + itens**.
6. Quem autorizou WhatsApp recebe mensagens de status se a integração Meta estiver configurada.

O CPF **não** é solicitado nesse fluxo; pagamento atual é simulado. A conta de cliente é opcional e o login da equipe é independente.

## Migração SQL (obrigatória)

**Faça backup e revise os objetos existentes antes da alteração no Somee.**
O script `sql/patch_identificacao_pedidos_whatsapp.sql`:

- adiciona `Pedidos.nome_cliente` (permite nulo para compatibilidade com histórico/equipe);
- adiciona telefone de WhatsApp, flag e instante de consentimento;
- cria `WhatsApp_Outbox` para envios assíncronos com tentativas;
- atualiza `dbo.sp_CriarPedido`, preservando a transação, estoque, preço e validações.

O script **não é executado pelo Spring Boot**, pois `ddl-auto=none`.
Ele foi construído sobre `database/table_hub_db.sql`. Compare a definição de `sp_CriarPedido` existente no seu banco com o script antes de aplicá-lo, para não perder modificações realizadas diretamente no SQL Server.

## Integração real com WhatsApp Cloud API (Meta)

Configuração no backend (somente por variáveis de ambiente; **não commitar tokens**):

| Variável | Finalidade |
|---|---|
| `WHATSAPP_ENABLED` | `false` por padrão; use `true` **após** validar o template aprovado |
| `WHATSAPP_ACCESS_TOKEN` | Token autorizado com `whatsapp_business_messaging` |
| `WHATSAPP_PHONE_NUMBER_ID` | Identificador do telefone registrado na Meta |
| `WHATSAPP_GRAPH_VERSION` | Versão da Graph API disponível na sua conta, por exemplo `v23.0` |
| `WHATSAPP_TEMPLATE_NAME` | Modelo aprovado, exemplo `tablehub_status_pedido` |
| `WHATSAPP_TEMPLATE_LANGUAGE` | Idioma do modelo aprovado, por exemplo `pt_BR` |

É necessário criar e obter aprovação de um **template de utilidade** no WhatsApp Manager. O modelo aqui adotado precisa ter **exatamente três parâmetros de corpo**, nessa ordem:

```text
Olá {{1}}! O pedido #{{2}} está com status: {{3}}.
```

- `{{1}}`: nome do cliente;
- `{{2}}`: identificador do pedido;
- `{{3}}`: `Recebido`, `Em preparo`, `Pronto` ou `Entregue`.

Só habilite a variável depois de o nome, idioma e parâmetros coincidirem com o template aprovado. A API exige modelo autorizado para mensagens iniciadas pela empresa; poderão existir custos de envio. O frontend consulta `GET /api/whatsapp/status` antes de mostrar a opção habilitada.

**Sem credenciais, a caixa WhatsApp fica indisponível e nenhum envio é tentado.**
Cliente ainda pode pedir sem telefone ou cadastro.

O backend armazena telefone apenas **se o cliente tiver marcado o consentimento**. A fila fica em `dbo.WhatsApp_Outbox` sem repetir número telefônico. O processamento em lote ocorre a cada aproximadamente 15 s, até cinco tentativas; falhas de rede não desfazem pedidos nem mudanças de status.

A situação `ACEITO` significa apenas que a Meta aceitou a requisição HTTP. **Esta versão não recebe Webhooks de entrega/leitura**, então não afirma que a mensagem chegou ao aparelho. O endpoint protegido `POST /api/pedidos/{id}/whatsapp/cancelar` com `X-Order-Token` cancela novas notificações e remove o número salvo.

## Segurança e privacidade

- Não reutilizar JWT de cliente ou funcionário para abrir outra mesa.
- Validar nome no servidor e DDD brasileiro antes de armazenar telefone com consentimento.
- Nunca incluir telefone ou token Meta nas respostas públicas `OrderResponse`.
- Não inserir CPF nesse endpoint.
- Não enviar propaganda: a autorização vale apenas para os status deste pedido.
- Definir prazo de retenção para dados pessoais e rotina de descarte antes da produção.
- Para confirmar entrega/leitura, adicionar webhook oficial validado pela assinatura da Meta em uma etapa posterior.

## Testes locais

1. Aplique o patch em **banco de testes** e execute `.\mvnw.cmd test`.
2. Rode frontend: `npm test`, `npm run build`, `npm run dev`.
3. Escaneie QR de mesa válida. Confira que o cardápio abre sem login.
4. Inclua itens, continue para identificação e envie com nome **sem telefone**.
5. Verifique que a cozinha exibe o nome no pedido e que estoque/status não mudaram de regra.
6. Teste nome vazio (impedido) e telefone inválido com WhatsApp habilitado (impedido).
7. Teste opt-in e depois cancelamento com o token da própria encomenda.
8. Para envio real, use número de teste autorizado da Meta e template aprovado. **Não utilizar números reais de terceiros em testes.**

## Riscos de implantação

- Frontend desta branch exige backend desta branch e migração SQL **antes** de liberar pedidos.
- Em ambientes com limite de recursos, o envio em fila mantém o caminho de pedido independente de latência da Meta.
- A implantação com `WHATSAPP_ENABLED=false` permite validar todo o pedido anonimamente antes de contratar a integração.
