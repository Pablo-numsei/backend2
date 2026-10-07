USE TableHub;
GO

SET XACT_ABORT ON;
GO

IF OBJECT_ID('dbo.Atendimentos_Mesa', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Atendimentos_Mesa (
        id_atendimento BIGINT IDENTITY(1,1) NOT NULL,
        mesa_id BIGINT NOT NULL,
        pedido_id BIGINT NULL,
        tipo VARCHAR(20) NOT NULL,
        detalhe NVARCHAR(255) NULL,
        status VARCHAR(30) NOT NULL CONSTRAINT DF_Atendimentos_Mesa_Status DEFAULT ('ENVIADO'),
        criado_em DATETIME2 NOT NULL CONSTRAINT DF_Atendimentos_Mesa_CriadoEm DEFAULT SYSUTCDATETIME(),
        atualizado_em DATETIME2 NOT NULL CONSTRAINT DF_Atendimentos_Mesa_AtualizadoEm DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_Atendimentos_Mesa PRIMARY KEY (id_atendimento),
        CONSTRAINT FK_Atendimentos_Mesa_Mesas FOREIGN KEY (mesa_id) REFERENCES dbo.Mesas(id_mesa),
        CONSTRAINT FK_Atendimentos_Mesa_Pedidos FOREIGN KEY (pedido_id) REFERENCES dbo.Pedidos(id_pedido),
        CONSTRAINT CK_Atendimentos_Mesa_Tipo CHECK (tipo IN ('GARCOM', 'CONTA')),
        CONSTRAINT CK_Atendimentos_Mesa_Status CHECK (status IN ('ENVIADO', 'EM_ATENDIMENTO', 'CONCLUIDO'))
    );

    CREATE INDEX IX_Atendimentos_Mesa_Status_CriadoEm
        ON dbo.Atendimentos_Mesa(status, criado_em DESC);

    CREATE INDEX IX_Atendimentos_Mesa_Mesa
        ON dbo.Atendimentos_Mesa(mesa_id, criado_em DESC);
END;
GO

SELECT id_atendimento, mesa_id, pedido_id, tipo, detalhe, status, criado_em, atualizado_em
FROM dbo.Atendimentos_Mesa
ORDER BY criado_em DESC;
GO
