# TableHub — diagnóstico e execução da finalização

Data: 14/10/2026. Baseline: backend `2c62ee0`, frontend `ff8caa6`.
As referências de linha do diagnóstico apontam para esses commits, antes desta rodada.

## Escopo, autorizações e recuperação

O anexo recebido começa em um item intermediário e contém as seções 6–26. Não foram inventados requisitos das seções ausentes. O trabalho preserva React, Spring Boot, SQL Server e a identidade visual existente.

O usuário autorizou expressamente: correções de preço/busca; substituir clientes fictícios por pedidos anônimos reais; identificar pagamentos como simulados; implantar QR assinado, com substituição dos QR antigos; regra de ocupação até entrega e pagamento; reutilização de chamados abertos idênticos. Também autorizou sanitizar somente o e-mail/hash potencialmente reais do SQL, substituindo-os por exemplos fictícios e preservando todo o restante. Não há banco de teste disponível. Alterações de schema e implantação em produção continuam sem autorização.

Snapshot local ignorado pelo Git: `.tablehub-recovery/20261014-finalizacao-925f78b6/`. Contém fontes dos dois projetos, HEADs/status iniciais e checksums dos arquivos de banco já alterados pelo usuário. Arquivos `.env` foram excluídos; os snapshots podem conter material sensível já presente no SQL e devem continuar privados. Nenhuma rotina de banco foi executada.

Working tree inicial do backend: `database/table_hub_db.sql` modificado; `estrutura_tablehub.csv`, `relacionamentos_tablehub.csv` e `sql/TEST_CHAVES(FK).sql` não rastreados. Esses arquivos pertencem ao usuário e não serão sobrescritos nem publicados indiscriminadamente.

## Resultado inicial

Não foi confirmado um novo comprometimento crítico de autenticação nesta inspeção. Existem riscos altos de abuso do fluxo de mesa, além de problemas operacionais comprovados. Isso não constitui garantia de ausência de outras vulnerabilidades.

As correções anteriores permanecem presentes: cadastro administrativo protegido, autorização por perfil no servidor, leitura de pedido/itens/pagamento por token específico, separação entre tokens de funcionário e pedido, DTOs reduzidos, confirmação de pagamento restrita à equipe, locks financeiros/estoque, CAS no atendimento e push após commit com fila/timeout.

