/*
TABLEHUB — AUDITORIA SOMEE / SQL SERVER (somente leitura)
Data da análise: outubro/2026.
NÃO executa ALTER/CREATE/UPDATE/DELETE e NÃO modifica pedidos, clientes ou estoque.
Use no banco usado pelo DB_URL do backend. Compartilhe os resultados,
mas nunca compartilhe credenciais, senha, PIN, hash ou tokens.

Contexto: DER antigo continha 13 tabelas, 88 colunas e 17 FKs.
Modelo esperado pós-patches: 15 tabelas, aprox. 108 colunas e até 20 FKs.
Isso é uma expectativa de projeto, NÃO uma medição do Somee de hoje.
O esquema efetivo de Desperdicios_Produtos e seus FKs precisa de confirmação.
*/
SET NOCOUNT ON;

-- 1) CONFIRME O BANCO E FUSO SEM MUDANÇAS DE DADOS
SELECT N'01_AMBIENTE' AS bloco, DB_NAME() AS banco_atual,
       GETDATE() AS horario_servidor,
       SYSUTCDATETIME() AS horario_utc,
       SYSDATETIMEOFFSET() AS horario_servidor_com_fuso;

-- 2) TABELAS: OK = encontrada; AUSENTE = não encontrada
DECLARE @TabelasEsperadas TABLE (tabela sysname PRIMARY KEY);
INSERT INTO @TabelasEsperadas (tabela) VALUES
    (N'Atendimentos_Mesa'),
    (N'Categorias'),
    (N'Historico_Status_Pedido'),
    (N'Itens_Pedido'),
    (N'Mesas'),
    (N'Pagamentos'),
    (N'Pedidos'),
    (N'Perfis'),
    (N'Produtos'),
    (N'Push_Subscriptions'),
    (N'Status_Pedido'),
    (N'Transicoes_Status_Pedido'),
    (N'Usuarios'),
    (N'Clientes'),
    (N'Desperdicios_Produtos');

SELECT N'02_TABELAS' AS bloco, e.tabela,
       CASE WHEN t.object_id IS NULL THEN N'AUSENTE' ELSE N'OK' END AS resultado
FROM @TabelasEsperadas e
LEFT JOIN sys.tables t ON t.name=e.tabela AND t.schema_id=SCHEMA_ID(N'dbo')
ORDER BY resultado,e.tabela;

-- 3) COLUNAS NOVAS E DESPERDÍCIO: confere tipo e obrigatoriedade esperados.
-- OBS: custo_unitario_informado deve aceitar NULL no funcionamento atual;
-- o patch antigo de criação para banco novo contém divergência nesse ponto.
DECLARE @ColunasEsperadas TABLE (
 tabela sysname, coluna sysname, tipo sysname, aceita_null bit,
 PRIMARY KEY(tabela,coluna)
);
INSERT INTO @ColunasEsperadas (tabela,coluna,tipo,aceita_null) VALUES
    (N'Clientes',N'id_cliente',N'bigint',0),
    (N'Clientes',N'nome_usuario',N'nvarchar',0),
    (N'Clientes',N'email',N'nvarchar',1),
    (N'Clientes',N'senha_hash',N'nvarchar',1),
    (N'Clientes',N'pin_hash',N'nvarchar',1),
    (N'Clientes',N'criado_em',N'datetime2',0),
    (N'Pedidos',N'cliente_id',N'bigint',1),
    (N'Pedidos',N'nome_cliente',N'nvarchar',1),
    (N'Pedidos',N'observacoes',N'nvarchar',1),
    (N'Pedidos',N'criado_em_utc',N'datetime2',1),
    (N'Desperdicios_Produtos',N'id_desperdicio',N'bigint',0),
    (N'Desperdicios_Produtos',N'produto_id',N'bigint',0),
    (N'Desperdicios_Produtos',N'quantidade',N'int',0),
    (N'Desperdicios_Produtos',N'motivo',N'nvarchar',0),
    (N'Desperdicios_Produtos',N'observacao',N'nvarchar',1),
    (N'Desperdicios_Produtos',N'custo_unitario_informado',N'decimal',1),
    (N'Desperdicios_Produtos',N'registrado_em',N'datetime2',0),
    (N'Desperdicios_Produtos',N'registrado_por',N'bigint',0),
    (N'Desperdicios_Produtos',N'estoque_antes',N'int',0),
    (N'Desperdicios_Produtos',N'estoque_depois',N'int',0),
    (N'Push_Subscriptions',N'canal',N'varchar',0);

