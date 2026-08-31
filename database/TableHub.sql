USE master;
GO

IF DB_ID(N'TableHub') IS NULL
BEGIN
    CREATE DATABASE TableHub;
END;
GO

ALTER AUTHORIZATION ON DATABASE::TableHub TO sa;
GO

SELECT name
FROM sys.databases
WHERE name = 'TableHub';
GO

USE TableHub;
GO

SELECT DB_NAME() AS BancoAtual;




/*
    TABLE HUB - SQL SERVER
    ESTRUTURA DO BANCO (versão compatível sem THROW)

    IMPORTANTE:
    1) Execute primeiro o arquivo 01_Criar_Banco_TableHub.sql.
    2) Depois execute este arquivo.
    3) Esta versão usa RAISERROR em vez de THROW para funcionar em
       ambientes/targets SQL Server mais antigos e evitar os erros
       de IntelliSense mostrados no editor.
*/

/* Abra esta consulta já conectado ao banco TableHub.
   Esta verificação evita criar objetos acidentalmente em master. */
IF DB_NAME() <> N'TableHub'
BEGIN
    RAISERROR(N'Conecte esta consulta ao banco TableHub antes de executar o script.', 16, 1);
    RETURN;
END;
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* ============================================================
   RN010 - Perfis são entidades próprias, não texto livre.
   ============================================================ */
CREATE TABLE dbo.Perfis (
    id_perfil      BIGINT IDENTITY(1,1) NOT NULL,
    nome           NVARCHAR(30) NOT NULL,
    descricao      NVARCHAR(200) NULL,
    criado_em      DATETIME2(0) NOT NULL CONSTRAINT DF_Perfis_CriadoEm DEFAULT GETDATE(), -- RN010

    CONSTRAINT PK_Perfis PRIMARY KEY (id_perfil), -- RN010
    CONSTRAINT UQ_Perfis_Nome UNIQUE (nome) -- RN010
);
GO

/* RN010 - Perfis oficiais do sistema. */
INSERT INTO dbo.Perfis (nome, descricao)
VALUES
    (N'Administrador', N'Gerencia usuários, produtos, categorias e configurações.'),
    (N'Cozinha',       N'Visualiza e atualiza o fluxo de preparo dos pedidos.'),
    (N'Garçom',        N'Acompanha pedidos, mesas e entregas.');
GO

/* ============================================================
   RN012 - Usuários guardam senha somente como hash.
   RN010 - perfil_id referencia a tabela Perfis.
   Entidade principal: criado_em e alterado_em.
   ============================================================ */
CREATE TABLE dbo.Usuarios (
    id_usuario      BIGINT IDENTITY(1,1) NOT NULL,
    perfil_id       BIGINT NOT NULL,
    nome            NVARCHAR(120) NOT NULL,
    email           NVARCHAR(180) NOT NULL,
    senha_hash      NVARCHAR(255) NOT NULL,
    ativo           BIT NOT NULL CONSTRAINT DF_Usuarios_Ativo DEFAULT (1), -- RN010/RN012
    criado_em       DATETIME2(0) NOT NULL CONSTRAINT DF_Usuarios_CriadoEm DEFAULT GETDATE(), -- RN010/RN012
    alterado_em     DATETIME2(0) NULL,

    CONSTRAINT PK_Usuarios PRIMARY KEY (id_usuario), -- RN010/RN012
    CONSTRAINT UQ_Usuarios_Email UNIQUE (email), -- RN010/RN012
    CONSTRAINT FK_Usuarios_Perfis FOREIGN KEY (perfil_id) -- RN010/RN012
        REFERENCES dbo.Perfis(id_perfil)
        ON DELETE NO ACTION
);
GO

/* ON DELETE NO ACTION em Perfis: impede apagar um perfil ainda usado por usuário (RN010). */
CREATE INDEX IX_Usuarios_Perfil ON dbo.Usuarios(perfil_id);
GO

/* ============================================================
   RN001 - Cada mesa possui QR Code exclusivo, garantido pelo BD.
   ============================================================ */
