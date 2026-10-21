/*
 * TableHub - vínculo opcional do pedido com cliente autenticado.
 * NÃO APLICAR SEM BACKUP, AMBIENTE DE TESTES E COMPARAÇÃO COM A PROCEDURE EXISTENTE.
 * Preserva a lógica de baixa de estoque e preço do script original.
 * Antes, aplicar sql/patch_clientes_pin.sql.
 */
IF OBJECT_ID(N'dbo.Clientes', N'U') IS NULL OR COL_LENGTH('dbo.Clientes','pin_hash') IS NULL
    THROW 50035, N'Aplique primeiro patch_clientes_pin.sql.', 1;
IF OBJECT_ID(N'dbo.Pedidos', N'U') IS NULL
    THROW 50036, N'Tabela dbo.Pedidos não encontrada.', 1;
GO

IF COL_LENGTH(N'dbo.Pedidos', N'cliente_id') IS NULL
    ALTER TABLE dbo.Pedidos ADD cliente_id BIGINT NULL;
IF COL_LENGTH(N'dbo.Pedidos', N'nome_cliente') IS NULL
    ALTER TABLE dbo.Pedidos ADD nome_cliente NVARCHAR(40) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name=N'FK_Pedidos_Cliente')
    ALTER TABLE dbo.Pedidos WITH CHECK ADD CONSTRAINT FK_Pedidos_Cliente
        FOREIGN KEY (cliente_id) REFERENCES dbo.Clientes(id_cliente);
GO

-- Snapshot do nome e FK são gravados NA MESMA TRANSAÇÃO do pedido.
-- Compare esta procedure com a versão real no Somee antes de aplicar.
CREATE OR ALTER PROCEDURE dbo.sp_CriarPedido
    @mesa_id      BIGINT,
    @itens        dbo.TipoItemPedido READONLY,
    @criado_por   BIGINT = NULL,
    @id_pedido    BIGINT OUTPUT,
    @cliente_id BIGINT = NULL,
    @nome_cliente NVARCHAR(40) = NULL
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
            cliente_id,
            nome_cliente
        )
        VALUES (
            @mesa_id,
            @status_recebido,
            0,
            0,
            @criado_por,
            @cliente_id,
            @nome_cliente
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
