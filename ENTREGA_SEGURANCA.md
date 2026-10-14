# TableHub — correções prioritárias para a entrega

## Estado e recuperação

Os dois repositórios permanecem nas branches originais (`main`). A publicação no GitHub foi autorizada pelo usuário após as correções e usa os remotes próprios de cada projeto: `Pablo-numsei/backend2` e `Pablo-numsei/tablehub-front-v2`. Nenhuma alteração de schema foi aplicada.
O snapshot anterior às correções está em `.tablehub-recovery/20261013-before-security/`:

- `backend-source.tar.gz` e `frontend-source.tar.gz`: fontes atuais, incluindo arquivos não rastreados; excluem `.git`, dependências e builds.
- `baseline.json`: HEAD, branch e alterações que já existiam.
- Patches do working tree/index e checksums SHA-256 dos arquivos compactados.

As alterações SQL pré-existentes, a exclusão de `database/TableHub.sql`, o novo `database/table_hub_db.sql`, o lockfile e o arquivo workspace do frontend foram preservados. Não restaure arquivos usando `git reset --hard`. Para recuperar, compare os fontes com os snapshots e restaure somente os arquivos da etapa desejada; arquivos criados nesta entrega estão no manifesto separado.

A publicação inclui os scripts SQL pré-existentes em commit separado das correções de segurança e inclui o lockfile do frontend. Backups, credenciais, dependências, builds e arquivos pessoais de workspace permanecem locais. O verificador de recuperação aceita novos commits quando o HEAD original continua no histórico e compara os arquivos pré-existentes com os snapshots.

## Etapa 1 — autenticação, autorização e exposição de dados

- Cadastro de funcionários/perfis exige Administrador; bootstrap público removido.
- Acesso negado por padrão; permissões explícitas para Administrador, Garçom e Cozinha.
- Criação do pedido retorna um token assinado, separado dos tokens de funcionário. Leitura de pedido/itens/pagamento e inscrição push exigem a capacidade correspondente ou perfil autorizado.
- Autoria de pedido/status é obtida no servidor. Cozinha prepara; Garçom entrega; Administrador administra.
- DTOs de pedido/itens/pagamento excluem relações internas, QR e dados de funcionários.
- Login tem respostas uniformes; novos cadastros exigem 12 caracteres e limite de 72 bytes UTF-8. Logins legados curtos continuam permitidos.
- Spring Security atualizado para 6.3.10, corrigindo a CVE de BCrypt na linha compatível atual. Migração do framework para uma linha suportada permanece pendente.
- Proteção de abuso em memória por endereço remoto, com resposta 429. Em proxy, revisar identificação do cliente e complementar limites na infraestrutura.
- Frontend tem guards por perfil, sessão/expiração, preferência explícita de persistência, usuário real e logout.

## Etapa 2 — pagamentos, estoque e notificações

- Cliente solicita pagamento `PENDENTE`; seleção de método não efetua cobrança nem confirma transferência.
- Administrador/Garçom confirma recebimento externo manual no painel Pedidos, após conferir valor e transação. O registro financeiro usa lock do pedido e repetição idempotente.
- Cancelamento é baixa do registro manual, não estorno de provedor. Duplicidades financeiras legadas exigem conferência; não foram corrigidas automaticamente.
- Estoque usa PATCH com delta e quantidade esperada, lock e conflito 409. PUT de catálogo não restaura estoque antigo.
- WebSocket exige JWT de equipe, controla assinatura e bloqueia publicação nos tópicos. Push valida destinos/chaves, limita dispositivos/tempos e usa fila limitada após commit.
- Status de atendimento usa atualização condicional para evitar regressão concorrente.
- `open-in-view` desabilitado para evitar entidade antiga após SQL direto; SQL detalhado desabilitado por padrão.

## Etapa 3 — integração do cliente

