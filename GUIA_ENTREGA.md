# TableHub — demonstração e preparação da entrega

## Ambiente

Esta rodada não executou banco do restaurante, migração ou implantação. Não há SQL Server de teste separado disponível. O navegador automatizado intercepta uma API sintética; esse resultado não comprova persistência no SQL Server.

Para homologação real, preparar banco isolado com a estrutura/procedures/triggers existentes, dados fictícios e contas por perfil. Configurar segredos fora do Git. Validar os passos abaixo e registrar resultado, horário, ambiente e evidência sem dados sensíveis. Não usar o banco operacional para gerar movimentações simuladas apenas para teste.

## Roteiro do vídeo

| Tempo sugerido | Demonstração | Evidência esperada |
| --- | --- | --- |
| 00:00–00:30 | Apresentar o ambiente e fazer login com conta de teste | Nenhuma senha/token visível na gravação |
| 00:30–01:00 | Dashboard e papéis de equipe | Números do ambiente declarado; Cozinha sem acesso administrativo |
| 01:00–02:00 | Cadastrar categoria/produto com preço 19,99; editar descrição/preço | Registro reaparece após atualização; validação de erro no formulário |
| 02:00–02:40 | Ajustar estoque | Delta e quantidade esperada; conflito não sobrescreve venda concorrente |
| 02:40–03:30 | Cadastrar mesa e abrir QR | Número único; link assinado e domínio acessível pelo telefone |
| 03:30–04:30 | Abrir QR no celular, selecionar item e confirmar pedido | Mesa correta, preço oficial, estoque atualizado |
| 04:30–05:30 | Mostrar pedido associado à mesa e avançar pela equipe | Recebido → Em preparo → Pronto → Entregue; cliente acompanha |
| 05:30–06:20 | Chamar garçom e solicitar conta | Chamado aparece; avanço até Concluído; repetição idêntica reutiliza aberto |
| 06:20–07:10 | Solicitar e confirmar pagamento simulado | Pendente antes da confirmação; somente equipe confirma; nenhuma cobrança real |
| 07:10–07:40 | Mesas e pedidos anônimos | Mesa libera após entregar/pagar; não aparecem clientes fictícios |
| 07:40–08:30 | Atualizar páginas e reiniciar serviços no ambiente isolado | Conferir persistência real, quando houver SQL Server de teste |

Notificação push real exige VAPID configurado, HTTPS, permissão do navegador e entrega do provedor. Se isso não estiver validado, mostrar o polling e declarar que o push real permanece pendente. Não representar fixture ou screenshot como prova de gravação no banco.

## Homologação integrada pendente

1. Conferir conta Administrador e perfis no banco isolado.
2. Conferir versão instalada de procedures, triggers, tipos TVP, índices e constraints; os CSVs de estrutura não provam essas definições.
3. Executar o fluxo do vídeo com mesa cujo número seja diferente da PK e com mais de oito mesas.
4. Disputar duas vendas pelo último item e venda contra ajuste de estoque. Conferir transação/rollback completo.
5. Repetir POST de pedido após falha de comunicação: registrar a limitação de idempotência antes de adotar em operação.
6. Criar chamados simultâneos iguais e diferentes, verificar deduplicação e nenhuma notificação em rollback.
7. Conferir confirmação/cancelamento repetidos de pagamento, eventos após commit e dados financeiros legados duplicados.
8. Conferir mesa desativada, contexto expirado, token de outra mesa/pedido e acesso administrativo por todos os papéis.
9. Atualizar e reiniciar os serviços, validar persistência e comportamento de notificações perdidas na fila em memória.
10. Testar telefone real, ambos os temas, leitura física do QR e recuperação após queda de rede.

## Hospedagem — decisões antes de contratar

| Opção | Vantagem | Requisito/custo a avaliar |
| --- | --- | --- |
| Frontend estático + serviço Java/container + SQL Server existente | Permite entregar cada repositório separadamente | Tráfego, runtime Java 21, conexão de rede segura com SQL Server e limites de plano |
| Plataforma integrada com frontend estático, aplicação e banco gerenciado | Menos administração de infraestrutura | Mensalidade de aplicação/banco, armazenamento, backups e regras de firewall |
| VPS com proxy HTTPS, aplicação e SQL Server administrados pela equipe | Controle de rede e execução | RAM/disco, atualizações, licença/limites da edição SQL Server, backup e manutenção |

Valores e fornecedor dependem da plataforma e plano escolhidos; nenhum serviço foi contratado, nenhum custo foi assumido e nenhuma publicação foi feita. A escolha de plano e orçamento precisa de confirmação antes de qualquer contratação.

## Guia de implantação após autorização

- Usar os commits revisados de cada repositório. Guardar o artefato anterior para rollback sem alterar o banco.
- Configurar DB_URL/DB_USER/DB_PASSWORD/JWT_SECRET/VAPID no backend, sem arquivos públicos. Usar TLS com certificado validado para o SQL Server e conta de privilégio mínimo.
- Configurar `app.cors.allowed-origins` com a origem HTTPS exata do frontend. Proxy deve preservar WebSocket e ter limites de corpo, frequência e timeout; revisar identificação do IP real sem confiar cegamente em headers enviados pelo cliente.
- Compilar o frontend com VITE_API_URL e VITE_PUBLIC_URL corretos. Essas variáveis são públicas; não incluir segredos.
- Configurar HTTPS, fallback SPA, tipos MIME e cabeçalhos de segurança do host estático. Revisar CSP conforme recursos realmente usados, sem política improvisada que quebre o aplicativo.
- Conferir login, consulta pública, emissão de QR, pedido, atendimento e simulação no ambiente isolado. Reemitir/imprimir os QR novos; manter fallback numérico para ações anularia a proteção.
- Planejar rotação da chave de assinatura: ela invalida sessões/capacidades e QRs associados. QR público pode ser compartilhado e não comprova presença física.
- Atualizar framework/dependências coordenadamente e repetir os testes antes do uso público. O patch específico de BCrypt não torna toda a plataforma atualizada.
- Só promover à produção após autorização específica e homologação. Não apontar fixtures do teste de navegador para o backend operacional.
