/*
 * TableHub — identificação tardia de pedidos e notificações WhatsApp.
 * REVISAR e fazer BACKUP antes de aplicar no SQL Server Somee.
 * Execute a migração ANTES de subir o backend desta feature.
 * Não altera mesas, preços, estoque ou rotas administrativas.
 */
IF DB_NAME() IN (N'master', N'model', N'msdb', N'tempdb')
    THROW 50020, N'Conecte-se ao banco TableHub antes de aplicar esta migração.', 1;
IF OBJECT_ID(N'dbo.Pedidos', N'U') IS NULL OR OBJECT_ID(N'dbo.sp_CriarPedido', N'P') IS NULL
    THROW 50021, N'Estrutura de pedidos ou procedure sp_CriarPedido ausente.', 1;
GO

-- Pedidos antigos continuam válidos; nome é obrigatório SOMENTE para novos pedidos anônimos.
IF COL_LENGTH(N'dbo.Pedidos', N'nome_cliente') IS NULL
    ALTER TABLE dbo.Pedidos ADD nome_cliente NVARCHAR(80) NULL;
IF COL_LENGTH(N'dbo.Pedidos', N'whatsapp_numero') IS NULL
    ALTER TABLE dbo.Pedidos ADD whatsapp_numero VARCHAR(16) NULL;
IF COL_LENGTH(N'dbo.Pedidos', N'whatsapp_consentimento') IS NULL
    ALTER TABLE dbo.Pedidos ADD whatsapp_consentimento BIT NOT NULL
        CONSTRAINT DF_Pedidos_WhatsAppConsentimento DEFAULT (0);
IF COL_LENGTH(N'dbo.Pedidos', N'whatsapp_consentido_em') IS NULL
    ALTER TABLE dbo.Pedidos ADD whatsapp_consentido_em DATETIME2(0) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N'CK_Pedidos_WhatsAppConsentimento')
BEGIN
    ALTER TABLE dbo.Pedidos WITH CHECK ADD CONSTRAINT CK_Pedidos_WhatsAppConsentimento
        CHECK (whatsapp_consentimento = 0
               OR (whatsapp_numero IS NOT NULL AND whatsapp_consentido_em IS NOT NULL));
END;
GO

