-- Execute uma unica vez no SQL Server / Somee antes de usar a API de desperdicios.
-- Script idempotente: nao apaga nem sobrescreve dados existentes.
SET XACT_ABORT ON;
GO
IF OBJECT_ID('dbo.Desperdicios_Produtos', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Desperdicios_Produtos (
        id_desperdicio BIGINT IDENTITY(1,1) NOT NULL,
        produto_id BIGINT NOT NULL,
        produto_nome NVARCHAR(150) NOT NULL,
        quantidade INT NOT NULL,
        motivo VARCHAR(20) NOT NULL,
        observacao NVARCHAR(255) NULL,
        valor_unitario DECIMAL(10,2) NOT NULL,
        valor_total DECIMAL(18,2) NOT NULL,
        estoque_antes INT NOT NULL,
        estoque_depois INT NOT NULL,
        registrado_por NVARCHAR(255) NOT NULL,
        registrado_em DATETIME2(0) NOT NULL CONSTRAINT DF_Desperdicios_RegistradoEm DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_Desperdicios_Produtos PRIMARY KEY (id_desperdicio),
        CONSTRAINT FK_Desperdicios_Produtos_Produto FOREIGN KEY (produto_id) REFERENCES dbo.Produtos(id_produto),
        CONSTRAINT CK_Desperdicios_Quantidade CHECK (quantidade > 0),
        CONSTRAINT CK_Desperdicios_Motivo CHECK (motivo IN ('VENCIMENTO', 'PREPARO', 'DANO', 'OUTROS')),
        CONSTRAINT CK_Desperdicios_Valores CHECK (valor_unitario >= 0 AND valor_total >= 0),
        CONSTRAINT CK_Desperdicios_Estoque CHECK (estoque_antes >= quantidade AND estoque_depois = estoque_antes - quantidade)
    );
    CREATE INDEX IX_Desperdicios_Data ON dbo.Desperdicios_Produtos(registrado_em DESC, id_desperdicio DESC);
    CREATE INDEX IX_Desperdicios_Produto ON dbo.Desperdicios_Produtos(produto_id, registrado_em DESC);
END;
GO
