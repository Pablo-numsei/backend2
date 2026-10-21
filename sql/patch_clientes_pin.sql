/*
 * TableHub — Clientes com nome de usuário + PIN de quatro dígitos.
 * Antes: backup e conferência no banco de testes.
 * Não altera dbo.Usuarios (funcionários).
 * Contas legadas por e-mail/senha exigem migração assistida:
 * nunca converter senha antiga em PIN.
 */
IF DB_NAME() IN (N'master', N'model', N'msdb', N'tempdb')
    THROW 50031, N'Conecte-se ao banco TableHub antes de aplicar a migração.', 1;
GO

IF OBJECT_ID(N'dbo.Clientes', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Clientes (
        id_cliente BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Clientes PRIMARY KEY,
        nome_usuario NVARCHAR(40) COLLATE Latin1_General_100_CI_AI NOT NULL
           CONSTRAINT UQ_Clientes_NomeUsuario UNIQUE,
        email NVARCHAR(180) COLLATE Latin1_General_100_CI_AI NULL,
        senha_hash NVARCHAR(255) NULL,
        pin_hash NVARCHAR(255) NULL,
        criado_em DATETIME2(0) NOT NULL
           CONSTRAINT DF_Clientes_CriadoEm DEFAULT SYSUTCDATETIME()
    );
END
ELSE
BEGIN
    IF COL_LENGTH(N'dbo.Clientes', N'pin_hash') IS NULL
        ALTER TABLE dbo.Clientes ADD pin_hash NVARCHAR(255) NULL;
    IF EXISTS (SELECT 1 FROM sys.key_constraints
               WHERE name = N'UQ_Clientes_Email' AND parent_object_id = OBJECT_ID(N'dbo.Clientes'))
        ALTER TABLE dbo.Clientes DROP CONSTRAINT UQ_Clientes_Email;
    ALTER TABLE dbo.Clientes ALTER COLUMN email NVARCHAR(180) COLLATE Latin1_General_100_CI_AI NULL;
    ALTER TABLE dbo.Clientes ALTER COLUMN senha_hash NVARCHAR(255) NULL;
END;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.Clientes')
               AND name = N'UX_Clientes_Email_NaoNulo')
    CREATE UNIQUE INDEX UX_Clientes_Email_NaoNulo ON dbo.Clientes(email) WHERE email IS NOT NULL;
GO
