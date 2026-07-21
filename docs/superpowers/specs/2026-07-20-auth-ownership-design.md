# Autenticação e Ownership — Design

## Contexto

O Ponte hoje não tem nenhum conceito de conta ou dono. Qualquer pessoa na
mesma rede acessa `/`, o dashboard e todos os endpoints REST sem login; o
dashboard mostra o único perfil semeado ("Alex") e a predição não checa a
quem os símbolos pertencem. Isso está anotado como backlog pós-MVP desde a
revisão final e é pré-requisito para um piloto com mais de uma família (hoje
todo mundo veria os dados de todo mundo).

Esta é a primeira fatia do item "autenticação e ownership" do roadmap:
login + dono do perfil de criança já existente. Cadastro de **múltiplos**
perfis de criança por conta, papéis diferenciados (responsável vs.
terapeuta) e verificação de posse por símbolo individual na predição ficam
fora de escopo — ver seção "Fora de escopo".

## Modelo de dados

Novo pacote `br.com.ponte.account`:

- `Account` (entidade): `id`, `email` (`unique`, `not null`), `passwordHash`
  (`not null`), `createdAt`. Sem setters, construtor `Account(String email,
  String passwordHash)` — mesmo estilo das entidades existentes
  (`ChildProfile`, `ConsentRecord`).
- `AccountRepository extends JpaRepository<Account, Long>` com
  `findByEmail(String email)`.

`ChildProfile` (`br.com.ponte.profile`) ganha `accountId` (`Long`,
`not null`) — FK por id simples, mesmo padrão já usado em
`ConsentRecord.childProfileId` (o projeto não usa relações `@ManyToOne`).
Construtor passa a ser `ChildProfile(String displayName, Long accountId)`.

## Backend: autenticação

- Dependências novas: `spring-boot-starter-security` (compile) e
  `spring-security-test` (test).
- `br.com.ponte.account.AccountPrincipal implements UserDetails` — envolve
  `Account`; `getUsername()` = email, `getPassword()` = hash,
  `getAuthorities()` vazio (papel único, sem RBAC nesta fatia).
- `br.com.ponte.account.AccountUserDetailsService implements
  UserDetailsService` — carrega via `AccountRepository.findByEmail`.
- `br.com.ponte.config.SecurityConfig`: `BCryptPasswordEncoder` como
  `PasswordEncoder`; `SecurityFilterChain` com:
  - `/api/v1/auth/register` e `/api/v1/auth/login` → `permitAll()`
  - `/api/v1/**` (resto) → `authenticated()`
  - tudo mais (`/`, `/index.html`, `/login.html`, `/uploads/**`, assets
    estáticos) → `permitAll()` — a UI decide o que mostrar reagindo ao
    status da API (mesmo padrão já usado pelo gate de consentimento), não
    por redirect no servidor.
  - `formLogin` e `httpBasic` desabilitados; `AuthenticationEntryPoint`
    customizado responde `401` puro (sem página de login do Spring) quando
    uma rota autenticada é acessada sem sessão.
  - **CSRF desabilitado nesta fatia** — decisão explícita, não descuido:
    não há CORS configurado (`WebConfig` não define nenhum), então não há
    superfície cross-origin; todo fetch do frontend é same-origin. Revisar
    se/quando o app Android nativo (item futuro do roadmap) ou qualquer
    cliente cross-origin for adicionado.
- `br.com.ponte.account.AuthController` (`/api/v1/auth`):
  - `POST /register` `{email, password}` → valida (`@Email`, `@NotBlank`,
    senha `@Size(min=8)`), rejeita e-mail já usado com
    `IllegalArgumentException` ("E-mail já cadastrado.", já mapeado para
    `400` pelo `ApiExceptionHandler` existente), cria a `Account` com senha
    hasheada, autentica a sessão via `request.login(email, password)` e
    responde `201`.
  - `POST /login` `{email, password}` → `request.login(...)`; falha vira
    `InvalidCredentialsException` (nova, mapeada para `401`); sucesso
    `200`.
  - `POST /logout` → `request.logout()`, `204`. Requer sessão (não está na
    lista `permitAll`).
  - `GET /me` → `200 {email}` do principal autenticado, ou `401` (via
    filtro) se não há sessão. Usado pela UI para mostrar/reagir ao estado
    de login quando necessário (ex.: futura exibição do e-mail logado).

## Backend: ownership

- `ChildProfileRepository` ganha `findByAccountId(Long accountId)` e
  `existsByIdAndAccountId(Long id, Long accountId)`.
