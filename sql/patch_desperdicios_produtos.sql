-- Modelo para BANCO NOVO; custo de desperdício pode ser desconhecido (NULL).
-- Para banco existente cuja coluna está NOT NULL, consultar auditoria e somente
-- se necessário executar patch_desperdicios_custo_opcional.sql depois de backup.
-- NAO execute se sua tabela ja existe; o script e apenas para novos bancos.
-- NUNCA derruba, altera ou apaga uma tabela existente.
SET XACT_ABORT ON;
GO
IF OBJECT_ID('dbo.Desperdicios_Produtos', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Desperdicios_Produtos (
        id_desperdicio BIGINT IDENTITY(1,1) NOT NULL,
        produto_id BIGINT NOT NULL,
        quantidade INT NOT NULL,
        motivo NVARCHAR(20) NOT NULL,
        observacao NVARCHAR(255) NULL,
        custo_unitario_informado DECIMAL(12,2) NULL,
        registrado_em DATETIME2(0) NOT NULL CONSTRAINT DF_Desperdicios_RegistradoEm DEFAULT SYSUTCDATETIME(),
        registrado_por BIGINT NOT NULL,
        estoque_antes INT NOT NULL,
        estoque_depois INT NOT NULL,
        CONSTRAINT PK_Desperdicios_Produtos PRIMARY KEY (id_desperdicio),
        CONSTRAINT FK_Desperdicios_Produto FOREIGN KEY (produto_id) REFERENCES dbo.Produtos(id_produto),
        CONSTRAINT FK_Desperdicios_Usuario FOREIGN KEY (registrado_por) REFERENCES dbo.Usuarios(id_usuario),
        CONSTRAINT CK_Desperdicios_Quantidade CHECK (quantidade > 0),
        CONSTRAINT CK_Desperdicios_Motivo CHECK (motivo IN (N'OUTRO', N'PREPARO', N'DANIFICADO', N'VENCIMENTO')),
        CONSTRAINT CK_Desperdicios_Custo CHECK (custo_unitario_informado >= 0),
        CONSTRAINT CK_Desperdicios_Estoque CHECK (estoque_antes >= quantidade AND estoque_depois = estoque_antes - quantidade)
    );
    CREATE INDEX IX_Desperdicios_Data ON dbo.Desperdicios_Produtos(registrado_em DESC, id_desperdicio DESC);
    CREATE INDEX IX_Desperdicios_Produto ON dbo.Desperdicios_Produtos(produto_id, registrado_em DESC);
END;
GO