CREATE TABLE dbo.Mesas (
    id_mesa        BIGINT IDENTITY(1,1) NOT NULL,
    numero         INT NOT NULL,
    qr_code        NVARCHAR(255) COLLATE Latin1_General_100_BIN2 NOT NULL,
    ativa          BIT NOT NULL CONSTRAINT DF_Mesas_Ativa DEFAULT (1), -- RN001
    criado_em      DATETIME2(0) NOT NULL CONSTRAINT DF_Mesas_CriadoEm DEFAULT GETDATE(), -- RN001
    alterado_em    DATETIME2(0) NULL,

    CONSTRAINT PK_Mesas PRIMARY KEY (id_mesa), -- RN001
    CONSTRAINT UQ_Mesas_Numero UNIQUE (numero), -- RN001
    CONSTRAINT UQ_Mesas_QrCode UNIQUE (qr_code), -- RN001
    CONSTRAINT CK_Mesas_Numero CHECK (numero > 0) -- RN001
);
GO

/* ============================================================
   Categorias de produtos.
   Exclusão lógica via ativo evita quebrar produtos históricos.
   ============================================================ */
CREATE TABLE dbo.Categorias (
    id_categoria   BIGINT IDENTITY(1,1) NOT NULL,
    nome           NVARCHAR(100) NOT NULL,
    descricao      NVARCHAR(300) NULL,
    ativo          BIT NOT NULL CONSTRAINT DF_Categorias_Ativo DEFAULT (1), -- RN007/RN008
    criado_em      DATETIME2(0) NOT NULL CONSTRAINT DF_Categorias_CriadoEm DEFAULT GETDATE(), -- RN007/RN008
    alterado_em    DATETIME2(0) NULL,

    CONSTRAINT PK_Categorias PRIMARY KEY (id_categoria), -- RN007/RN008
    CONSTRAINT UQ_Categorias_Nome UNIQUE (nome) -- RN007/RN008
);
GO

/* ============================================================
   RN007/RF014 - Estoque e disponibilidade ficam no schema.
   RN008 - Auditoria mínima: alterado_por e alterado_em.
   Regra monetária - preço sempre DECIMAL(10,2), nunca FLOAT/REAL.
   ============================================================ */
CREATE TABLE dbo.Produtos (
    id_produto      BIGINT IDENTITY(1,1) NOT NULL,
    categoria_id    BIGINT NOT NULL,
    nome            NVARCHAR(150) NOT NULL,
    descricao       NVARCHAR(500) NULL,
    preco           DECIMAL(10,2) NOT NULL,
    estoque         INT NOT NULL CONSTRAINT DF_Produtos_Estoque DEFAULT (0), -- RN007/RN008
    disponivel      BIT NOT NULL CONSTRAINT DF_Produtos_Disponivel DEFAULT (0), -- RN007/RN008
    ativo           BIT NOT NULL CONSTRAINT DF_Produtos_Ativo DEFAULT (1), -- RN007/RN008
    criado_em       DATETIME2(0) NOT NULL CONSTRAINT DF_Produtos_CriadoEm DEFAULT GETDATE(), -- RN007/RN008
    alterado_em     DATETIME2(0) NULL,
    alterado_por    BIGINT NULL,

    CONSTRAINT PK_Produtos PRIMARY KEY (id_produto), -- RN007/RN008
    CONSTRAINT CK_Produtos_Preco CHECK (preco > 0), -- RN007/RN008
    CONSTRAINT CK_Produtos_Estoque CHECK (estoque >= 0), -- RN007/RN008
    CONSTRAINT FK_Produtos_Categorias FOREIGN KEY (categoria_id) -- RN007/RN008
        REFERENCES dbo.Categorias(id_categoria)
        ON DELETE NO ACTION,
    CONSTRAINT FK_Produtos_AlteradoPor FOREIGN KEY (alterado_por) -- RN007/RN008
        REFERENCES dbo.Usuarios(id_usuario)
        ON DELETE NO ACTION
);
GO