SELECT N'03_COLUNAS_CRITICAS' AS bloco, e.tabela, e.coluna,
       e.tipo AS tipo_esperado, ty.name AS tipo_atual,
       CASE WHEN e.aceita_null=1 THEN 'SIM' ELSE 'NAO' END AS null_esperado,
       CASE WHEN c.column_id IS NULL THEN 'AUSENTE' WHEN c.is_nullable=1 THEN 'SIM' ELSE 'NAO' END AS null_atual,
       CASE WHEN c.column_id IS NULL THEN 'AUSENTE'
            WHEN ty.name<>e.tipo THEN 'TIPO_DIVERGENTE'
            WHEN c.is_nullable<>e.aceita_null THEN 'NULL_DIVERGENTE'
            ELSE 'OK' END AS resultado
FROM @ColunasEsperadas e
LEFT JOIN sys.tables t ON t.name=e.tabela AND t.schema_id=SCHEMA_ID(N'dbo')
LEFT JOIN sys.columns c ON c.object_id=t.object_id AND c.name=e.coluna
LEFT JOIN sys.types ty ON ty.user_type_id=c.user_type_id
ORDER BY resultado,e.tabela,e.coluna;

-- 4) FKs esperadas. Relações de desperdício são previsões do script para bancos novos.
DECLARE @FKsEsperadas TABLE (
 tabela_origem sysname, coluna_origem sysname,
 tabela_destino sysname, coluna_destino sysname,
 PRIMARY KEY(tabela_origem,coluna_origem,tabela_destino,coluna_destino)
);
INSERT INTO @FKsEsperadas (tabela_origem,coluna_origem,tabela_destino,coluna_destino) VALUES
    (N'Atendimentos_Mesa',N'mesa_id',N'Mesas',N'id_mesa'),
    (N'Atendimentos_Mesa',N'pedido_id',N'Pedidos',N'id_pedido'),
    (N'Historico_Status_Pedido',N'alterado_por',N'Usuarios',N'id_usuario'),
    (N'Historico_Status_Pedido',N'pedido_id',N'Pedidos',N'id_pedido'),
    (N'Historico_Status_Pedido',N'status_id',N'Status_Pedido',N'id_status'),
    (N'Itens_Pedido',N'pedido_id',N'Pedidos',N'id_pedido'),
    (N'Itens_Pedido',N'produto_id',N'Produtos',N'id_produto'),
    (N'Pagamentos',N'pedido_id',N'Pedidos',N'id_pedido'),
    (N'Pedidos',N'alterado_por',N'Usuarios',N'id_usuario'),
    (N'Pedidos',N'mesa_id',N'Mesas',N'id_mesa'),
    (N'Pedidos',N'status_id',N'Status_Pedido',N'id_status'),
    (N'Produtos',N'alterado_por',N'Usuarios',N'id_usuario'),
    (N'Produtos',N'categoria_id',N'Categorias',N'id_categoria'),
    (N'Push_Subscriptions',N'pedido_id',N'Pedidos',N'id_pedido'),
    (N'Transicoes_Status_Pedido',N'status_destino_id',N'Status_Pedido',N'id_status'),
    (N'Transicoes_Status_Pedido',N'status_origem_id',N'Status_Pedido',N'id_status'),
    (N'Usuarios',N'perfil_id',N'Perfis',N'id_perfil'),
    (N'Pedidos',N'cliente_id',N'Clientes',N'id_cliente'),
    (N'Desperdicios_Produtos',N'produto_id',N'Produtos',N'id_produto'),
    (N'Desperdicios_Produtos',N'registrado_por',N'Usuarios',N'id_usuario');

;WITH FKsReais AS (
 SELECT pt.name AS tabela_origem, pc.name AS coluna_origem,
        rt.name AS tabela_destino, rc.name AS coluna_destino,
        fk.name AS constraint_nome, fk.is_disabled, fk.is_not_trusted
 FROM sys.foreign_key_columns fkc
 JOIN sys.foreign_keys fk ON fk.object_id=fkc.constraint_object_id
 JOIN sys.tables pt ON pt.object_id=fkc.parent_object_id
 JOIN sys.tables rt ON rt.object_id=fkc.referenced_object_id
 JOIN sys.columns pc ON pc.object_id=fkc.parent_object_id AND pc.column_id=fkc.parent_column_id
 JOIN sys.columns rc ON rc.object_id=fkc.referenced_object_id AND rc.column_id=fkc.referenced_column_id
 WHERE pt.schema_id=SCHEMA_ID(N'dbo') AND rt.schema_id=SCHEMA_ID(N'dbo')
)
SELECT N'04_CHAVES_ESTRANGEIRAS' AS bloco, e.tabela_origem, e.coluna_origem,
       e.tabela_destino, e.coluna_destino, r.constraint_nome,
       CASE WHEN r.constraint_nome IS NULL THEN 'AUSENTE'
            WHEN r.is_disabled=1 THEN 'DESATIVADA'
            WHEN r.is_not_trusted=1 THEN 'NAO_CONFIAVEL'
            ELSE 'OK' END AS resultado
