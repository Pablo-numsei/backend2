/*
 * TableHub - cadastro opcional de clientes
 * Execute este patch UMA VEZ no mesmo banco usado pela variável DB_URL,
 * antes de habilitar POST /api/clientes/cadastro.
 *
 * Não altera dbo.Usuarios nem os perfis administrativos.
 */
IF DB_NAME() IN (N'master', N'model', N'msdb', N'tempdb')
BEGIN
    RAISERROR(N'Conecte-se ao banco de dados do TableHub antes de executar este patch.', 16, 1);
    RETURN;
END;

IF OBJECT_ID(N'dbo.Clientes', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Clientes (
        id_cliente BIGINT IDENTITY(1,1) NOT NULL
            CONSTRAINT PK_Clientes PRIMARY KEY,
        nome_usuario NVARCHAR(40) COLLATE Latin1_General_100_CI_AI NOT NULL,
        email NVARCHAR(180) COLLATE Latin1_General_100_CI_AI NOT NULL,
        senha_hash NVARCHAR(255) NOT NULL,
        criado_em DATETIME2(0) NOT NULL
            CONSTRAINT DF_Clientes_CriadoEm DEFAULT SYSUTCDATETIME(),
        CONSTRAINT UQ_Clientes_NomeUsuario UNIQUE (nome_usuario),
        CONSTRAINT UQ_Clientes_Email UNIQUE (email)
    );
END;
GO