/*
   ON DELETE NO ACTION em Categoria: categoria utilizada não pode sumir e quebrar produto.
   ON DELETE NO ACTION em alterado_por: preserva a identidade do usuário da auditoria (RN008).
*/
CREATE INDEX IX_Produtos_Categoria ON dbo.Produtos(categoria_id);
CREATE INDEX IX_Produtos_Disponibilidade ON dbo.Produtos(ativo, disponivel, categoria_id);
GO

/* ============================================================
   RN007/RF014 - Sincroniza estoque/ativo com disponibilidade.
   estoque = 0 ou ativo = 0 => indisponível.
   estoque > 0 e ativo = 1 => disponível.
   ============================================================ */
CREATE TRIGGER dbo.TR_Produtos_SincronizaDisponibilidade
ON dbo.Produtos
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    /* Evita recursão causada pelo UPDATE executado pelo próprio trigger. */
    IF TRIGGER_NESTLEVEL() > 1 RETURN;

    UPDATE p
       SET p.disponivel = CASE WHEN p.estoque > 0 AND p.ativo = 1 THEN 1 ELSE 0 END
    FROM dbo.Produtos p
    INNER JOIN inserted i ON i.id_produto = p.id_produto
    WHERE p.disponivel <> CASE WHEN p.estoque > 0 AND p.ativo = 1 THEN 1 ELSE 0 END;
END;
GO

/*
   RN008 + preservação de histórico:
   DELETE de produto vira exclusão lógica, mantendo referências de pedidos antigos.
*/
CREATE TRIGGER dbo.TR_Produtos_SoftDelete
ON dbo.Produtos
INSTEAD OF DELETE
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE p
       SET p.ativo = 0,
           p.disponivel = 0,
           p.alterado_em = GETDATE()
    FROM dbo.Produtos p
    INNER JOIN deleted d ON d.id_produto = p.id_produto;
END;
GO

/* ============================================================
   RN005 - Status operacional normalizado.
   ============================================================ */
CREATE TABLE dbo.Status_Pedido (
    id_status      BIGINT IDENTITY(1,1) NOT NULL,
    nome           NVARCHAR(30) NOT NULL,
    ordem_fluxo    TINYINT NOT NULL,

    CONSTRAINT PK_Status_Pedido PRIMARY KEY (id_status), -- RN005
    CONSTRAINT UQ_Status_Pedido_Nome UNIQUE (nome), -- RN005
    CONSTRAINT UQ_Status_Pedido_Ordem UNIQUE (ordem_fluxo), -- RN005
    CONSTRAINT CK_Status_Pedido_Ordem CHECK (ordem_fluxo BETWEEN 1 AND 4) -- RN005
);
GO

/* RN005 - Fluxo fixo: Recebido -> Em preparo -> Pronto -> Entregue. */
INSERT INTO dbo.Status_Pedido (nome, ordem_fluxo)
VALUES
    (N'Recebido',   1),
    (N'Em preparo', 2),
    (N'Pronto',     3),
    (N'Entregue',   4);
GO

/* ============================================================
   RN005 - Relações de transição válidas garantidas pelo banco.
   ============================================================ */
CREATE TABLE dbo.Transicoes_Status_Pedido (
    status_origem_id   BIGINT NOT NULL,
    status_destino_id  BIGINT NOT NULL,

    CONSTRAINT PK_Transicoes_Status_Pedido PRIMARY KEY (status_origem_id, status_destino_id), -- RN005
    CONSTRAINT CK_Transicoes_Status_Diferentes CHECK (status_origem_id <> status_destino_id), -- RN005
    CONSTRAINT FK_Transicoes_Status_Origem FOREIGN KEY (status_origem_id) -- RN005
        REFERENCES dbo.Status_Pedido(id_status)
        ON DELETE NO ACTION,
    CONSTRAINT FK_Transicoes_Status_Destino FOREIGN KEY (status_destino_id) -- RN005
        REFERENCES dbo.Status_Pedido(id_status)
        ON DELETE NO ACTION
);
GO