| ID | Severidade / prioridade | Estado inicial e evidência | Alteração proposta / impacto |
| --- | --- | --- | --- |
| SEC-01 | Alta / P0 | `OrderController.java:48`, `SecurityConfig.java:72`: pedido novo aceita apenas um `mesaId` público; QR não é validado. | Exigir contexto assinado da mesa para visitante; equipe autorizada usa sua sessão. Altera QR impresso e fluxo inicial, preservando tokens de pedidos anteriores. |
| SEC-02 | Alta / P0 | `AtendimentoController.java:37`: chamada sem pedido não exige token; `AtendimentoService.java:79`: cada POST insere/notifica. | Contexto da mesa e prevenção proporcional de abuso. Deduplicação depende da regra aprovada para não juntar chamados distintos. |
| SEC-03 | Média / P0 | `MesaController.java:29,40`: GET público retorna a entidade, incluindo QR e mesas inativas. | DTO público mínimo e QR assinado disponível somente à equipe. Necessário antes de usar QR como capacidade. |
| SEC-04 | Média / P1 | `MesaController.java:59,85`: POST recebe entidade com ID e pode fazer merge de registro existente. | DTO restrito, ID gerado no servidor e rejeição de campos administrativos. Testar duplicação e tentativa de sobrescrita. |
| SEC-05 | Alta condicionada / P0 de publicação | `database/table_hub_db.sql:817–818`: conta e hash potencialmente reais, adicionados antes desta rodada. Não é exposição pública comprovada. | Não publicar esse trecho sem sanitização autorizada. Não reproduzir valores no relatório/logs. Confirmar se a credencial é de exemplo antes de recomendar rotação. |
| FUN-01 | Média / P1 | `CatalogDialogs.jsx:46`: comparação exata de ponto flutuante rejeita 19,99 e 1,10. Reproduzido com Node. | Validar texto decimal e converter em centavos. Não altera preço de produtos existentes. |
| FUN-02 | Alta operacional / P1 | `DashboardService.java:187` usa TOP 8; `Mesas.jsx:53` assume Livre para mesa ausente. | Situação completa para todas as mesas, sem inferir liberdade pela ausência no resumo. Definir regra operacional sem inventar presença física. |
| FUN-03 | Média / P1 | `Mesas.jsx:57,106`: pedido sempre nulo e cadastro desabilitado; carga somente inicial. | Formulário integrado à API administrativa, pedidos reais associados e atualização controlada. |
| FUN-04 | Média / P1 | `Clientes.jsx:16–67,91`: cinco demos somados aos pedidos locais e apresentados como movimentação. | Mostrar pedidos anônimos persistidos, filtros/estados e totais do recorte. Não criar entidade de cliente, visitas ou dados pessoais. |
| FUN-05 | Média / P1 | Menu/confirmar/chamados usam fallback Mesa 04; verificação da mesa acontece tarde. | Remover associação implícita, validar QR/contexto antes de novas ações. Cardápio público continua consultável. |
| FUN-06 | Média / P1 | `CatalogDialogs.jsx:64,75`: estoque carregado pode ficar antigo; 409 não atualiza o formulário. | Recarregar apenas a referência de estoque no conflito, preservar edição e exigir nova confirmação. |
| FUN-07 | Média / P1 | `CatalogDialogs.jsx:92,99`: backdrop/Escape fecham durante POST, apesar dos botões bloqueados. | Impedir fechamento durante gravação e manter resultado visível. |
| FUN-08 | Baixa / P2 | `Cardapio.jsx:87,92`: consulta com acento é comparada a nomes sem acento. | Normalizar consulta e dados igualmente. |
| EST-01 | Alta / P1 | `PedidoService.java:116`: retries de criação executam outra procedure e consomem estoque novamente. | Reduzir duplicação no cliente; idempotência durável entre reinícios exige armazenamento autorizado. Não prometer garantia com cache em memória. |
| EST-02 | Média / P2 | `Atendimento.jsx:72,102`: polling pode sobrepor requisições e aplicar resposta antiga. | Serializar/abortar cargas, pausar aba oculta e aplicar resultado da ação sem regredir estado. |
| EST-03 | Média / P2 | `Pedidos.jsx:92–110`: consulta individual de itens para cada pedido a cada cinco segundos. | Otimização posterior com contrato de listagem/paginação. Evitar refatoração ampla nesta entrega. |

## Evidências e reprodução segura

Nenhum procedimento abaixo deve ser executado contra banco operacional. Casos que escrevem dados exigem mocks ou SQL Server de teste separado.

### SEC-01 — criação de pedidos sem contexto de mesa

- Pré-condições: conhecer ID de mesa ativa e produto disponível; não requer login.
- Reprodução isolada: MockMvc com repositórios/procedure simulados, POST `/api/pedidos` contendo `mesaId` válido e itens, sem token de mesa. O baseline admite criação; a correção deverá responder 403 sem chamar o serviço de criação.
- Impacto: pedidos falsos e indisponibilidade artificial do estoque. Não concede leitura de pedidos existentes de terceiros.
- Reteste exigido: QR/contexto correto, expirado, adulterado, de outra mesa; mesa inativa; tokens de funcionário/pedido usados no lugar errado; mesa com número diferente do ID.

### SEC-02 — chamadas arbitrárias e repetidas

- Pré-condições: conhecer mesa ativa; caminho sem `pedidoId` é anônimo.
- Reprodução isolada: POST `/api/atendimentos` com `mesaId`, `tipo=GARCOM` e sem token. Repetir com mocks, sem enviar notificações reais.
- Impacto: spam para equipe e registros desnecessários. Com `pedidoId`, a capacidade do pedido já é exigida e a correspondência pedido/mesa deve continuar validada.
- Reteste: ausência/contexto incorreto; detalhes distintos; repetição de chamado aberto; concorrência; repetir transição sem mudar datas novamente; garantir push somente para nova criação confirmada.

### SEC-03/04 — contrato de mesas

