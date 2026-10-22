# TableHub — SQL Server: auditoria, instalação e migrações (outubro/2026)

Este diretório contém os **scripts versionados** necessários para os módulos SQL atualmente identificados no backend Spring Boot. **Fazer `git pull` não executa nenhum SQL no Somee.**

> **Escopo**: a branch foi produzida analisando a versão `feat/customer-pin-vitrine`, as migrações já versionadas e os testes de pedidos realizados no Somee. **Não foi obtido um dump atualizado do banco**: é impossível afirmar que não existem divergências adicionais sem executar as auditorias na instância real.

## 1. Banco existente no Somee (`Table_hub_db`)

**Não execute `database/table_hub_db.sql` no Somee existente.** Esse arquivo é a estrutura-base antiga para um banco novo, contém `CREATE DATABASE`, `ALTER AUTHORIZATION` e operações não idempotentes; ele não reflete sozinho as migrações posteriores.

1. Faça **backup recente** e valide que está conectado ao banco certo (`SELECT DB_NAME()`).
2. Execute **somente consultas de leitura**:
   - `sql/AUDITORIA_ESQUEMA_SOMEE.sql`: blocos 01–09 de tabelas, colunas, FKs e defaults.
   - `sql/VALIDAR_CONTRATO_BACKEND.sql`: procedures, triggers, views, tipo-tabela e perfis/status operacionais.
3. Compare os resultados com os códigos e com `docs/DER_TABLEHUB_PREVISTO_POS_MIGRACOES.md`. Não compartilhe PINs, tokens, senhas, hashes ou segredos de configuração.
4. **Aplique um patch opcional somente se a auditoria comprovar que ele é necessário**, primeiro em uma cópia de testes:
   - `sql/patch_desperdicios_custo_opcional.sql`: quando `Desperdicios_Produtos.custo_unitario_informado` consta `NULL_DIVERGENTE` (custo deve admitir ausência).
   - `sql/patch_desperdicios_integridade_fks.sql`: quando há FK **ausente** de desperdício para Produtos/Usuarios. Aborta diante de referências órfãs, sem apagá-las.
5. Repita a auditoria e os testes depois de qualquer correção.

**As migrações `patch_clientes_pin.sql`, `patch_pedidos_cliente_pin.sql` e `patch_pedidos_observacoes_utc.sql` já foram aplicadas** na sessão validada do TableHub. **Não as execute novamente somente para atualizar o DER.**

A tabela de desperdícios **já estava presente e funcional** nas capturas fornecidas; a versão em `patch_desperdicios_produtos.sql` é apenas para **novos bancos**. Ela agora permite custo unitário desconhecido (`NULL`), de acordo com o backend.

## 2. Instalação em banco de testes **novo e vazio** (não executar automaticamente)

Pré-requisito: acesso administrativo SQL Server, banco vazio e revisão da estrutura-base. O script `database/table_hub_db.sql` não é um instalador seguro para repetir em base em operação e seu uso no Somee depende das permissões do provedor.

Execute separadamente, conferindo resultados e dependências:

1. `database/table_hub_db.sql` — entidades-base, tipos, procedures e regras de integridade.
2. `sql/patch_clientes_pin.sql` — cria `Clientes` com nome de usuário + hash do PIN. **Não execute `patch_cadastro_clientes.sql` antes; é uma migração legada substituída pelo fluxo PIN.**
3. `sql/patch_pedidos_cliente_pin.sql` — vínculo opcional de `Pedidos` com cliente.
4. `sql/patch_pedidos_observacoes_utc.sql` — observações e horário UTC dos **novos** pedidos.
5. `sql/patch_atendimentos_mesa.sql` — chamados de garçom/fechamento.
6. `sql/patch_push_subscriptions.sql` — notificações de pedidos e equipe.
7. `sql/patch_desperdicios_produtos.sql` — tabela de desperdício com custos opcionais, somente se ainda não existir.
8. `sql/seed_cardapio_tablehub.sql` — **opcional**, apenas se os produtos/categorias da demonstração forem desejados.
9. `sql/AUDITORIA_ESQUEMA_SOMEE.sql` e `sql/VALIDAR_CONTRATO_BACKEND.sql` — confira a instalação.