/* ON DELETE NO ACTION: status utilizado pelo fluxo não pode ser removido. */
INSERT INTO dbo.Transicoes_Status_Pedido (status_origem_id, status_destino_id)
SELECT origem.id_status, destino.id_status
FROM (VALUES
    (N'Recebido',   N'Em preparo'),
    (N'Em preparo', N'Pronto'),
    (N'Pronto',     N'Entregue')
) v(origem_nome, destino_nome)
INNER JOIN dbo.Status_Pedido origem ON origem.nome = v.origem_nome
INNER JOIN dbo.Status_Pedido destino ON destino.nome = v.destino_nome;
GO

/* ============================================================
   RN003/RN004/RN009 - Pedido principal.
   RN004: criado_em é gerado pelo SQL Server com GETDATE().
   RN009: excluido_em implementa soft delete.
   confirmado permite criar pedido + itens atomicamente antes de congelá-lo.
   ============================================================ */
CREATE TABLE dbo.Pedidos (
    id_pedido       BIGINT IDENTITY(1,1) NOT NULL,
    mesa_id         BIGINT NOT NULL,
    status_id       BIGINT NOT NULL,
    valor_total     DECIMAL(10,2) NOT NULL CONSTRAINT DF_Pedidos_ValorTotal DEFAULT (0), -- RN003/RN004/RN009
    confirmado      BIT NOT NULL CONSTRAINT DF_Pedidos_Confirmado DEFAULT (0), -- RN003/RN004/RN009
    confirmado_em   DATETIME2(0) NULL,
    criado_em       DATETIME2(0) NOT NULL CONSTRAINT DF_Pedidos_CriadoEm DEFAULT GETDATE(), -- RN003/RN004/RN009
    alterado_em     DATETIME2(0) NULL,
    alterado_por    BIGINT NULL,
    excluido_em     DATETIME2(0) NULL,

    CONSTRAINT PK_Pedidos PRIMARY KEY (id_pedido), -- RN003/RN004/RN009
    CONSTRAINT CK_Pedidos_ValorTotal CHECK (valor_total >= 0), -- RN003/RN004/RN009
    CONSTRAINT CK_Pedidos_Confirmacao CHECK ( -- RN003/RN004/RN009
        (confirmado = 0 AND confirmado_em IS NULL)
        OR
        (confirmado = 1 AND confirmado_em IS NOT NULL)
    ),
    CONSTRAINT FK_Pedidos_Mesas FOREIGN KEY (mesa_id) -- RN003/RN004/RN009
        REFERENCES dbo.Mesas(id_mesa)
        ON DELETE NO ACTION,
    CONSTRAINT FK_Pedidos_Status FOREIGN KEY (status_id) -- RN003/RN004/RN009
        REFERENCES dbo.Status_Pedido(id_status)
        ON DELETE NO ACTION,
    CONSTRAINT FK_Pedidos_AlteradoPor FOREIGN KEY (alterado_por) -- RN003/RN004/RN009
        REFERENCES dbo.Usuarios(id_usuario)
        ON DELETE NO ACTION
);
GO

/*
   ON DELETE NO ACTION em Mesa, Status e Usuário preserva o histórico do pedido (RN009).
   Índices suportam fila por chegada e filtro por status (RN004/RN005).
*/
CREATE INDEX IX_Pedidos_CriadoEm ON dbo.Pedidos(criado_em, id_pedido);
CREATE INDEX IX_Pedidos_Status_CriadoEm ON dbo.Pedidos(status_id, criado_em, id_pedido);
CREATE INDEX IX_Pedidos_Mesa ON dbo.Pedidos(mesa_id, criado_em);
GO

/* ============================================================
   RN003/RNF005 - Itens são separados do cabeçalho do pedido.
   preco_unitario é snapshot: alteração futura do preço do produto
   não modifica pedidos antigos.
   ============================================================ */
