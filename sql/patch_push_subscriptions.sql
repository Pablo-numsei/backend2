USE TableHub;
GO

SET XACT_ABORT ON;
GO

IF OBJECT_ID(N'dbo.Push_Subscriptions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Push_Subscriptions (
        id_push       BIGINT IDENTITY(1,1) NOT NULL,
        pedido_id     BIGINT NULL,
        canal         VARCHAR(20) NOT NULL
            CONSTRAINT DF_Push_Subscriptions_Canal DEFAULT ('PEDIDO'),
        endpoint      NVARCHAR(1500) NOT NULL,
        p256dh        NVARCHAR(255) NOT NULL,
        auth          NVARCHAR(255) NOT NULL,
        ativo         BIT NOT NULL
            CONSTRAINT DF_Push_Subscriptions_Ativo DEFAULT (1),
        criado_em     DATETIME2(0) NOT NULL
            CONSTRAINT DF_Push_Subscriptions_CriadoEm DEFAULT SYSUTCDATETIME(),
        alterado_em   DATETIME2(0) NULL,

        CONSTRAINT PK_Push_Subscriptions PRIMARY KEY (id_push),
        CONSTRAINT FK_Push_Subscriptions_Pedidos
            FOREIGN KEY (pedido_id)
            REFERENCES dbo.Pedidos(id_pedido)
            ON DELETE NO ACTION,
        CONSTRAINT CK_Push_Subscriptions_Canal
            CHECK (canal IN ('PEDIDO', 'EQUIPE')),
        CONSTRAINT CK_Push_Subscriptions_Vinculo
            CHECK (
                (canal = 'PEDIDO' AND pedido_id IS NOT NULL)
                OR
                (canal = 'EQUIPE' AND pedido_id IS NULL)
            )
    );
END;
GO

IF COL_LENGTH('dbo.Push_Subscriptions', 'canal') IS NULL
BEGIN
    ALTER TABLE dbo.Push_Subscriptions
        ADD canal VARCHAR(20) NOT NULL
            CONSTRAINT DF_Push_Subscriptions_Canal DEFAULT ('PEDIDO');
END;
GO

IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.Push_Subscriptions')
      AND name = N'pedido_id'
      AND is_nullable = 0
)
BEGIN
    ALTER TABLE dbo.Push_Subscriptions
        ALTER COLUMN pedido_id BIGINT NULL;
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = N'CK_Push_Subscriptions_Canal'
      AND parent_object_id = OBJECT_ID(N'dbo.Push_Subscriptions')
)
BEGIN
    ALTER TABLE dbo.Push_Subscriptions
        ADD CONSTRAINT CK_Push_Subscriptions_Canal
        CHECK (canal IN ('PEDIDO', 'EQUIPE'));
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = N'CK_Push_Subscriptions_Vinculo'
      AND parent_object_id = OBJECT_ID(N'dbo.Push_Subscriptions')
)
BEGIN
    ALTER TABLE dbo.Push_Subscriptions
        ADD CONSTRAINT CK_Push_Subscriptions_Vinculo
        CHECK (
            (canal = 'PEDIDO' AND pedido_id IS NOT NULL)
            OR
            (canal = 'EQUIPE' AND pedido_id IS NULL)
        );
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_Push_Subscriptions_Pedido'
      AND object_id = OBJECT_ID(N'dbo.Push_Subscriptions')
)
BEGIN
    CREATE INDEX IX_Push_Subscriptions_Pedido
        ON dbo.Push_Subscriptions(pedido_id, canal, ativo);
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_Push_Subscriptions_Canal'
      AND object_id = OBJECT_ID(N'dbo.Push_Subscriptions')
)
BEGIN
    CREATE INDEX IX_Push_Subscriptions_Canal
        ON dbo.Push_Subscriptions(canal, ativo);
END;
GO

SELECT
    id_push,
    pedido_id,
    canal,
    endpoint,
    ativo,
    criado_em,
    alterado_em
FROM dbo.Push_Subscriptions
ORDER BY id_push DESC;
GO
