USE TableHub;
GO

SET XACT_ABORT ON;
GO

/*
  Cardápio inicial compatível com o frontend TableHub.
  - Não apaga produtos existentes.
  - Cria categorias ausentes.
  - Atualiza nome/categoria/descrição/preço dos produtos conhecidos.
  - Preserva o estoque de produtos já existentes.
  - Produtos novos entram com estoque inicial 50.
*/

BEGIN TRANSACTION;

DECLARE @Categorias TABLE (
    nome NVARCHAR(100) NOT NULL,
    descricao NVARCHAR(300) NULL
);

INSERT INTO @Categorias (nome, descricao)
VALUES
    (N'Hambúrgueres',     N'Hambúrgueres e lanches principais'),
    (N'Acompanhamentos',  N'Porções e acompanhamentos'),
    (N'Bebidas',          N'Bebidas do cardápio'),
    (N'Sobremesas',       N'Sobremesas do cardápio');

INSERT INTO dbo.Categorias (nome, descricao)
SELECT c.nome, c.descricao
FROM @Categorias c
WHERE NOT EXISTS (
    SELECT 1
    FROM dbo.Categorias existente
    WHERE existente.nome = c.nome
);

UPDATE destino
SET
    destino.descricao = origem.descricao,
    destino.ativo = 1,
    destino.alterado_em = GETDATE()
FROM dbo.Categorias destino
INNER JOIN @Categorias origem
    ON origem.nome = destino.nome;

DECLARE @Produtos TABLE (
    categoria NVARCHAR(100) NOT NULL,
    nome NVARCHAR(150) NOT NULL,
    descricao NVARCHAR(500) NULL,
    preco DECIMAL(10,2) NOT NULL
);

INSERT INTO @Produtos (categoria, nome, descricao, preco)
VALUES
    (N'Hambúrgueres',    N'Hambúrguer com Bacon',     N'Hambúrguer com bacon.',                31.90),
    (N'Hambúrgueres',    N'Hambúrguer de Frango',     N'Hambúrguer de frango.',                24.00),
    (N'Acompanhamentos', N'Batata Frita',              N'Porção de batata frita.',              18.00),
    (N'Acompanhamentos', N'Batata Rústica',            N'Porção de batata rústica.',            20.00),
    (N'Acompanhamentos', N'Onion Rings',               N'Porção de anéis de cebola.',           20.00),
    (N'Acompanhamentos', N'Nuggets de Frango',         N'Porção de nuggets de frango.',         18.00),
    (N'Acompanhamentos', N'Mini Pastéis',              N'Porção de mini pastéis.',              22.00),
    (N'Bebidas',         N'Coca-Cola 350 ml',          N'Coca-Cola lata 350 ml.',               12.90),
    (N'Sobremesas',      N'Banoffee',                  N'Sobremesa banoffee.',                   16.00),
    (N'Sobremesas',      N'Petit Gâteau',              N'Petit gâteau.',                         22.00),
    (N'Sobremesas',      N'Pudim de Leite',            N'Pudim de leite.',                       14.00);

UPDATE p
SET
    p.categoria_id = c.id_categoria,
    p.descricao = origem.descricao,
    p.preco = origem.preco,
    p.ativo = 1,
    p.alterado_em = GETDATE()
FROM dbo.Produtos p
INNER JOIN @Produtos origem
    ON origem.nome = p.nome
INNER JOIN dbo.Categorias c
    ON c.nome = origem.categoria;

INSERT INTO dbo.Produtos (
    categoria_id,
    nome,
    descricao,
    preco,
    estoque
)
SELECT
    c.id_categoria,
    origem.nome,
    origem.descricao,
    origem.preco,
    50
FROM @Produtos origem
INNER JOIN dbo.Categorias c
    ON c.nome = origem.categoria
WHERE NOT EXISTS (
    SELECT 1
    FROM dbo.Produtos p
    WHERE p.nome = origem.nome
);

COMMIT TRANSACTION;
GO

SELECT
    p.id_produto,
    c.nome AS categoria,
    p.nome,
    p.preco,
    p.estoque,
    p.disponivel,
    p.ativo
FROM dbo.Produtos p
INNER JOIN dbo.Categorias c
    ON c.id_categoria = p.categoria_id
WHERE p.nome IN (
    N'Hambúrguer com Bacon',
    N'Hambúrguer de Frango',
    N'Batata Frita',
    N'Batata Rústica',
    N'Onion Rings',
    N'Nuggets de Frango',
    N'Mini Pastéis',
    N'Coca-Cola 350 ml',
    N'Banoffee',
    N'Petit Gâteau',
    N'Pudim de Leite'
)
ORDER BY
    c.nome,
    p.nome;
GO