- `br.com.ponte.profile.ChildOwnershipGuard` (`@Component`): método
  `requireOwned(Long childId, Long accountId)` — lança
  `ChildProfileNotFoundException` (nova, mapeada para `404`) se o perfil
  não existe **ou** pertence a outra conta. As duas situações colapsam no
  mesmo `404` de propósito, para não revelar a existência de perfis de
  outras contas por diferença de status code.
- Checagem sempre no **controller**, logo após resolver
  `@AuthenticationPrincipal AccountPrincipal principal`, antes de delegar
  para o service — os services (`SymbolService`, `UsageService`) continuam
  sem conhecer conta nenhuma; assinaturas e testes de service não mudam.
- Controllers afetados:
  - `ProfileController.list(...)`: troca `findAll()` por
    `findByAccountId(principal.getAccountId())`.
  - `SymbolController.board(...)` e `.addCustom(...)`: guard antes de
    delegar para `SymbolService`.
  - `UsageController.record(...)` e `.summary(...)`: guard sobre o
    `childId` (do corpo ou query param).
  - `PredictionController.predict(...)`: guard sobre
    `request.childId()`. **Não** verifica se cada `symbolId` da lista
    pertence a esse `childId` — fica de fora desta fatia (ver "Fora de
    escopo").
  - `ConsentController.grant(...)` e `.revoke(...)`: guard sobre
    `profileId`.

## Frontend

- `login.html` + `login.js` novos: um formulário (e-mail + senha) que
  chama `POST /api/v1/auth/login`; um link/toggle muda o modo do mesmo
  formulário para "criar conta", chamando `POST /api/v1/auth/register` em
  vez de login. Sucesso → `location.href = '/'`. Erro → texto inline na
  própria página (mesma linguagem visual do erro inline já usado no gate
  de consentimento).
- `app.js` e `dashboard.js`: ambos já fazem `fetch('/api/v1/profiles')`
  como primeira coisa em `init()`. Passa a checar
  `if (res.status === 401) { location.href = '/login.html'; return; }`
  antes do tratamento de erro genérico existente.
- Botão "sair" (ghost, no topbar de `index.html` e `dashboard.html`):
  `POST /api/v1/auth/logout` seguido de `location.href = '/login.html'`.
- `sw.js`: `login.html` e `login.js` entram no array `SHELL`; `CACHE`
  bump (convenção já documentada).

## Seed e impacto nos testes existentes

- `DataSeeder` cria uma `Account` demo (`demo@ponte.app` / `ponte1234`,
  documentados no "Como rodar" do README) dona do perfil "Alex" — mantém o
  `mvn spring-boot:run` funcionando sem configuração, só exigindo login
  com essas credenciais.
- **Toda** a suíte `MockMvc` atual (`CustomSymbolApiTest`,
  `ProfileConsentApiTest`, `UsageApiTest`, `SymbolApiTest`,
  `PredictionApiTest`) chama a API hoje sem autenticação — vão passar a
  receber `401`. Cada teste precisa: criar/reusar uma `Account`, garantir
  que o `ChildProfile` usado no teste pertence a essa conta, e autenticar
  a requisição `MockMvc` como essa conta via
  `SecurityMockMvcRequestPostProcessors.user(new AccountPrincipal(account))`
  (não `@WithMockUser` — a checagem de ownership precisa de um id de
  conta real no banco). Um helper de teste compartilhado evita repetir
  isso em cada arquivo.
- Nova classe de teste `br.com.ponte.account.AuthApiTest` cobrindo
  register/login/logout/me e o caso central novo: conta B tentando
  acessar o perfil de conta A recebe `404`.

## Fora de escopo (explícito)

- Cadastro de múltiplos perfis de criança por conta (tela de gestão de
  perfis) — a fatia atual só liga a conta ao perfil único já existente.
- Papéis diferenciados (responsável vs. terapeuta) — conta única com
  acesso total ao(s) perfil(is) que possui.
- Verificação de e-mail e recuperação de senha.
- Ownership por `symbolId` individual na predição (só o `childId` do
  pedido é checado).
- Autenticação por token (JWT) para um futuro cliente nativo — sessão por
  cookie é suficiente para o app web/PWA atual; revisar quando/se o app
  Android nativo do roadmap for iniciado.
- Autenticação em `/uploads/**` — nomes de arquivo já são UUIDs
  imprevisíveis; mantém o comportamento atual.

## Verificação

- `mvn test` com a suíte inteira migrada para autenticar antes de cada
  chamada, mais os novos testes de `AuthApiTest` e de rejeição
  cross-account (`404`).
- Manual no navegador: registrar conta nova, logar, deslogar, tentar
  acessar `/` e `/dashboard.html` sem sessão (deve cair em
  `login.html`), confirmar que a conta demo do seed continua acessando
  "Alex" após login.
