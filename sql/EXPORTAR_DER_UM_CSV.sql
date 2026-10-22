/*
TableHub — exportacao simples de metadados do DER para UM unico CSV.
SOMENTE LEITURA; nao cria nem altera tabelas, colunas ou dados.
Execute conectado ao banco Table_hub_db do Somee.
Exportar a UNICA tabela de resultados (07_CONTAGENS, 08_COLUNAS e 09_FKS)
em CSV e enviar o arquivo para revisao do DER e das migracoes.
Nenhum nome de usuario, email, senha, token ou pedido e consultado.
*/
SET NOCOUNT ON;

;WITH Inventario AS (
    SELECT
        N'08_COLUNAS' AS bloco,
        CONVERT(NVARCHAR(128), t.name) COLLATE DATABASE_DEFAULT AS tabela,
        CONVERT(NVARCHAR(128), c.name) COLLATE DATABASE_DEFAULT AS coluna,
        CONVERT(NVARCHAR(128), ty.name) COLLATE DATABASE_DEFAULT AS tipo,
        CONCAT(N'max_length=', c.max_length,
               N'; precisao=', c.precision,
               N'; escala=', c.scale,
               N'; permite_null=', CASE WHEN c.is_nullable=1 THEN N'SIM' ELSE N'NAO' END)
            COLLATE DATABASE_DEFAULT AS detalhes,
        CASE WHEN EXISTS (
            SELECT 1
            FROM sys.indexes i
            INNER JOIN sys.index_columns ic
              ON ic.object_id = i.object_id AND ic.index_id = i.index_id
            WHERE i.object_id = c.object_id AND i.is_primary_key=1
              AND ic.column_id = c.column_id AND ic.key_ordinal > 0
        ) THEN N'PK' ELSE N'-' END COLLATE DATABASE_DEFAULT AS chave,
        COALESCE(CONVERT(NVARCHAR(MAX), dc.definition), N'')
            COLLATE DATABASE_DEFAULT AS referencia_ou_default
    FROM sys.tables t
    INNER JOIN sys.columns c ON c.object_id=t.object_id
    INNER JOIN sys.types ty ON ty.user_type_id=c.user_type_id
    LEFT JOIN sys.default_constraints dc ON dc.object_id=c.default_object_id
    WHERE t.schema_id=SCHEMA_ID(N'dbo')

    UNION ALL

    SELECT
        N'09_FKS',
        CONVERT(NVARCHAR(128), pt.name) COLLATE DATABASE_DEFAULT,
        CONVERT(NVARCHAR(128), pc.name) COLLATE DATABASE_DEFAULT,
        CONVERT(NVARCHAR(128), N'FOREIGN KEY') COLLATE DATABASE_DEFAULT,
        CONCAT(N'nome=', fk.name,
               N'; desativada=', fk.is_disabled,
               N'; nao_validada=', fk.is_not_trusted)
             COLLATE DATABASE_DEFAULT,
        N'FK' COLLATE DATABASE_DEFAULT,
        CONCAT(CONVERT(NVARCHAR(128), rt.name) COLLATE DATABASE_DEFAULT,
               N'.',
               CONVERT(NVARCHAR(128), rc.name) COLLATE DATABASE_DEFAULT)
             COLLATE DATABASE_DEFAULT
    FROM sys.foreign_key_columns fkc
    INNER JOIN sys.foreign_keys fk ON fk.object_id=fkc.constraint_object_id
    INNER JOIN sys.tables pt ON pt.object_id=fkc.parent_object_id
    INNER JOIN sys.tables rt ON rt.object_id=fkc.referenced_object_id
    INNER JOIN sys.columns pc ON pc.object_id=fkc.parent_object_id
         AND pc.column_id=fkc.parent_column_id
    INNER JOIN sys.columns rc ON rc.object_id=fkc.referenced_object_id
         AND rc.column_id=fkc.referenced_column_id
    WHERE pt.schema_id=SCHEMA_ID(N'dbo')
      AND rt.schema_id=SCHEMA_ID(N'dbo')

    UNION ALL

    SELECT
        N'07_CONTAGENS',
        N'dbo' COLLATE DATABASE_DEFAULT,
        N'TABELAS' COLLATE DATABASE_DEFAULT,
        N'COUNT' COLLATE DATABASE_DEFAULT,
        CONVERT(NVARCHAR(30), COUNT(*)) COLLATE DATABASE_DEFAULT,
        N'-' COLLATE DATABASE_DEFAULT,
        N'' COLLATE DATABASE_DEFAULT
    FROM sys.tables
    WHERE schema_id=SCHEMA_ID(N'dbo')

    UNION ALL

    SELECT
        N'07_CONTAGENS',
        N'dbo' COLLATE DATABASE_DEFAULT,
        N'COLUNAS' COLLATE DATABASE_DEFAULT,
        N'COUNT' COLLATE DATABASE_DEFAULT,
        CONVERT(NVARCHAR(30), COUNT(*)) COLLATE DATABASE_DEFAULT,
        N'-' COLLATE DATABASE_DEFAULT,
        N'' COLLATE DATABASE_DEFAULT
    FROM sys.columns c
    INNER JOIN sys.tables t ON t.object_id=c.object_id
    WHERE t.schema_id=SCHEMA_ID(N'dbo')

    UNION ALL

    SELECT
        N'07_CONTAGENS',
        N'dbo' COLLATE DATABASE_DEFAULT,
        N'FK_CONSTRAINTS' COLLATE DATABASE_DEFAULT,
        N'COUNT' COLLATE DATABASE_DEFAULT,
        CONVERT(NVARCHAR(30), COUNT(*)) COLLATE DATABASE_DEFAULT,
        N'-' COLLATE DATABASE_DEFAULT,
        N'' COLLATE DATABASE_DEFAULT
    FROM sys.foreign_keys
    WHERE schema_id=SCHEMA_ID(N'dbo')
)
SELECT bloco, tabela, coluna, tipo, detalhes, chave, referencia_ou_default
FROM Inventario
ORDER BY bloco, tabela, coluna, chave;
