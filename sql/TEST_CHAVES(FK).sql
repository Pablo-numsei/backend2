SELECT DB_NAME() AS BancoAtual

SELECT TABLE_NAME
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_TYPE = 'BASE TABLE'
ORDER BY TABLE_NAME


SELECT nome.Tabela,
       CASE
           WHEN t.object_id IS NOT NULL THEN 'CRIADA'
           ELSE 'FALTANDO'
       END AS Situacao
FROM (VALUES
    ('Perfis'),
    ('Usuarios'),
    ('Categorias'),
    ('Produtos'),
    ('Mesas'),
    ('Pedidos'),
    ('Itens_Pedido'),
    ('Status_Pedido'),
    ('Transicoes_Status_Pedido'),
    ('Historico_Status_Pedido'),
    ('Pagamentos'),
    ('Atendimentos_Mesa'),
    ('Push_Subscriptions')
) AS nome(Tabela)
LEFT JOIN sys.tables t
    ON t.name = nome.Tabela
    AND SCHEMA_NAME(t.schema_id) = 'dbo'
ORDER BY nome.Tabela;



SELECT
    fk.name AS ChaveEstrangeira,
    OBJECT_NAME(fkc.parent_object_id) AS TabelaOrigem,
    COL_NAME(fkc.parent_object_id, fkc.parent_column_id) AS ColunaOrigem,
    OBJECT_NAME(fkc.referenced_object_id) AS TabelaDestino,
    COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id) AS ColunaDestino
FROM sys.foreign_keys fk
JOIN sys.foreign_key_columns fkc
    ON fk.object_id = fkc.constraint_object_id
ORDER BY TabelaOrigem;
