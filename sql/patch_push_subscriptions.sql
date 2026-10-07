USE TableHub;
GO

SET XACT_ABORT ON;
GO

IF OBJECT_ID(N'dbo.Push_Subscriptions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Push_Subscriptions (
        id_push       BIGINT IDENTITY(1,1) NOT NULL,
        pedido_id     BIGINT NOT NULL,
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
            ON DELETE NO ACTION
    );

    CREATE INDEX IX_Push_Subscriptions_Pedido
        ON dbo.Push_Subscriptions(pedido_id, ativo);
END;
GO

SELECT
    id_push,
    pedido_id,
    endpoint,
    ativo,
    criado_em,
    alterado_em
FROM dbo.Push_Subscriptions
ORDER BY id_push DESC;
GO