Se `database/table_hub_db.sql` precisar de ajustes para ambiente novo, prepare uma versão revisada em outra tarefa. **Não cole todos os arquivos em uma mesma execução sem revisar `GO`, `CREATE DATABASE` e precondições.**

## 3. Inventário de SQL versionado

| Script | Finalidade | Banco Somee existente |
|---|---|---|
| `AUDITORIA_ESQUEMA_SOMEE.sql` | Inventário físico e comparação com DER | **Executar** (somente leitura) |
| `VALIDAR_CONTRATO_BACKEND.sql` | Conferir objetos e regras esperadas | **Executar** (somente leitura) |
| `patch_clientes_pin.sql` | Clientes com nome + PIN | Já aplicado; **não repetir** |
| `patch_pedidos_cliente_pin.sql` | FK e identificação do cliente no pedido | Já aplicado; **não repetir** |
| `patch_pedidos_observacoes_utc.sql` | Observações e timestamp UTC | Já aplicado; **não repetir** |
| `patch_atendimentos_mesa.sql` | Registro de chamados por mesa | Executar só se auditoria comprovar ausência |
| `patch_push_subscriptions.sql` | Inscrições de notificações | Executar só se auditoria comprovar ausência |
| `patch_desperdicios_produtos.sql` | Criação do desperdício para novo banco | **Não executar** se tabela existe |
| `patch_desperdicios_custo_opcional.sql` | Custo opcional preservando precisão | **Opcional** se `NULL_DIVERGENTE` |
| `patch_desperdicios_integridade_fks.sql` | FKs de produto e responsável | **Opcional** se ausentes |
| `patch_cadastro_clientes.sql` | Cadastro legado e-mail/senha | **Obsoleto** para PIN; não aplicar |
| `seed_cardapio_tablehub.sql` | Dados de exemplo | Opcional, nunca em produção sem revisar |
| `TEST_CHAVES(FK).sql` | Consultas/testes de integridade | Revisar antes de rodar |

## 4. Alterações SQL que **não foram necessárias**

- A habilitação dos filtros PIX/Crédito/Débito foi uma alteração de frontend e de endpoint de backend que lê `Pagamentos`; **não exige nova tabela nem migração**.
- A exibição de nomes fictícios é somente visual no frontend: **não grava clientes de exemplo no banco**.
- A autenticação por PIN mantém `Clientes` separada de `Usuarios` da equipe.
- O timestamp `Pedidos.criado_em` antigo não possui fuso confiável; ele **não será corrigido em massa**. `criado_em_utc` é usado nos novos pedidos.

## 5. O que ainda depende de verificação real

A auditoria ainda deve confirmar:
- Quantidade real de tabelas, colunas e FKs.
- Integridade e nulabilidade da tabela de desperdício.
- Nomes reais de índices únicos, chaves e demais restrições.
- Presença e definição das procedures e triggers, especialmente `sp_CriarPedido`.
- Dados operacionais de `Perfis`, `Status_Pedido` e `Transicoes_Status_Pedido`.

Para entregar um DER definitivo, exporte os blocos `08_EXPORTAR_COLUNAS` e `09_EXPORTAR_FKS` da auditoria. Sem esses dados, o DER da pasta docs permanece explicitamente **previsto**, não certificado.

## 6. Como atualizar o código da nova branch

```powershell
cd D:\\backend2
git status
git fetch origin
git switch chore/sql-auditoria-esquema-tablehub
git pull --ff-only origin chore/sql-auditoria-esquema-tablehub
.\\mvnw.cmd test
```

Se `git switch` avisar sobre alterações locais, preserve-as primeiro (`git diff`, `git stash push` para os arquivos específicos), em vez de descartá-las.

**Não há necessidade de executar patches no banco simplesmente porque a branch foi baixada.**