CREATE TABLE dbo.Itens_Pedido (
    id_item          BIGINT IDENTITY(1,1) NOT NULL,
    pedido_id        BIGINT NOT NULL,
    produto_id       BIGINT NOT NULL,
    quantidade       INT NOT NULL,
    preco_unitario   DECIMAL(10,2) NOT NULL,
    subtotal AS CONVERT(DECIMAL(10,2), quantidade * preco_unitario) PERSISTED,
    criado_em        DATETIME2(0) NOT NULL CONSTRAINT DF_ItensPedido_CriadoEm DEFAULT GETDATE(), -- RN003/RNF005

    CONSTRAINT PK_Itens_Pedido PRIMARY KEY (id_item), -- RN003/RNF005
    CONSTRAINT UQ_Itens_Pedido_PedidoProduto UNIQUE (pedido_id, produto_id), -- RN003/RNF005
    CONSTRAINT CK_Itens_Pedido_Quantidade CHECK (quantidade > 0), -- RN003/RNF005
    CONSTRAINT CK_Itens_Pedido_Preco CHECK (preco_unitario > 0), -- RN003/RNF005
    CONSTRAINT FK_Itens_Pedido_Pedidos FOREIGN KEY (pedido_id) -- RN003/RNF005
        REFERENCES dbo.Pedidos(id_pedido)
        ON DELETE NO ACTION,
    CONSTRAINT FK_Itens_Pedido_Produtos FOREIGN KEY (produto_id) -- RN003/RNF005
        REFERENCES dbo.Produtos(id_produto)
        ON DELETE NO ACTION
);
GO

/* ON DELETE NO ACTION em Pedido/Produto preserva histórico (RN003/RN009). */
CREATE INDEX IX_Itens_Pedido_Pedido ON dbo.Itens_Pedido(pedido_id);
CREATE INDEX IX_Itens_Pedido_Produto ON dbo.Itens_Pedido(produto_id);
GO

/* ============================================================
   RN003 - Depois da confirmação, itens ficam imutáveis no banco.
   A regra vale para INSERT, UPDATE e DELETE, evitando alterações diretas.
   ============================================================ */
CREATE TRIGGER dbo.TR_Itens_Pedido_ImutavelAposConfirmacao
ON dbo.Itens_Pedido
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (
        SELECT 1
        FROM inserted i
        INNER JOIN dbo.Pedidos p ON p.id_pedido = i.pedido_id
        WHERE p.confirmado = 1
    )
    OR EXISTS (
        SELECT 1
        FROM deleted d
        INNER JOIN dbo.Pedidos p ON p.id_pedido = d.pedido_id
        WHERE p.confirmado = 1
    )
    BEGIN
        RAISERROR(N'RN003: itens de pedido confirmado são imutáveis.', 16, 1);
        ROLLBACK TRANSACTION;
        RETURN;
    END;
END;
GO

/*
   Mantém valor_total consistente durante a montagem de pedidos ainda não confirmados.
   Se a operação for inválida em pedido confirmado, o trigger RN003 faz rollback da instrução inteira.
*/
CREATE TRIGGER dbo.TR_Itens_Pedido_RecalculaTotal
ON dbo.Itens_Pedido
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;

    ;WITH PedidosAfetados AS (
        SELECT pedido_id FROM inserted
        UNION
        SELECT pedido_id FROM deleted
    )
    UPDATE p
       SET p.valor_total = ISNULL(x.total, 0),
           p.alterado_em = GETDATE()
    FROM dbo.Pedidos p
    INNER JOIN PedidosAfetados pa ON pa.pedido_id = p.id_pedido
    OUTER APPLY (
        SELECT CONVERT(DECIMAL(10,2), SUM(ip.subtotal)) AS total
        FROM dbo.Itens_Pedido ip
        WHERE ip.pedido_id = p.id_pedido
    ) x
    WHERE p.confirmado = 0;
END;
GO

/* ============================================================
   RN005 - Histórico das mudanças de status.
   ============================================================ */
