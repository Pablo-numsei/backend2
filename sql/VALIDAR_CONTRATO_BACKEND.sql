/*
TABLEHUB — VERIFICAR CONTRATO E OBJETOS DO BACKEND
Somente leitura. Complementa AUDITORIA_ESQUEMA_SOMEE.sql.
Uso: execute no banco indicado por DB_URL; confira AUSENTE antes de
considerar uma migração. Não executa alterações no Somee.

Compatibilidade: comparações entre catálogo do servidor e variáveis locais
usam COLLATE DATABASE_DEFAULT para evitar Msg 468 no Somee. Apenas leitura.

Atenção: objetos legados são criados pelo script-base e podem não ser
reconstruíveis a partir de um patch isolado sem avaliar dependências.
*/
SET NOCOUNT ON;

SELECT N'CONTRATO_01_BANCO' AS bloco, DB_NAME() AS banco,
       CONVERT(NVARCHAR(30), SYSUTCDATETIME(), 126) AS utc_servidor;

DECLARE @Obrigatorios TABLE (
    objeto NVARCHAR(160) NOT NULL,
    tipo CHAR(2) NOT NULL,
    modulo NVARCHAR(90) NOT NULL
);
INSERT INTO @Obrigatorios (objeto, tipo, modulo) VALUES
 (N'dbo.sp_CriarPedido',N'P',N'Pedidos / estoque'),
 (N'dbo.sp_AvancarStatusPedido',N'P',N'Pedidos / transições'),
 (N'dbo.TR_Produtos_SincronizaDisponibilidade',N'TR',N'Catálogo'),
 (N'dbo.TR_Produtos_SoftDelete',N'TR',N'Catálogo'),
 (N'dbo.TR_Itens_Pedido_ImutavelAposConfirmacao',N'TR',N'Pedidos'),
 (N'dbo.TR_Itens_Pedido_RecalculaTotal',N'TR',N'Pedidos'),
 (N'dbo.TR_Pedidos_ValidaEHistorizaStatus',N'TR',N'Pedidos'),
 (N'dbo.TR_Pedidos_SoftDelete',N'TR',N'Pedidos'),
 (N'dbo.vw_Produtos_Ativos',N'V',N'Catálogo'),
 (N'dbo.vw_Pedidos_Ativos',N'V',N'Pedidos');

SELECT N'CONTRATO_02_PROCEDURES_TRIGGERS_VIEWS' AS bloco,
       e.objeto, e.modulo,
       CASE WHEN o.object_id IS NULL THEN N'AUSENTE'
            WHEN o.type COLLATE DATABASE_DEFAULT <> e.tipo COLLATE DATABASE_DEFAULT THEN N'TIPO_DIVERGENTE'
            ELSE N'OK' END AS resultado
FROM @Obrigatorios e
LEFT JOIN sys.objects o ON o.object_id = OBJECT_ID(e.objeto)
ORDER BY resultado, e.objeto;

SELECT N'CONTRATO_03_TIPO_TABELA' AS bloco,
       N'dbo.TipoItemPedido' AS tipo_esperado,
       CASE WHEN EXISTS (
           SELECT 1 FROM sys.table_types tt
           JOIN sys.schemas s ON s.schema_id = tt.schema_id
           WHERE s.name = N'dbo' AND tt.name = N'TipoItemPedido'
       ) THEN N'OK' ELSE N'AUSENTE' END AS resultado;

SELECT N'CONTRATO_04_CONSTRAINTS' AS bloco,
       N'dbo.Clientes.nome_usuario' AS exigencia,
       CASE WHEN EXISTS (
           SELECT 1 FROM sys.indexes i
           JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
           JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
           WHERE i.object_id = OBJECT_ID(N'dbo.Clientes')
             AND i.is_unique = 1 AND ic.key_ordinal = 1 AND c.name = N'nome_usuario'
             AND NOT EXISTS (
                 SELECT 1 FROM sys.index_columns ic2
                 WHERE ic2.object_id=i.object_id AND ic2.index_id=i.index_id
                   AND ic2.key_ordinal > 1
             )
       ) THEN N'OK' ELSE N'AUSENTE_OU_NAO_UNICO' END AS resultado
UNION ALL
SELECT N'CONTRATO_04_CONSTRAINTS',
       N'dbo.Pedidos.criado_em_utc (DEFAULT UTC)',
       CASE WHEN EXISTS (
           SELECT 1 FROM sys.columns c
           JOIN sys.default_constraints dc ON dc.object_id = c.default_object_id
           WHERE c.object_id = OBJECT_ID(N'dbo.Pedidos')
             AND c.name=N'criado_em_utc'
             AND UPPER(dc.definition) LIKE N'%SYSUTCDATETIME%'
       ) THEN N'OK' ELSE N'AUSENTE_OU_DEFAULT_DIFERENTE' END;

SELECT N'CONTRATO_05_PROCEDURE_PEDIDO' AS bloco,
       e.nome_parametro,
       CASE WHEN p.parameter_id IS NULL THEN N'AUSENTE' ELSE N'OK' END AS resultado
FROM (VALUES
 (N'@mesa_id'), (N'@itens'), (N'@criado_por'),
 (N'@id_pedido'), (N'@cliente_id'),
 (N'@nome_cliente'), (N'@observacoes')
) e(nome_parametro)
LEFT JOIN sys.parameters p
 ON p.object_id = OBJECT_ID(N'dbo.sp_CriarPedido')
 AND p.name COLLATE DATABASE_DEFAULT = e.nome_parametro COLLATE DATABASE_DEFAULT
ORDER BY e.nome_parametro;

-- Valores base não sensíveis para a operação (apenas quando as tabelas existem).
IF OBJECT_ID(N'dbo.Perfis', N'U') IS NOT NULL
    EXEC(N'SELECT N''CONTRATO_06_PERFIS'' AS bloco, nome FROM dbo.Perfis ORDER BY nome');
ELSE
    SELECT N'CONTRATO_06_PERFIS' AS bloco, N'TABELA AUSENTE' AS situacao;

IF OBJECT_ID(N'dbo.Status_Pedido', N'U') IS NOT NULL
    EXEC(N'SELECT N''CONTRATO_07_STATUS'' AS bloco, nome, ordem_fluxo FROM dbo.Status_Pedido ORDER BY ordem_fluxo');
ELSE
    SELECT N'CONTRATO_07_STATUS' AS bloco, N'TABELA AUSENTE' AS situacao;

SELECT N'CONTRATO_08_OBSERVACAO' AS bloco,
       N'Essa checagem nao prova equivalencia das procedures: comparar definicao com a versao homologada antes de alterar.' AS mensagem;
