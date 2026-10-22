/*
TableHub — ajuste OPCIONAL da coluna de custo de desperdício.
APLIQUE SOMENTE se a auditoria 03_COLUNAS_CRITICAS indicar NULL_DIVERGENTE
para Desperdicios_Produtos.custo_unitario_informado, após backup recente
e teste em cópia do banco.

Efeito: permite NULL onde o tipo DECIMAL/NUMERIC antes exigia NOT NULL.
Não altera os valores gravados nem subtrai estoque. Não cria tabela.
É idempotente. Não é necessário no Somee quando a coluna já aceita NULL.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

IF DB_NAME() IN (N'master', N'model', N'msdb', N'tempdb')
    THROW 50101, N'Selecione o banco do TableHub antes de executar.', 1;
IF OBJECT_ID(N'dbo.Desperdicios_Produtos', N'U') IS NULL
    THROW 50102, N'Tabela Desperdicios_Produtos ausente; confira a auditoria antes de migrar.', 1;
IF COL_LENGTH(N'dbo.Desperdicios_Produtos', N'custo_unitario_informado') IS NULL
    THROW 50103, N'Coluna de custo ausente; nao e seguro altera-la automaticamente.', 1;

DECLARE @tipo SYSNAME, @precisao TINYINT, @escala TINYINT, @nullable BIT;
SELECT @tipo = ty.name,
       @precisao = c.precision, @escala = c.scale,
       @nullable = c.is_nullable
FROM sys.columns c
JOIN sys.types ty ON ty.user_type_id = c.user_type_id
WHERE c.object_id = OBJECT_ID(N'dbo.Desperdicios_Produtos')
  AND c.name = N'custo_unitario_informado';

IF @tipo NOT IN (N'decimal', N'numeric') OR @precisao IS NULL
    THROW 50104, N'Tipo do custo inesperado. Pare e compare a tabela com o backend.', 1;

IF @nullable = 0
BEGIN
    -- Conserva precisão/escala existentes: não converte nem arredonda valores.
    DECLARE @ddl NVARCHAR(MAX) = N'ALTER TABLE dbo.Desperdicios_Produtos '
        + N'ALTER COLUMN custo_unitario_informado ' + @tipo
        + N'(' + CONVERT(NVARCHAR(3), @precisao) + N',' + CONVERT(NVARCHAR(3), @escala)
        + N') NULL;';
    EXEC sys.sp_executesql @ddl;
END;

SELECT N'Desperdicios_Produtos' AS tabela, c.name AS coluna,
       ty.name AS tipo, c.precision AS precisao, c.scale AS escala,
       CASE WHEN c.is_nullable = 1 THEN N'SIM' ELSE N'NAO' END AS permite_null
FROM sys.columns c
JOIN sys.types ty ON ty.user_type_id = c.user_type_id
WHERE c.object_id = OBJECT_ID(N'dbo.Desperdicios_Produtos')
  AND c.name = N'custo_unitario_informado';
