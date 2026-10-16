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

## Endpoints

- \`POST /api/auth/password-reset/request\` — \`{"email":"funcionario@exemplo.com"}\` (202).
- \`POST /api/auth/password-reset/confirm\` — \`{"token":"...","novaSenha":"senha-forte-com-12+"}\` (200 se válido; 400 caso contrário).
- Ambos dispensam autenticação. Filtro de requisições limita a 5 pedidos de link/minuto/IP e 10 confirmações/minuto/IP.

**Limitação:** sessões de funcionário com JWT já emitidos continuam válidas até o vencimento normal. A troca de senha invalida os links anteriores, mas **não** invalida sessões previamente autenticadas. Para ambientes de produção, adicionar revogação de sessões por versão de credenciais.
