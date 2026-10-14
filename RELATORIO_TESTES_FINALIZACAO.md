# TableHub — testes da finalização

Data: 14/10/2026. Resultado final após as correções autorizadas.

| Verificação | Resultado | Ambiente / alcance |
| --- | --- | --- |
| Backend `mvn verify` | 217 testes, 0 falhas, 0 erros, 0 ignorados; build aprovado | JDK 21; repositórios/JDBC simulados; nenhum SQL Server |
| Frontend `npm test` | 29 testes aprovados | Node 24.21.0; testes puros e contrato Axios |
| Frontend `npm run build` | Aprovado | Vite 7.3.7; artefatos em dist |
| Navegador `node tests/browserSmoke.mjs` | 16 cenários aprovados | Edge/Chromium headless, API interceptada, perfil isolado |
| `npm audit --json` | 0 alertas na árvore resolvida | Não comprova ausência de outras falhas |
| Recuperação/preservação | Arquivos anteriores preservados, salvo os 2 literais SQL autorizados | Snapshot, checksums e prova de sanitização locais |

O pacote Java foi gerado em `target/backend-0.0.1-SNAPSHOT.jar` e não será versionado. O build Vite emite aviso de chunk maior que 500 kB; não é falha de compilação. Refatoração de carregamento/divisão de rotas permanece melhoria posterior.

## Backend

| Suíte | Casos |
| --- | ---: |
| Contexto de componentes sem conexão de banco | 1 |
| HTTP de painel de mesas | 5 |
| HTTP de limites de corpo/login | 4 |
| Filtro de frequência/tamanho/caminho | 19 |
| HTTP de segurança geral | 21 |
| WebSocket/STOMP | 7 |
| HTTP de QR/contexto/mesa | 19 |
| Push controller | 2 |
| Resposta pública de pedido | 2 |
| Validação de DTOs | 7 |
| Atendimento | 16 |
| Autenticação | 9 |
| Usuário atual | 9 |
| Dashboard | 1 |
| JWT | 16 |
| Agrupamento do painel de mesas | 3 |
| Serviço de mesas | 10 |
| Acesso por pedido | 7 |
| Notificação de pagamento e transações | 4 |
| Pagamento | 17 |
| Produto/estoque | 11 |
| Validação de endpoint push | 4 |
| Serviço push | 4 |
| Acesso por mesa | 19 |
| **Total** | **217** |

As suítes HTTP usam JWT assinado real com segredo sintético, Spring Security e MockMvc; a persistência é simulada. Elas verificam papéis e capacidades; não abrem listener público nem validam driver/locks reais do SQL Server.

O agrupamento de mesas usa ResultSet simulado. O caso de mais de oito mesas e entregue sem pagamento é coberto, mas o comportamento da consulta/procedure no servidor real precisa de homologação.

## Frontend

| Suíte | Casos |
| --- | ---: |
| Catálogo administrativo/preços/busca/histórico anônimo | 7 |
| Axios e headers específicos por pedido/mesa | 3 |
| Sessão, perfis e expiração | 7 |
| Catálogo/carrinho/recibo/storage | 7 |
| Contexto de mesa e URL do QR | 5 |
| **Total** | **29** |

## Navegador — cenários aprovados

1. Visitante em gestão é redirecionado ao login.
2. Link numérico ou sem mesa não cria associação implícita à Mesa 04 nem habilita pedido.
3. QR trocado por contexto permite chamado pré-pedido da mesa correspondente.
4. Menu → carrinho → pedido usa ID real de mesa/produto, preço oficial e X-Table-Token.
5. Cliente solicita simulação pendente; recibo confirmado permanece bloqueado.
6. Equipe confirma simulação e cliente consulta o registro persistido na fixture.
7. Ajuste de estoque usa delta e quantidade esperada.
8. Nona mesa exibe pedido entregue ainda sem pagamento como ocupada.
9. Administrador cadastra mesa usando somente o número.
10. Gestão obtém e apresenta QR assinado com margem branca.
11. Histórico mostra pedidos anônimos persistidos, sem clientes fictícios.
12. Equipe avança atendimento até Concluído.
13. Cadastro de categoria/produto aceita preço válido com vírgula.
14. Edição preserva preço digitado após 409 de estoque, atualiza referência e aceita retry; busca com acento funciona.
15. Menu a 375 px não cria rolagem horizontal.
16. Modal QR cabe em tela 375×560 com rolagem interna, sem overflow horizontal.

