# Recuperação de senha — TableHub

O modal usa como referência visual o formulário Bootstrap do repositório \`projeto-html-css-scss-js-bootstrap\`. Nesse projeto antigo, o botão somente valida o e-mail e redireciona; ele não envia e-mail. No TableHub, a recuperação é implementada de verdade.

## Fluxo

1. No login, clicar em **Esqueceu a senha?** e informar o e-mail da conta de funcionário.
2. A API sempre retorna a mesma confirmação (**202**) para usuários cadastrados e inexistentes, para reduzir enumeração.
3. Uma conta ativa recebe um link no formato \`/redefinir-senha#token=...\`.
4. O token é assinado com \`JWT_SECRET\`, tem propósito exclusivo \`password-reset\`, dura 15 minutos e é associado à versão do hash atual da senha.
5. O formulário permite trocar a senha com comprimento de 12 a 72 caracteres e máximo de 72 bytes UTF-8.
6. A atualização condicional da senha na tabela \`dbo.Usuarios\` faz com que o token só funcione uma vez, inclusive com tentativas simultâneas.

**Nenhuma tabela nova é necessária na Somee.** A migração SQL não é necessária.

## Configuração SMTP (não colocar valores no GitHub)

Adicione estas variáveis **no seu ambiente seguro**, junto às já usadas para iniciar o backend:

| Variável | Finalidade |
|---|---|
| \`MAIL_HOST\` | Host SMTP do provedor de e-mail |
| \`MAIL_PORT\` | Porta SMTP (587 por padrão) |
| \`MAIL_USERNAME\` | Usuário de autenticação SMTP |
| \`MAIL_PASSWORD\` | Senha de aplicativo ou chave SMTP |
| \`MAIL_FROM\` | Endereço remetente permitido no serviço |
| \`MAIL_SMTP_AUTH\` | Autenticação, \`true\` por padrão |
| \`MAIL_STARTTLS\` | Proteção TLS, \`true\` por padrão |
| \`FRONTEND_PUBLIC_URL\` | Origem acessível do frontend (p. ex. \`http://localhost:5173\` no mesmo PC) |

Se a pessoa abrir o e-mail **no celular**, o endereço \`localhost\` não funcionará: use um domínio HTTPS público configurado e uma API acessível.

Sem \`MAIL_HOST\` e \`MAIL_FROM\`, a solicitação recebe HTTP **503** e o resto do backend continua operando.

## Render gratuito: Brevo por HTTPS (sem SMTP)

O plano gratuito do Render bloqueia conexoes de saida SMTP nas portas 25, 465 e 587.
Para usar recuperacao de senha no Render, escolha o provedor `brevo`, que envia
por HTTPS 443 sem precisar de SMTP. Nao existe alteracao necessaria no SQL.

1. Crie conta em https://www.brevo.com e valide um e-mail ou dominio remetente.
2. Obtenha uma **chave de API v3** da Brevo (e nao a senha SMTP da Brevo).
3. Em Render -> backend2 -> Environment, cadastre:

| Variavel | Render | Observacao |
|---|---|---|
| `MAIL_PROVIDER` | `brevo` | Habilita chamada HTTP para a Brevo |
| `BREVO_API_KEY` | Chave de API v3 | Segredo; nunca em commits, VITE_* ou prints |
| `MAIL_FROM` | Endereco verificado na Brevo | Pode ser diferente do Gmail de casa |
| `FRONTEND_PUBLIC_URL` | `https://tablehub-front-v2.vercel.app` | Para o link de redefinicao abrir na Vercel |

4. Salve as variaveis e faça deploy do commit atualizado na branch
   `chore/sql-auditoria-esquema-tablehub`.
5. Teste com uma **conta de funcionario de testes**: login -> Esqueceu a senha?
   -> solicitar -> receber e-mail -> redefinir. Se o envio falhar, o backend
   registra um aviso generico; confira eventos e quota no painel Brevo.

No Render em modo Brevo, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME` e `MAIL_PASSWORD`
nao sao necessarias. Se permanecerem armazenadas, nao sao utilizadas quando
`MAIL_PROVIDER=brevo`. Para diminuir exposicao, remova as credenciais SMTP
do Render quando confirmar o funcionamento da API.

O endpoint de solicitacao continua retornando HTTP 202 para contas inexistentes
e contas ativas, sem revelar a existencia do email. Envio aceito pela Brevo
nao equivale necessariamente a entrega na caixa de entrada.

Para desenvolvimento **em casa**, mantenha `MAIL_PROVIDER=smtp` (padrao)
e as variaveis `MAIL_*` do Gmail. Nao e preciso alterar codigo local.

Documentacao oficial da API:
https://developers.brevo.com/docs/send-a-transactional-email

## Endpoints

- \`POST /api/auth/password-reset/request\` — \`{"email":"funcionario@exemplo.com"}\` (202).
- \`POST /api/auth/password-reset/confirm\` — \`{"token":"...","novaSenha":"senha-forte-com-12+"}\` (200 se válido; 400 caso contrário).
- Ambos dispensam autenticação. Filtro de requisições limita a 5 pedidos de link/minuto/IP e 10 confirmações/minuto/IP.

**Limitação:** sessões de funcionário com JWT já emitidos continuam válidas até o vencimento normal. A troca de senha invalida os links anteriores, mas **não** invalida sessões previamente autenticadas. Para ambientes de produção, adicionar revogação de sessões por versão de credenciais.
