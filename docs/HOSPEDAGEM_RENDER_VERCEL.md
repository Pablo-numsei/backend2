# Publicação do TableHub para demonstração (2026)

Arquitetura: **React/Vite na Vercel → Spring Boot/Java 21 no Render → SQL Server no Somee**. Sem SQL Server local. **Não executar scripts de criação SQL no banco existente para fazer o deploy.**

## Pré-requisito importante: chaves existentes

Você usou um JWT_SECRET temporário na escola. **Não use esse segredo temporário no serviço público associado ao banco Somee existente**, a menos que tenha certeza de que o segredo utilizado para PIN (`CUSTOMER_PIN_PEPPER`) permaneceu separado e igual ao original. Na configuração atual, o segredo do PIN usa `JWT_SECRET` como fallback. Um novo segredo pode impedir o login dos clientes cadastrados com PIN antigo.

Recupere primeiro os segredos originais configurados em casa; não os envie por chat e não os inclua em commits. Faça backup do banco antes da exposição pública. O ambiente de demonstração pode conter dados de pedidos, usuários e PINs: não disponibilize dados pessoais reais.

## Backend: Render

1. Abra https://dashboard.render.com/ → **New → Web Service**.
2. Conecte o GitHub e selecione `Pablo-numsei/backend2`.
3. Branch: **`chore/sql-auditoria-esquema-tablehub`**.
4. Runtime/Language: **Docker**; Dockerfile na raiz `./Dockerfile`. O Render fará o build; não é necessário instalar Docker na escola.
5. Selecione um plano adequado. O plano Free é útil para TCC, mas hiberna após 15 minutos sem atividade e pode demorar cerca de 1 minuto para reiniciar. Java 21 com SQL Server pode precisar de memória superior à disponível no Free.
6. Configure as variáveis de ambiente no painel Render (não em código):

| Variável | Valor |
|---|---|
| `DB_URL` | **JDBC completo** do Somee, exemplo de formato: `jdbc:sqlserver://SEU_HOST:1433;databaseName=SEU_BANCO;encrypt=true;trustServerCertificate=false` (verifique certificado TLS confiável) |
| `DB_USER` | Seu login SQL Server |
| `DB_PASSWORD` | Sua senha SQL Server |
| `JWT_SECRET` | **Segredo original** do ambiente de casa |
| `CUSTOMER_PIN_PEPPER` | Se configurado originalmente, o mesmo valor original |
| `APP_CORS_ALLOWED_ORIGINS` | Depois do frontend publicado: domínio exato HTTPS da Vercel, por exemplo `https://seu-tablehub.vercel.app` |
| `SHOW_SQL` | `false` |
| `FRONTEND_PUBLIC_URL` | URL HTTPS do frontend, se habilitar recuperação de senha |
| `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT` | Apenas se quiser testar Web Push; mantenha as chaves privadas fora do Git |

7. Crie o Web Service e aguarde logs de inicialização do Java. O `Dockerfile` já ouve em `0.0.0.0` usando `PORT` do Render. Copie a URL **real** `https://....onrender.com` exibida pelo painel.
8. Se o banco falhar, cheque DNS, acesso outbound à porta SQL, credenciais, certificado e políticas de acesso do Somee. Nunca copie senhas para logs ou issues do GitHub.

## Frontend: Vercel

1. Abra https://vercel.com/new → **Import Git Repository**.
2. Repositório: `Pablo-numsei/tablehub-front-v2`; configure **Production Branch** como `feat/customer-pin-vitrine` em **Project → Settings → Git** (se o assistente importar `main`, ajuste antes de considerar o deploy concluído). Pode ser necessário reimplantar após trocar essa branch.
3. Preset: **Vite**; Root Directory: `./`; Build command: `npm run build`; Install: `npm ci`; Output Directory: `dist`.
4. Em **Environment Variables**, use:
    - `VITE_API_URL` = URL HTTPS **real** do backend Render, **sem** `/api` no final. Exemplo de formato: `https://seu-backend.onrender.com`.
    - `VITE_PUBLIC_URL` = domínio HTTPS de produção do frontend, quando o conhecer, ou omita (a tela de QR utilizará `window.location.origin`).
5. Clique Deploy. As variáveis `VITE_*` são incorporadas **durante o build**. Se alterá-las depois, faça redeploy.
6. O arquivo `vercel.json` na raiz permite que as rotas do React Router funcionem em atualizações diretas, como `/menu` e `/login`.
7. Copie o domínio HTTPS estável de produção da Vercel e configure **esse mesmo domínio** na variável Render `APP_CORS_ALLOWED_ORIGINS`. Valores múltiplos: `https://seu-tablehub.vercel.app,http://localhost:5173`, caso queira acesso local. Salve e aguarde reinício/redeploy do backend.

## Validação ponta a ponta

- Abra o frontend Vercel no computador e celular; teste login da equipe com uma conta **de teste**, listagem de produtos, mesas, pedido de teste (em banco de demonstração).
- Abra QR da mesa pelo painel público; o link deve iniciar em `https://...` da Vercel, **nunca localhost**. Gere/redistribua QR Codes após configurar `VITE_PUBLIC_URL`.
- Teste requests na aba Network: não pode haver chamadas a `localhost:8080`, erros de CORS, `ERR_CONNECTION_REFUSED` ou misturas HTTP/HTTPS.
- WebSocket e notificações devem ser testados separadamente. Web Push precisa de HTTPS e chaves VAPID; instâncias Render Free hibernam e não oferecem disponibilidade contínua.
- Pagamentos são **simulados**; não realize operações de demonstração no banco operacional com dados reais.

## Erros comuns

| Erro | Conferir |
|---|---|
| `403 / CORS blocked` | `APP_CORS_ALLOWED_ORIGINS` deve corresponder ao domínio HTTPS exato da Vercel; sem barra final |
| `401` no login cliente PIN | Segredo JWT/PIN diferente do original; tokens anteriores expirados ou inválidos |
| `500` ou falha startup SQL | `DB_URL` JDBC completo, credenciais, hostname, porta 1433, TLS/certificado, limites do Somee |
| Site Vercel tenta acessar `localhost:8080` | `VITE_API_URL` ausente/incorreto; redeploy para incorporar a variável |
| `404` ao recarregar `/menu` | Confirmar `vercel.json` no commit efetivamente implantado |
| `OutOfMemoryError` no Render | Instância pequena; configurar recurso suficiente; não elevar heap além da RAM |
| Primeira requisição demora | Render Free hiberna após 15 min e reinicia sob demanda |

## Comandos Git locais

Backend:
```powershell
cd D:\backend2
git switch chore/sql-auditoria-esquema-tablehub
git pull --ff-only origin chore/sql-auditoria-esquema-tablehub
```

Frontend:
```powershell
cd D:\tablehub-front-v2
git switch feat/customer-pin-vitrine
git pull --ff-only origin feat/customer-pin-vitrine
npm ci
npm run build
```

Atualizar GitHub não publica automaticamente **antes** de o projeto estar conectado e configurado nos painéis Render/Vercel. Depois, commits nessas branches podem gerar novos deploys se o auto-deploy estiver habilitado.

Referências oficiais:
- https://render.com/docs/web-services
- https://render.com/docs/free
- https://vercel.com/docs/frameworks/frontend/vite
- https://vercel.com/docs/environment-variables