O servidor de fixtures não é o backend Spring; não usar estes cenários como prova de persistência real, TLS, scanner ou entrega de push. Assinaturas criptográficas são verificadas nas suítes HTTP/backend, não pela fixture do navegador.

## Falhas intermediárias e correções

- Primeira tentativa Node no sandbox falhou em spawn EPERM, antes de executar testes; repetição autorizada fora do isolamento passou.
- Primeira suíte ampliada de backend: 214 casos, duas falhas. O stream sintético retornava EOF numa leitura de comprimento zero; corrigido para o contrato InputStream. Um mock de identidade retornava 0L; fixture corrigida e o serviço reforçado para exigir ID positivo, com dois casos adicionais. Suíte final completa: 216/216.
- O teste extra de busca esperava uma linha para consulta que também correspondia à categoria dos dois produtos. Corrigida a consulta da fixture para um termo único com acento; os 16 cenários passaram. A lógica de busca da aplicação não foi relaxada.
- A revisão final acrescentou SEC-08: 10.005 rotas push inexistentes esgotavam chaves e bloqueavam um login legítimo. O caso foi executado antes da correção e falhou com 429; após limitar a alocação às rotas existentes, a suíte completa passou com 217 testes. Nenhuma requisição real de ataque foi enviada.

## Evidências locais e reprodução

Relatórios JUnit XML em `target/surefire-reports`. Evidências adicionais em `.tablehub-recovery/20261014-finalizacao-925f78b6/`: baseline/snapshots/checksums, `security-boundary-evidence.json`, `security-boundary-after.json`, `npm-audit-initial.json`, `maven-finalizacao-verify.log`, `sql-sanitization-proof.json` e `final-validation.json`. São arquivos locais ignorados; não devem ser publicados porque snapshots podem conter material sensível anterior à sanitização.

```powershell
# Backend: JDK 21, no diretório do backend
.\mvnw.cmd verify

# Frontend: Node compatível, no diretório do frontend
npm.cmd ci
npm.cmd test
npm.cmd run build
node tests/browserSmoke.mjs
```

O teste de navegador requer Edge/Chrome no Windows, cria perfil descartável e intercepta `http://localhost:8080/api/*`; não o adapte para enviar fixtures ao banco operacional.

`git diff --check` passou nos arquivos de código/documentação desta rodada. O SQL mantém o aviso de linha em branco final que já existia: o usuário autorizou apenas duas substituições de dados, portanto esse restante foi preservado.

## Não validado

- SQL Server real: procedures, triggers, constraints efetivamente instaladas, isolamento, deadlocks, rollback, disputa de estoque e persistência após reinício.
- Fluxo integrado completo com um backend real conectado ao banco.
- Leitura física de QR em telefone e domínio externo.
- Push real com VAPID/provedor/permissão do navegador.
- TLS/certificados, firewall, conta do banco, proxy, hospedagem e custos.
- Pen test externo, carga sustentada, acessibilidade completa e todos os dispositivos.

Sem banco de teste, esses itens permanecem pendentes; não houve teste de escrita no banco do usuário nem publicação em produção.

## Commits por etapa

| Repositório | Commit | Conteúdo |
| --- | --- | --- |
| Backend | `85072c9` | Limite de corpo, normalização de caminho e prevenção de esgotamento por rotas inexistentes |
| Backend | `d2ba9a9` | QR/contexto, DTOs e cadastro de mesa, painel completo e deduplicação de atendimento |
| Backend | `fb150f9` | Notificação de pagamento após commit, sem callback aninhado perdido |
| Backend | `b801332` | Arquivos de banco preexistentes e duas substituições fictícias autorizadas |
| Frontend | `fbb5a64` | Preços, busca, histórico anônimo e identificação de simulação |
| Frontend | `f1f6d9f` | Contexto de QR, mesas/cliente/atendimento e testes de navegador |
| Frontend | `82a7e06` | README do frontend |

O commit de documentação do backend inclui este relatório, auditoria, manifesto e guia. Os arquivos exatos de cada etapa podem ser revisados por `git show --stat <commit>`; nenhum commit contém snapshots, credenciais reais, dependências ou builds.