- Pré-condições: GET é público; exploração do merge exige conta Administrador.
- Reprodução isolada: GET público com mesa ativa/inativa e verificar JSON; POST administrativo com ID de mesa existente e número/QR diferentes, verificar que não atualiza essa mesa.
- Impacto: metadados desnecessários e sobrescrita acidental/indevida por operação de criação. Não é escalonamento anônimo de privilégios.
- Reteste: whitelists, número duplicado, número não positivo, limites de tamanho, 401/403 por papel, concorrência tratada pela unicidade existente.

### FUN-01 — preço decimal

Reprodução sem servidor: `Math.round(19.99 * 100) !== 19.99 * 100` resulta verdadeiro. A validação deve aceitar `19,99`, `19.99`, `1,10`, rejeitar casas excedentes, zero, negativos, notação inesperada e valor acima do limite persistido.

### FUN-02/03/04 — integridade da apresentação

Usar API sintética com pelo menos nove mesas e pedido na nona; conferir situação, pedido associado, filtros e atualização. Para Clientes, usar pedidos de mesas/datas diferentes, conferir que cada registro permanece anônimo e que não aparecem nomes/e-mails/frequências inventados. Totais de pedidos não são recebimentos bancários nem contagem de pessoas.

## Auditoria por domínio

### Autenticação, autorização e JWT

Assinatura/expiração são validadas no backend; propósito distingue equipe de pedido. Permissões administrativas não dependem apenas do React. Testes existentes cobrem manipulação, expiração, sessão inválida, perfil e acesso por pedido. Novos tokens de QR e contexto terão propósitos separados e não poderão autenticar equipe ou acessar pedidos alheios.

Logout remove a sessão do navegador; um JWT copiado permanece válido até expirar, salvo conta desativada/revalidação aplicável. Revogação persistente e 2FA permanecem evoluções separadas. Tokens em Web Storage continuam acessíveis ao JavaScript da origem: não foi demonstrada injeção XSS nesta inspeção. Cookies HttpOnly/BFF exigiriam mudança coordenada de autenticação/CSRF e não serão introduzidos sem decisão e testes.

BCrypt permanece no cadastro/login; novos cadastros têm limite de bytes e tamanho mínimo, preservando login legado. Não foi comprovado segredo real versionado nos arquivos de configuração. Hash de conta potencialmente real no SQL local é tratado separadamente em SEC-05.

### SQL, transações e estoque

JDBC observado usa placeholders; o texto dinâmico de criação de pedido constrói a quantidade de placeholders, sem concatenar valores de usuário. Procedures calculam preços/total no banco, usam transação/rollback e locks de estoque. Não foi comprovada SQL Injection ou venda acima do estoque nesta leitura.

O diff SQL atual começa após a linha 802 e não altera `sp_CriarPedido:565–681`. CSVs de estrutura não comprovam quais procedures/triggers/índices estão instalados no servidor. Testes unitários de locks não substituem concorrência real no SQL Server.

Desativar mesa com trabalho aberto e definir ocupação exigem regra explícita. Não há tabela de comanda/sessão/presença, nem entidade de clientes. Nenhuma nova tabela, coluna, índice ou migração será criada nesta rodada sem autorização.

### XSS, entradas e erros

A saída React é tratada como texto nas telas inspecionadas; não foi confirmada execução de script por nomes/descrições. Inputs/DTOs precisam de limites coerentes com colunas. Erros HTTP gerais não devem devolver stack traces/SQL ao cliente. Mensagens internas nos logs ainda exigem acesso restrito e revisão de dados sensíveis.

No baseline, o limite de corpo dependia de Content-Length e a limitação de frequência comparava a URI bruta. O harness isolado confirmou os dois desvios. As correções e evidências estão registradas abaixo em SEC-06/07; nenhum ataque de indisponibilidade foi executado contra serviço real.

### Pagamentos

Valores vêm do pedido persistido; cliente inicia registro pendente, equipe autorizada confirma/cancela. Lock e idempotência de confirmação existentes são preservados. Não há gateway, webhook, transferência ou estorno bancário. O usuário autorizou tornar explícito que esta entrega demonstra pagamento simulado; os registros de teste nunca devem ser confundidos com conciliação financeira real.

### Notificações, STOMP e infraestrutura