-- Fila persistente: reintenta se a Meta estiver temporariamente indisponível.
IF OBJECT_ID(N'dbo.WhatsApp_Outbox', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.WhatsApp_Outbox (
        id_notificacao BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_WhatsApp_Outbox PRIMARY KEY,
        pedido_id BIGINT NOT NULL,
        status_pedido NVARCHAR(30) NOT NULL,
        situacao VARCHAR(16) NOT NULL CONSTRAINT DF_WhatsApp_Situacao DEFAULT 'PENDENTE',
        tentativas INT NOT NULL CONSTRAINT DF_WhatsApp_Tentativas DEFAULT 0,
        proxima_tentativa DATETIME2(0) NOT NULL CONSTRAINT DF_WhatsApp_Proxima DEFAULT SYSUTCDATETIME(),
        reservado_ate DATETIME2(0) NULL,
        criado_em DATETIME2(0) NOT NULL CONSTRAINT DF_WhatsApp_Criado DEFAULT SYSUTCDATETIME(),
        aceito_em DATETIME2(0) NULL,
        CONSTRAINT FK_WhatsApp_Outbox_Pedidos FOREIGN KEY (pedido_id) REFERENCES dbo.Pedidos(id_pedido),
        CONSTRAINT UQ_WhatsApp_Outbox_PedidoStatus UNIQUE (pedido_id, status_pedido),
        CONSTRAINT CK_WhatsApp_Outbox_Situacao CHECK (situacao IN ('PENDENTE','PROCESSANDO','ACEITO','FALHA'))
    );
END;
GO

-- Preserve the stored procedure's original transactional stock/pricing logic,
-- adding only optional customer fields for backwards compatibility with staff tools.
CREATE OR ALTER PROCEDURE dbo.sp_CriarPedido
    @mesa_id      BIGINT,
    @itens        dbo.TipoItemPedido READONLY,
    @criado_por   BIGINT = NULL,
    @id_pedido    BIGINT OUTPUT,
    @nome_cliente NVARCHAR(80) = NULL,
    @telefone_cliente VARCHAR(16) = NULL,
    @whatsapp_consentimento BIT = 0
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    BEGIN TRY
        BEGIN TRANSACTION;

        IF NOT EXISTS (SELECT 1 FROM @itens)
        BEGIN
            RAISERROR(N'O pedido precisa possuir pelo menos um item.', 16, 1);
        END;

        IF NOT EXISTS (
            SELECT 1
            FROM dbo.Mesas WITH (UPDLOCK, HOLDLOCK)
            WHERE id_mesa = @mesa_id
              AND ativa = 1
        )
        BEGIN
            RAISERROR(N'Mesa inexistente ou inativa.', 16, 1);
        END;

        /* Bloqueia as linhas dos produtos durante valida��o para evitar overselling. */
        IF EXISTS (
            SELECT 1
            FROM @itens i
            LEFT JOIN dbo.Produtos p WITH (UPDLOCK, HOLDLOCK)
                   ON p.id_produto = i.produto_id
            WHERE p.id_produto IS NULL
               OR p.ativo = 0
               OR p.disponivel = 0
               OR p.estoque < i.quantidade
        )
        BEGIN
            RAISERROR(N'Existe produto inexistente, indispon�vel ou sem estoque suficiente.', 16, 1);
        END;

        DECLARE @status_recebido BIGINT;
        SELECT @status_recebido = id_status
        FROM dbo.Status_Pedido
        WHERE nome = N'Recebido';

        IF @status_recebido IS NULL
        BEGIN
            RAISERROR(N'Status inicial Recebido n�o est� configurado.', 16, 1);
        END;

        /* Pedido nasce n�o confirmado apenas durante esta transa��o. */
        INSERT INTO dbo.Pedidos (
            mesa_id,
            status_id,
            valor_total,
            confirmado,
            alterado_por,
            nome_cliente,
            whatsapp_numero,
            whatsapp_consentimento,
            whatsapp_consentido_em
        )
        VALUES (
            @mesa_id,
            @status_recebido,
            0,
            0,
            @criado_por,
            NULLIF(LTRIM(RTRIM(@nome_cliente)), N''),
            CASE WHEN @whatsapp_consentimento = 1 THEN @telefone_cliente ELSE NULL END,
            @whatsapp_consentimento,
            CASE WHEN @whatsapp_consentimento = 1 THEN SYSUTCDATETIME() ELSE NULL END
        );

        SET @id_pedido = SCOPE_IDENTITY();

        /* Snapshot do pre�o atual do produto no momento da compra. */
        INSERT INTO dbo.Itens_Pedido (pedido_id, produto_id, quantidade, preco_unitario)
        SELECT @id_pedido, p.id_produto, i.quantidade, p.preco
        FROM @itens i
        INNER JOIN dbo.Produtos p ON p.id_produto = i.produto_id;

        /* Baixa de estoque na mesma transa��o. */
        UPDATE p
           SET p.estoque = p.estoque - i.quantidade,
               p.alterado_por = COALESCE(@criado_por, p.alterado_por),
               p.alterado_em = GETDATE()
        FROM dbo.Produtos p
        INNER JOIN @itens i ON i.produto_id = p.id_produto;

        /* Confirma e congela os itens. */
        UPDATE dbo.Pedidos
           SET valor_total = (
                   SELECT CONVERT(DECIMAL(10,2), SUM(ip.subtotal))
                   FROM dbo.Itens_Pedido ip
                   WHERE ip.pedido_id = @id_pedido
               ),
               confirmado = 1,
               confirmado_em = GETDATE(),
               alterado_em = GETDATE(),
               alterado_por = @criado_por
        WHERE id_pedido = @id_pedido;

        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE() <> 0
            ROLLBACK TRANSACTION;

        DECLARE @ErrorMessage NVARCHAR(4000);
        DECLARE @ErrorSeverity INT;
        DECLARE @ErrorState INT;

        SELECT
            @ErrorMessage = ERROR_MESSAGE(),
            @ErrorSeverity = ERROR_SEVERITY(),
            @ErrorState = ERROR_STATE();

        RAISERROR(@ErrorMessage, @ErrorSeverity, @ErrorState);
        RETURN;
    END CATCH;
END;
GO
