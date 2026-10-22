/*
 * TableHub — observação operacional do pedido + criação com data UTC.
 *
 * IMPORTANTE:
 * 1. Faça backup e teste em cópia do Somee.
 * 2. Aplica DEPOIS de patch_clientes_pin.sql e patch_pedidos_cliente_pin.sql.
 * 3. Compara a procedure atualmente instalada antes de substituí-la.
 * 4. Este patch não altera pedidos antigos, estoque, pagamentos ou status.
 * 5. Registros antigos continuam sem fuso original. Nunca somar horas em massa.
 */
IF DB_NAME() IN (N'master', N'model', N'msdb', N'tempdb')
    THROW 50041, N'Selecione o banco TableHub antes de executar o patch.', 1;
IF OBJECT_ID(N'dbo.Pedidos', N'U') IS NULL
    THROW 50042, N'Tabela dbo.Pedidos não encontrada.', 1;
IF OBJECT_ID(N'dbo.sp_CriarPedido', N'P') IS NULL
    THROW 50043, N'Procedure sp_CriarPedido não encontrada.', 1;
IF COL_LENGTH(N'dbo.Pedidos', N'cliente_id') IS NULL
    THROW 50044, N'Aplique primeiro patch_pedidos_cliente_pin.sql.', 1;
IF NOT EXISTS (
    SELECT 1 FROM sys.parameters
    WHERE object_id = OBJECT_ID(N'dbo.sp_CriarPedido') AND name = N'@cliente_id'
)
    THROW 50045, N'A procedure atual não contém autenticação de clientes. Pare e revise a migração anterior.', 1;
GO

IF COL_LENGTH(N'dbo.Pedidos', N'observacoes') IS NULL
    ALTER TABLE dbo.Pedidos ADD observacoes NVARCHAR(255) NULL;
IF COL_LENGTH(N'dbo.Pedidos', N'criado_em_utc') IS NULL
    ALTER TABLE dbo.Pedidos ADD criado_em_utc DATETIME2(0) NULL
        CONSTRAINT DF_Pedidos_CriadoEmUtc DEFAULT SYSUTCDATETIME();
GO

-- Em pedidos novos, a coluna criado_em_utc recebe o valor UTC pelo DEFAULT.
-- Pedidos anteriores a esta migração permanecem com criado_em_utc = NULL.
-- No corpo abaixo, a transação continua protegendo a atualização do estoque.
CREATE OR ALTER PROCEDURE dbo.sp_CriarPedido
    @mesa_id      BIGINT,
    @itens        dbo.TipoItemPedido READONLY,
    @criado_por   BIGINT = NULL,
    @id_pedido    BIGINT OUTPUT,
    @cliente_id BIGINT = NULL,
    @nome_cliente NVARCHAR(40) = NULL,
    @observacoes NVARCHAR(255) = NULL
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

        /* Bloqueia as linhas dos produtos durante validação para evitar overselling. */
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
            RAISERROR(N'Status inicial Recebido não est� configurado.', 16, 1);
        END;

        /* Pedido nasce não confirmado apenas durante esta transação. */
        INSERT INTO dbo.Pedidos (
            mesa_id,
            status_id,
            valor_total,
            confirmado,
            alterado_por,
            cliente_id,
            nome_cliente,
            observacoes
        )
        VALUES (
            @mesa_id,
            @status_recebido,
            0,
            0,
            @criado_por,
            @cliente_id,
            @nome_cliente,
            NULLIF(LTRIM(RTRIM(@observacoes)), N'')
        );

        SET @id_pedido = SCOPE_IDENTITY();

        /* Snapshot do preço atual do produto no momento da compra. */
        INSERT INTO dbo.Itens_Pedido (pedido_id, produto_id, quantidade, preco_unitario)
        SELECT @id_pedido, p.id_produto, i.quantidade, p.preco
        FROM @itens i
        INNER JOIN dbo.Produtos p ON p.id_produto = i.produto_id;

        /* Baixa de estoque na mesma transação. */
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