STOMP exige equipe autenticada para assinar tópico administrativo e bloqueia publicação indevida. Push de cliente exige capacidade do pedido; destinos/chaves são validados e envio tem limites, timeout e fila após commit. Inscrições antigas de equipe sem vínculo com usuário continuam exigindo revisão; não há revogação por funcionário persistida no schema atual. Fila em memória não oferece entrega garantida após falha/reinício.

CORS usa origens explícitas. HTTPS, cabeçalhos do host estático, validação do certificado SQL Server e privilégios da conta do banco dependem do ambiente. `trustServerCertificate=true` é um default local que não deve ser copiado como configuração recomendada de produção. Configurar DB_URL com criptografia e certificado verificável; não trocar a configuração do usuário sem validar o certificado disponível.

### Dependências e privacidade

`npm audit` executado em 14/10/2026 retornou zero alertas para a árvore resolvida. Isso não comprova ausência de vulnerabilidades. Backend usa Boot 3.3.4 com patch específico Security 6.3.10: migração para uma linha suportada e revisão de advisories por componente são pendências. O patch de BCrypt está fora da faixa afetada na [referência oficial Spring](https://spring.io/security/cve-2025-22228/). O Tomcat 10.1.30 herdado tem correções posteriores na [lista oficial Apache](https://tomcat.apache.org/security-10.html). Não foram demonstradas as condições para exploração dessas falhas no TableHub; não foi alegada RCE nem executado exploit. Atualização coordenada do framework permanece necessária antes do uso público. Um aviso de versão não é prova de explorabilidade do aplicativo.

Na interface anônima, evitar coleta de nome, telefone, e-mail ou associação entre consumidores. Logs/histórico precisam de finalidade, acesso e retenção definidos. Essa avaliação técnica não equivale a certificação de conformidade LGPD.

## Contrato proposto de QR sem mudar schema

1. Equipe autorizada obtém QR assinado em `GET /api/mesas/{id}/qr`.
2. Link contém número da mesa e QR assinado; o número sozinho serve somente para navegação pública.
3. `POST /api/mesas/contexto` valida QR, atividade e versão de `qr_code`, emitindo contexto curto da mesa.
4. Frontend guarda o contexto na aba, remove QR da navegação após troca e envia `X-Table-Token` apenas nas novas ações da mesa correspondente.
5. Backend verifica assinatura, propósito, expiração, mesa ativa e versão novamente. O token da mesa não lê pedidos anteriores; capacidades de pedidos continuam obrigatórias.

QR é público e pode ser fotografado/compartilhado. Não comprova presença física e precisa de limitação de abuso; esse modelo bloqueia a simples troca numérica, sem prometer controle de presença. Troca da chave de assinatura ou versão do QR invalida links anteriores. Não manter fallback numérico para ações, pois isso anularia a correção.

## Plano de execução

| Etapa | Escopo | Dependências / aprovação | Verificação |
| --- | --- | --- | --- |
| 0 | Snapshot, diagnóstico e baseline | Autorizado; concluído | Arquivos anteriores preservados; testes abaixo |
| 1 | Preço/busca, formulário confiável, pedidos anônimos e identificação de simulação | Autorização explícita recebida | Unitários + navegador com API sintética + build |
| 2 | DTOs de mesa, separação pública/equipe, QR/contexto, integração cliente | Autorização explícita recebida | HTTP de perfis/capacidades + testes de contrato + QR/pedido no navegador |
| 3 | Cadastro de mesas, pedidos associados, situação completa e atendimento consistente | Regras de ocupação/deduplicação autorizadas | Mais de oito mesas, retry/conflito, polling e responsividade |
| 4 | Retestes de segurança e consistência, relatórios e documentação | Sem banco de teste: simulação de persistência explicitada | Suites completas e diffs; SQL real permanece pendente |
| 5 | Revisão do diff e commits separados por projeto | Mostrar resultado ao usuário antes da publicação desta rodada | Sem segredos/arquivos locais; confirmar remotes e commits |
| 6 | Homologação SQL Server e hospedagem | Banco isolado/configuração; produção exige autorização específica | Roteiro integrado completo com evidências reais |

## Baseline executado nesta rodada

- Maven: 127 testes, zero falhas/erros/skips; compilação Java e contexto com persistência simulada.
- Node frontend: 16 testes, zero falhas. A primeira tentativa foi bloqueada por spawn EPERM; a repetição fora do isolamento executou os testes normalmente.
- Vite: build aprovado, 219 módulos, JavaScript 492,94 kB (gzip 153,50 kB).
- Navegador headless: seis cenários aprovados usando API interceptada, sem banco real.
- `npm audit --json`: zero alertas; relatório bruto local no snapshot.
- Não foram executados testes integrados no SQL Server, envio push real, scan físico do QR ou implantação externa. Não declarar persistência após reinício como validada sem esses testes.

## Referências oficiais

- [OWASP Top 10:2025 — Broken Access Control](https://top10.owasp.org/2025/A01_2025-Broken_Access_Control/): autorização no servidor, negação por padrão e restrição por recurso.
- [OWASP API Security Top 10](https://api-security.owasp.org/): API1 (autorização de objetos), API4 (consumo de recursos), API5 (funções administrativas), API6 (abuso de fluxo) e API8 (configuração).
- [OWASP REST Security](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html): HTTPS, validação JWT e entradas.
- [OWASP Session Management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html): limitações de Web Storage e proteção da sessão.

## Achados adicionais confirmados e correções

### SEC-06 — limite de login contornável por URL codificada

- Severidade: alta; OWASP API4/API6 e Top 10 A01/A02.
- Arquivo no baseline: `RequestRateLimitFilter.java:29–35`.
- Pré-condição: endpoint acessível com segmento percent-encoded que o Spring resolve para login.
- Evidência isolada: rota canônica, 21 requisições, 20 HTTP200 e 1 HTTP429; `/api/auth/%6cogin`, 25 requisições e 25 HTTP200. O serviço de autenticação foi simulado, sem tentativas de senha real.
- Impacto: contornar proteção de força bruta/consumo de BCrypt. Não demonstra obtenção de senha ou sessão.
- Correção: comparar servletPath já interpretado pelo container; fallback com parsing único de segmentos para mappings sem servletPath. Nunca decodificar duas vezes. Context path preservado.
- Reteste: URL codificada passou a 20 sucessos e 5 HTTP429; testes HTTP e unitários de orçamento compartilhado, context path e dupla codificação aprovados.

### SEC-07 — limite de tamanho não aplicado a corpo de comprimento desconhecido

- Severidade: média; OWASP API4.
- Arquivo no baseline: `RequestRateLimitFilter.java:25`.
- Pré-condição: corpo recebido sem Content-Length conhecido, como transferência chunked.
- Evidência isolada: 280.061 bytes com comprimento conhecido retornavam 413; mesmos bytes com comprimento desconhecido atravessavam o filtro.
- Impacto: limite anunciado não protegia o parser de JSON contra corpos grandes. Não foi causado DoS de processo real.
- Correção: leitura real limitada a 262.145 bytes para detectar overflow, rejeição 413 antes do parser e replay do corpo aceito; não alocar buffer sem limite.
- Reteste: os dois formatos são rejeitados com 413; JSON UTF-8 e corpo válido são preservados; leitura para no primeiro byte excedente. Testes HTTP/unitários aprovados.

### EST-04 — notificação de pagamento perdida por callback aninhado

- Severidade: média operacional, sem escalonamento de privilégio.
- Arquivos: `PaymentService.java:150` e `PushNotificationService` no dispatcher após commit.
- Causa: Payment registrava um callback; dentro dele o Push registrava outro callback na sincronização já em execução, fora do snapshot de callbacks do commit.
- Reprodução segura: TransactionSynchronizationManager/Utils com executor capturado, sem SQL, rede ou gateway; reproduz o callback tardio que não é executado.
- Correção: agendar push ainda dentro da transação, deixando seu dispatcher executar após commit; Socket mantém callback independente.
- Reteste: quatro testes cobrem perda anterior, envio somente após commit, nenhum envio em rollback e independência de falha Socket.

### SEC-08 — caminhos inexistentes esgotavam a capacidade global do limitador

- Severidade: média; OWASP API4.
- Arquivo: `RequestRateLimitFilter.java`, seleção de caminhos com prefixo `/api/push/`.
- Pré-condição: enviar muitos POSTs com caminhos diferentes sob esse prefixo; não exige endpoint válido nem credencial, pois o filtro precede a autorização.
- Evidência isolada: 10.005 caminhos distintos, sem rede/banco, preenchiam o limite global de chaves; o primeiro login legítimo seguinte recebia 429.
- Impacto: recusa temporária de clientes legítimos enquanto as entradas não expiravam. Não foi executado ataque contra serviço operacional.
- Correção: somente os dois endpoints de inscrição push existentes alocam chaves; caminhos inexistentes continuam negados pela autorização, sem consumir capacidade do limitador.
- Reteste: caso específico passa a permitir o login seguinte; orçamento por IP das rotas legítimas continua limitado e memória permanece limitada. A suíte final de 217 testes passou; detalhes no relatório de testes.

## Registro das etapas implementadas

| Etapa | Causa e solução | Arquivos principais | Resultado / limite |
| --- | --- | --- | --- |
| Segurança de requisições | Comparação de URI bruta e limite apenas por header; normalização e limite de bytes reais | RequestRateLimitFilter, RequestRateLimitFilterTest, RequestBoundaryHttpTest | Reproduções e retestes isolados aprovados; limites continuam por instância/IP |
| QR e contrato de mesas | Número público permitia novas ações; novos propósitos table-qr/table-access, DTOs e servidor gerando QR | MesaController, MesaService, TableAccessService, JwtService, DTOs, OrderController, AtendimentoController, SecurityConfig; hooks/services e telas do cliente no frontend | Tokens separados, mesa ativa/versão/expiração conferidas; QR físico e SQL real pendentes |
| Mesas e atendimento | Resumo parcial, pedido sempre nulo e repetição de linhas | MesaPanelService/Controller/DTO, DashboardService, AtendimentoService, Mesas e Atendimento React | Todas as mesas, regra aprovada, dedupe exata e polling controlado; locks reais ainda requerem SQL Server |
| Catálogo e histórico anônimo | Float exato, busca assimétrica, draft perdido e demos misturados | CatalogDialogs, Cardapio, Clientes, catalogForm, anonymousOrders | Preço 19,99, conflito de estoque preservando draft e busca retestados em navegador; sem entidade de cliente |
| Pagamento e push | Linguagem ambígua e dupla sincronização | PaymentService, PaymentNotificationTransactionTest, telas de pagamento, Pedidos, Dashboard e Financeiro | Simulação explícita, estado oficial preservado; banco ainda não distingue registros de demonstração |
| SQL e documentação | Material potencialmente real em seed e documentação incompleta | database/table_hub_db.sql, README de cada projeto, relatórios e guia | Somente dois literais alterados no SQL; demais linhas/exports preservados; nenhuma execução de banco |

Resultados completos e commits por etapa: [relatório de testes](RELATORIO_TESTES_FINALIZACAO.md). Relação completa dos arquivos: [manifesto](ARQUIVOS_FINALIZACAO.md).

## Pendências priorizadas após esta rodada

1. Alta: homologar procedures/triggers/locks, rollback e persistência em SQL Server isolado. Sem esse ambiente, não declarar aceite integrado real.
2. Alta: atualizar framework/servidor de aplicação coordenadamente, validar TLS do SQL Server, HTTPS/CORS/proxy e limites de infraestrutura antes de exposição pública.
3. Alta operacional: idempotência durável de criação de pedidos continua ausente; retry após resposta perdida pode criar outro pedido. Exige decisão sobre armazenamento durável sem improvisar mudança de schema.
4. Média: revogação de sessões/push por funcionário e revisão de inscrições antigas; cookies HttpOnly/BFF e 2FA são evoluções separadas.
5. Média: desativação de mesa com trabalho aberto ainda precisa de política explícita; modelo não registra reserva/presença/comanda. Não foi bloqueada unilateralmente uma ação administrativa existente.
6. Média: observação de preparo continua local; Financeiro oferece resumo, sem listagem detalhada; paginação/N+1 e fila push durável permanecem melhorias.
7. Baixa/média: leitura física de QR em telefone, acessibilidade completa, demais dispositivos e redução do bundle inicial. Vite informa chunk acima de 500 kB; build funciona e a divisão de rotas não foi introduzida no fim da entrega.

Nada nesta auditoria equivale a certificação de segurança, LGPD ou autorização de publicação em produção.