CREATE TABLE dbo.Historico_Status_Pedido (
    id_historico     BIGINT IDENTITY(1,1) NOT NULL,
    pedido_id        BIGINT NOT NULL,
    status_id        BIGINT NOT NULL,
    alterado_por     BIGINT NULL,
    alterado_em      DATETIME2(0) NOT NULL CONSTRAINT DF_HistoricoStatus_AlteradoEm DEFAULT GETDATE(), -- RN005/RN009

    CONSTRAINT PK_Historico_Status_Pedido PRIMARY KEY (id_historico), -- RN005/RN009
    CONSTRAINT FK_Historico_Status_Pedido FOREIGN KEY (pedido_id) -- RN005/RN009
        REFERENCES dbo.Pedidos(id_pedido)
        ON DELETE NO ACTION,
    CONSTRAINT FK_Historico_Status_Status FOREIGN KEY (status_id) -- RN005/RN009
        REFERENCES dbo.Status_Pedido(id_status)
        ON DELETE NO ACTION,
    CONSTRAINT FK_Historico_Status_Usuario FOREIGN KEY (alterado_por) -- RN005/RN009
        REFERENCES dbo.Usuarios(id_usuario)
        ON DELETE NO ACTION
);
GO

/* ON DELETE NO ACTION preserva a trilha histórica de status (RN005/RN009). */
CREATE INDEX IX_Historico_Status_Pedido ON dbo.Historico_Status_Pedido(pedido_id, alterado_em, id_historico);
GO

/* ============================================================
   RN005 - Garante no banco:
   1) novo pedido inicia em Recebido;
   2) mudanças respeitam Transicoes_Status_Pedido;
   3) cada status é registrado no histórico.
   ============================================================ */
CREATE TRIGGER dbo.TR_Pedidos_ValidaEHistorizaStatus
ON dbo.Pedidos
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    /* Novo pedido precisa iniciar em Recebido. */
    IF EXISTS (
        SELECT 1
        FROM inserted i
        LEFT JOIN deleted d ON d.id_pedido = i.id_pedido
        INNER JOIN dbo.Status_Pedido s ON s.id_status = i.status_id
        WHERE d.id_pedido IS NULL
          AND s.nome <> N'Recebido'
    )
    BEGIN
        RAISERROR(N'RN005: todo pedido deve iniciar com status Recebido.', 16, 1);
        ROLLBACK TRANSACTION;
        RETURN;
    END;

    /* Atualização de status precisa existir na tabela de transições válidas. */
    IF EXISTS (
        SELECT 1
        FROM inserted i
        INNER JOIN deleted d ON d.id_pedido = i.id_pedido
        LEFT JOIN dbo.Transicoes_Status_Pedido t
               ON t.status_origem_id = d.status_id
              AND t.status_destino_id = i.status_id
        WHERE i.status_id <> d.status_id
          AND t.status_origem_id IS NULL
    )
    BEGIN
        RAISERROR(N'RN005: transição de status inválida.', 16, 1);
        ROLLBACK TRANSACTION;
        RETURN;
    END;

    /* Histórico do status inicial. */
    INSERT INTO dbo.Historico_Status_Pedido (pedido_id, status_id, alterado_por, alterado_em)
    SELECT i.id_pedido, i.status_id, i.alterado_por, i.criado_em
    FROM inserted i
    LEFT JOIN deleted d ON d.id_pedido = i.id_pedido
    WHERE d.id_pedido IS NULL;

    /* Histórico de cada mudança válida de status. */
    INSERT INTO dbo.Historico_Status_Pedido (pedido_id, status_id, alterado_por, alterado_em)
    SELECT i.id_pedido, i.status_id, i.alterado_por, GETDATE()
    FROM inserted i
    INNER JOIN deleted d ON d.id_pedido = i.id_pedido
    WHERE i.status_id <> d.status_id;
END;
GO

/* ============================================================
   RN009 - DELETE físico de pedido é proibido.
   Um DELETE solicitado pela aplicação vira soft delete.
   ============================================================ */
CREATE TRIGGER dbo.TR_Pedidos_SoftDelete
ON dbo.Pedidos
INSTEAD OF DELETE
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE p
       SET p.excluido_em = COALESCE(p.excluido_em, GETDATE()),
           p.alterado_em = GETDATE()
    FROM dbo.Pedidos p
    INNER JOIN deleted d ON d.id_pedido = p.id_pedido;