FROM @FKsEsperadas e
LEFT JOIN FKsReais r ON r.tabela_origem=e.tabela_origem
 AND r.coluna_origem=e.coluna_origem AND r.tabela_destino=e.tabela_destino
 AND r.coluna_destino=e.coluna_destino
ORDER BY resultado,e.tabela_origem,e.coluna_origem;

-- 5) Confira procedure, parâmetros e default UTC.
SELECT N'05_PROCEDURE' AS bloco, p.name AS parametro, ty.name AS tipo,
       p.max_length AS tamanho_bytes
FROM sys.parameters p
JOIN sys.types ty ON ty.user_type_id=p.user_type_id
WHERE p.object_id=OBJECT_ID(N'dbo.sp_CriarPedido')
ORDER BY p.parameter_id;

SELECT N'06_DEFAULT_UTC' AS bloco, t.name AS tabela, c.name AS coluna,
       dc.definition AS expressao_default
FROM sys.tables t
JOIN sys.columns c ON c.object_id=t.object_id
LEFT JOIN sys.default_constraints dc ON dc.object_id=c.default_object_id
WHERE t.name=N'Pedidos' AND t.schema_id=SCHEMA_ID(N'dbo')
  AND c.name IN (N'criado_em',N'criado_em_utc');

-- 6) QUANTIDADES (metadados). Não mostra valores nem dados de clientes.
SELECT N'07_CONTAGENS' AS bloco,
       (SELECT COUNT(*) FROM sys.tables WHERE schema_id=SCHEMA_ID(N'dbo')) AS tabelas_dbo,
       (SELECT COUNT(*) FROM sys.columns c JOIN sys.tables t ON t.object_id=c.object_id WHERE t.schema_id=SCHEMA_ID(N'dbo')) AS colunas_dbo,
       (SELECT COUNT(*) FROM sys.foreign_keys WHERE schema_id=SCHEMA_ID(N'dbo')) AS chaves_estrangeiras_dbo;

-- 7) Para exportação de DER verificável: salve os 2 resultados em CSV.
SELECT N'08_EXPORTAR_COLUNAS' AS bloco, t.name AS Tabela, c.name AS Coluna,
       ty.name AS Tipo, c.max_length AS MaxLength, c.precision AS Precisao,
       c.scale AS Escala, c.is_nullable AS PermiteNull,
       CASE WHEN pk.column_id IS NOT NULL THEN 1 ELSE 0 END AS PK,
       dc.definition AS DefaultSQL
FROM sys.tables t
JOIN sys.columns c ON c.object_id=t.object_id
JOIN sys.types ty ON ty.user_type_id=c.user_type_id
OUTER APPLY (
  SELECT TOP (1) ic.column_id
  FROM sys.indexes ix
  JOIN sys.index_columns ic ON ic.object_id=ix.object_id AND ic.index_id=ix.index_id
  WHERE ix.object_id=t.object_id AND ix.is_primary_key=1 AND ic.column_id=c.column_id
) pk
LEFT JOIN sys.default_constraints dc ON dc.object_id=c.default_object_id
WHERE t.schema_id=SCHEMA_ID(N'dbo')
ORDER BY t.name,c.column_id;

SELECT N'09_EXPORTAR_FKS' AS bloco, pt.name AS TabelaOrigem,
       pc.name AS ColunaOrigem, rt.name AS TabelaDestino,
       rc.name AS ColunaDestino, fk.name AS NomeConstraint,
       fk.is_disabled AS Desativada, fk.is_not_trusted AS NaoValidada
FROM sys.foreign_key_columns fkc
JOIN sys.foreign_keys fk ON fk.object_id=fkc.constraint_object_id
JOIN sys.tables pt ON pt.object_id=fkc.parent_object_id
JOIN sys.tables rt ON rt.object_id=fkc.referenced_object_id
JOIN sys.columns pc ON pc.object_id=fkc.parent_object_id AND pc.column_id=fkc.parent_column_id
JOIN sys.columns rc ON rc.object_id=fkc.referenced_object_id AND rc.column_id=fkc.referenced_column_id
WHERE pt.schema_id=SCHEMA_ID(N'dbo') AND rt.schema_id=SCHEMA_ID(N'dbo')
ORDER BY pt.name,pc.name;