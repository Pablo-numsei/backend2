/*
TableHub — integridade REFERENCIAL OPCIONAL para desperdícios (SQL Server).
Execute somente se o resultado 04_CHAVES_ESTRANGEIRAS da auditoria mostrar
AUSENTE para produto_id ou registrado_por, com backup e teste prévio.
Nunca exclui registros órfãos; aborta caso encontrem-se órfãos.
Executar em banco de produção somente em janela de manutenção.
Reexecutável: detecta FKs equivalentes mesmo quando possuem nomes distintos.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;

IF DB_NAME() IN (N'master', N'model', N'msdb', N'tempdb')
    THROW 50110, N'Selecione o banco do TableHub antes de executar.', 1;
IF OBJECT_ID(N'dbo.Desperdicios_Produtos', N'U') IS NULL
    THROW 50111, N'Tabela de desperdicios ausente. Pare e revise.', 1;
IF OBJECT_ID(N'dbo.Produtos', N'U') IS NULL OR OBJECT_ID(N'dbo.Usuarios', N'U') IS NULL
    THROW 50112, N'Tabelas Produtos/Usuarios ausentes. Pare e revise.', 1;
IF COL_LENGTH(N'dbo.Desperdicios_Produtos', N'produto_id') IS NULL
   OR COL_LENGTH(N'dbo.Desperdicios_Produtos', N'registrado_por') IS NULL
    THROW 50113, N'Colunas de desperdicio ausentes. Pare e revise.', 1;

IF EXISTS (
    SELECT 1
    FROM dbo.Desperdicios_Produtos d
    LEFT JOIN dbo.Produtos p ON p.id_produto = d.produto_id
    WHERE d.produto_id IS NOT NULL AND p.id_produto IS NULL
)
    THROW 50114, N'Ha desperdicios com produto inexistente. Corrija manualmente antes de criar FK.', 1;

IF EXISTS (
    SELECT 1
    FROM dbo.Desperdicios_Produtos d
    LEFT JOIN dbo.Usuarios u ON u.id_usuario = d.registrado_por
    WHERE d.registrado_por IS NOT NULL AND u.id_usuario IS NULL
)
    THROW 50115, N'Ha desperdicios com usuario inexistente. Corrija manualmente antes de criar FK.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.foreign_key_columns fkc
        JOIN sys.foreign_keys fk ON fk.object_id = fkc.constraint_object_id
        JOIN sys.columns c ON c.object_id = fkc.parent_object_id
          AND c.column_id = fkc.parent_column_id
        JOIN sys.columns rc ON rc.object_id = fkc.referenced_object_id
          AND rc.column_id = fkc.referenced_column_id
        WHERE fkc.parent_object_id = OBJECT_ID(N'dbo.Desperdicios_Produtos')
          AND fkc.referenced_object_id = OBJECT_ID(N'dbo.Produtos')
          AND c.name = N'produto_id' AND rc.name = N'id_produto'
    )
        ALTER TABLE dbo.Desperdicios_Produtos WITH CHECK
            ADD CONSTRAINT FK_Desperdicios_Produto
            FOREIGN KEY (produto_id) REFERENCES dbo.Produtos(id_produto);

    IF NOT EXISTS (
        SELECT 1
        FROM sys.foreign_key_columns fkc
        JOIN sys.foreign_keys fk ON fk.object_id = fkc.constraint_object_id
        JOIN sys.columns c ON c.object_id = fkc.parent_object_id
          AND c.column_id = fkc.parent_column_id
        JOIN sys.columns rc ON rc.object_id = fkc.referenced_object_id
          AND rc.column_id = fkc.referenced_column_id
        WHERE fkc.parent_object_id = OBJECT_ID(N'dbo.Desperdicios_Produtos')
          AND fkc.referenced_object_id = OBJECT_ID(N'dbo.Usuarios')
          AND c.name = N'registrado_por' AND rc.name = N'id_usuario'
    )
        ALTER TABLE dbo.Desperdicios_Produtos WITH CHECK
            ADD CONSTRAINT FK_Desperdicios_Usuario
            FOREIGN KEY (registrado_por) REFERENCES dbo.Usuarios(id_usuario);

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

SELECT N'Desperdicios_Produtos' AS tabela,
       fk.name AS chave_estrangeira, fk.is_disabled AS desativada,
       fk.is_not_trusted AS nao_validada
FROM sys.foreign_keys fk
WHERE fk.parent_object_id = OBJECT_ID(N'dbo.Desperdicios_Produtos')
ORDER BY fk.name;