END;
GO

/* ============================================================
   Integração com o PaymentService existente no backend.
   Status financeiro fica separado do status operacional RN005.
   Assim PAGO/CANCELADO não corrompe o fluxo da cozinha.
   ============================================================ */
CREATE TABLE dbo.Pagamentos (
    id_pagamento     BIGINT IDENTITY(1,1) NOT NULL,
    pedido_id        BIGINT NOT NULL,
    valor            DECIMAL(10,2) NOT NULL,
    metodo           NVARCHAR(20) NULL,
    status           NVARCHAR(20) NOT NULL CONSTRAINT DF_Pagamentos_Status DEFAULT (N'PENDENTE'), -- RN009 + compatibilidade com PaymentService
    referencia_gateway NVARCHAR(150) NULL,
    criado_em        DATETIME2(0) NOT NULL CONSTRAINT DF_Pagamentos_CriadoEm DEFAULT GETDATE(), -- RN009 + compatibilidade com PaymentService
    alterado_em      DATETIME2(0) NULL,
    processado_em    DATETIME2(0) NULL,

    CONSTRAINT PK_Pagamentos PRIMARY KEY (id_pagamento), -- RN009 + compatibilidade com PaymentService
    CONSTRAINT CK_Pagamentos_Valor CHECK (valor >= 0), -- RN009 + compatibilidade com PaymentService
    CONSTRAINT CK_Pagamentos_Metodo CHECK (metodo IS NULL OR metodo IN (N'PIX', N'DEBITO', N'CREDITO')), -- RN009 + compatibilidade com PaymentService
    CONSTRAINT CK_Pagamentos_Status CHECK (status IN (N'PENDENTE', N'PAGO', N'CANCELADO', N'FALHOU')), -- RN009 + compatibilidade com PaymentService
    CONSTRAINT FK_Pagamentos_Pedidos FOREIGN KEY (pedido_id) -- RN009 + compatibilidade com PaymentService
        REFERENCES dbo.Pedidos(id_pedido)
        ON DELETE NO ACTION
);
GO

/* ON DELETE NO ACTION em Pagamentos preserva histórico financeiro do pedido. */
CREATE INDEX IX_Pagamentos_Pedido ON dbo.Pagamentos(pedido_id, criado_em);
CREATE UNIQUE INDEX UX_Pagamentos_ReferenciaGateway
ON dbo.Pagamentos(referencia_gateway)
WHERE referencia_gateway IS NOT NULL;
GO

/* ============================================================
   RNF005 - Tipo de tabela usado para enviar todos os itens de uma vez.
   PRIMARY KEY impede o mesmo produto duplicado na mesma requisição.
   ============================================================ */
CREATE TYPE dbo.TipoItemPedido AS TABLE (
    produto_id  BIGINT NOT NULL PRIMARY KEY, -- RNF005
    quantidade  INT NOT NULL CHECK (quantidade > 0) -- RNF005
);
GO

/* ============================================================
   RNF005 + RN003 + RN004 + RN007:
   Cria cabeçalho, itens, baixa estoque, calcula total e confirma o
   pedido dentro de UMA transação atômica.
   UPDLOCK/HOLDLOCK evita duas vendas simultâneas do mesmo estoque.
   ============================================================ */