- Catálogo, carrinho e confirmação usam IDs e preços do servidor; novos produtos aparecem com imagem alternativa.
- Mudança de preço antes do envio exige nova conferência do total.
- Pagamento/recibo consultam o estado oficial; recibo não aparece como confirmado para pedido pendente.
- Escritas no storage têm fallback em memória para evitar indicar fracasso após pedido já criado.
- Tokens de pedido não aparecem em URLs. O acompanhamento continua na aba que criou o pedido; nova janela sem token pede ajuda da equipe.
- O carrinho usa uma chave v2 para evitar confundir os antigos IDs locais com IDs reais do catálogo. Carrinhos antigos precisam ser refeitos.

## Validação executada

- Backend: 127 testes passaram, incluindo 21 cenários HTTP de autorização, contexto completo sem conexão de banco, JWT/capacidades, pagamentos, produtos, validação, WebSocket e push.
- Frontend: 16 testes passaram, incluindo sessão, contrato Axios/capacidade, catálogo/preços e falha do storage.
- Builds Java e Vite concluídos.
- Navegador Chromium headless: 6 cenários passaram com API sintética interceptada: gestão anônima, pedido, pagamento pendente/recibo bloqueado, confirmação manual/recibo, estoque e menu a 375 px.
- `git diff --check` sem erros.

Os testes de navegador não substituem integração com o SQL Server real. Nenhum teste alterou o banco do usuário.

## Conferência necessária antes de usar em produção

1. Confirmar existência de conta Administrador ativa; o cadastro público não pode criar o primeiro administrador.
2. Configurar DB_URL/DB_USER/DB_PASSWORD/JWT_SECRET e VAPID fora do Git, sem registrar valores em logs. O segredo JWT exige Base64 de bytes aleatórios adequados ao algoritmo.
3. Compilar frontend com VITE_API_URL correto e HTTPS; configurar `app.cors.allowed-origins` com as origens exatas e fallback para index.html nas rotas SPA.
4. Em homologação, conferir procedures/triggers/patches já existentes, lock concorrente, transições, faturamento, pagamento manual e estoque. Nenhuma migração foi aplicada nesta entrega.
5. Revalidar inscrições push anteriores à correção: o schema atual não registra o funcionário proprietário; revogação por usuário depende de evolução autorizada do modelo. Não presumir que cadastros antigos feitos pela API pública eram legítimos.
6. Conferir certificado/TLS do SQL Server, backup/restauração e relógios/fuso dos ambientes.

## Pendências fora deste corte

- 2FA, recuperação de senha e revogação persistente de sessões/dispositivos.
- Gateway eletrônico, webhook e estorno real.
- Persistência da observação de preparo: a UI avisa que o campo permanece no dispositivo e que restrições devem ser comunicadas à equipe.
- QR real, modelo de comanda/ocupação, clientes de demonstração e funcionalidades de gestão ainda incompletas.
- Paginação/N+1, acessibilidade completa, testes em outros dispositivos e migração para framework suportado.
- A fila push é em memória: reinício/saturação pode perder notificações; a consulta do estado continua sendo a fonte de verdade.

## Comandos

```powershell
# Backend, após clonar o repositório (JDK 21 e acesso para baixar dependências)
.\mvnw.cmd test
.\mvnw.cmd -DskipTests package

# Alternativa com Maven/cache locais preparados nesta sessão
& '.\.maven-home\wrapper\dists\apache-maven-3.9.9-bin\33b4b2b4\apache-maven-3.9.9\bin\mvn.cmd' -B --no-transfer-progress '-Dmaven.repo.local=D:\backend2\.m2-cache' test

# Verificação local: requer os snapshots ignorados, disponíveis nesta máquina
node scripts/verify-recovery.mjs

# Frontend, em D:\tablehub-front-v2
npm.cmd ci
npm.cmd test
npm.cmd run build
node tests/browserSmoke.mjs
```

O teste de navegador usa somente dist, perfil descartável em `.browser-tests` e API sintética; não use sua sessão de navegador real.