CREATE PROCEDURE dbo.sp_CriarPedido
    @mesa_id      BIGINT,
    @itens        dbo.TipoItemPedido READONLY,
    @criado_por   BIGINT = NULL,
    @id_pedido    BIGINT OUTPUT
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
            RAISERROR(N'Existe produto inexistente, indisponível ou sem estoque suficiente.', 16, 1);
        END;

        DECLARE @status_recebido BIGINT;
        SELECT @status_recebido = id_status
        FROM dbo.Status_Pedido
        WHERE nome = N'Recebido';

        IF @status_recebido IS NULL
        BEGIN
            RAISERROR(N'Status inicial Recebido não está configurado.', 16, 1);
        END;

        /* Pedido nasce não confirmado apenas durante esta transação. */
        INSERT INTO dbo.Pedidos (
            mesa_id,
            status_id,
            valor_total,
            confirmado,
            alterado_por
        )
        VALUES (
            @mesa_id,
            @status_recebido,
            0,
            0,
            @criado_por
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

/* ============================================================
   RN005 - Procedure segura para avançar status.
   A trigger continua sendo a autoridade final de integridade.
   ============================================================ */
CREATE PROCEDURE dbo.sp_AvancarStatusPedido
    @id_pedido     BIGINT,
    @novo_status   NVARCHAR(30),
    @alterado_por  BIGINT = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @novo_status_id BIGINT;

    SELECT @novo_status_id = id_status
    FROM dbo.Status_Pedido
    WHERE nome = @novo_status;

    IF @novo_status_id IS NULL
    BEGIN
        RAISERROR(N'Status informado não existe.', 16, 1);
        RETURN;
    END;

    UPDATE dbo.Pedidos
       SET status_id = @novo_status_id,
           alterado_por = @alterado_por,
           alterado_em = GETDATE()
    WHERE id_pedido = @id_pedido
      AND excluido_em IS NULL;

    IF @@ROWCOUNT = 0
    BEGIN
        RAISERROR(N'Pedido não encontrado ou removido logicamente.', 16, 1);
        RETURN;
    END;
END;
GO

/* ============================================================
   Views úteis para o backend e dashboards.
   Elas ocultam registros removidos logicamente.
   ============================================================ */
CREATE VIEW dbo.vw_Produtos_Ativos
AS
    SELECT
        p.id_produto,
        p.categoria_id,
        c.nome AS categoria,
        p.nome,
        p.descricao,
        p.preco,
        p.estoque,
        p.disponivel,
        p.criado_em,
        p.alterado_em,
        p.alterado_por
    FROM dbo.Produtos p
    INNER JOIN dbo.Categorias c ON c.id_categoria = p.categoria_id
    WHERE p.ativo = 1;
GO

CREATE VIEW dbo.vw_Pedidos_Ativos
AS
    SELECT
        p.id_pedido,
        p.mesa_id,
        m.numero AS mesa_numero,
        p.status_id,
        s.nome AS status,
        p.valor_total,
        p.confirmado,
        p.confirmado_em,
        p.criado_em,
        p.alterado_em,
        p.alterado_por
    FROM dbo.Pedidos p
    INNER JOIN dbo.Mesas m ON m.id_mesa = p.mesa_id
    INNER JOIN dbo.Status_Pedido s ON s.id_status = p.status_id
    WHERE p.excluido_em IS NULL;
GO

/* ============================================================
   DADOS BÁSICOS OPCIONAIS PARA PRIMEIROS TESTES
   (sem criar usuário/senha fictícios).
   ============================================================ */
INSERT INTO dbo.Categorias (nome, descricao)
VALUES
    (N'Pratos', N'Pratos principais'),
    (N'Bebidas', N'Bebidas do cardápio'),
    (N'Sobremesas', N'Sobremesas do cardápio');
GO

/*
EXEMPLO DE TESTE - execute somente depois de cadastrar mesa e produtos:

DECLARE @Itens dbo.TipoItemPedido;
INSERT INTO @Itens (produto_id, quantidade)
VALUES (1, 2), (2, 1);

DECLARE @PedidoId BIGINT;
EXEC dbo.sp_CriarPedido
    @mesa_id = 1,
    @itens = @Itens,
    @criado_por = NULL,
    @id_pedido = @PedidoId OUTPUT;

SELECT @PedidoId AS pedido_criado;
SELECT * FROM dbo.vw_Pedidos_Ativos WHERE id_pedido = @PedidoId;
SELECT * FROM dbo.Itens_Pedido WHERE pedido_id = @PedidoId;
*/
GO


SELECT TABLE_NAME
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_TYPE = 'BASE TABLE'
ORDER BY TABLE_NAME;