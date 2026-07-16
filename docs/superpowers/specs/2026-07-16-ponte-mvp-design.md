# Ponte — Design do MVP

**Data:** 2026-07-16
**Status:** Aprovado

## Contexto do produto

Ponte é um app de Comunicação Alternativa e Aumentativa (CAA) para crianças
autistas não-verbais ou minimamente verbais (~270-330 mil crianças no Brasil).
Concorrente estabelecido: Livox (Brasil, 2009). O wedge do Ponte:

1. **Pictogramas personalizados por IA** a partir de foto real do universo da
   criança (brinquedo, pessoa, lugar) — reduz o tempo de configuração da prancha.
2. **Predição de frase por IA**: a partir de 2-3 toques, sugere a frase mais
   provável, reduzindo carga motora.
3. **Painel de evolução comunicativa** para terapeutas/escolas.

## Restrição de arquitetura: motor planning

A literatura de CAA mostra que mudar a posição de símbolos numa grade prejudica
o aprendizado motor da criança. **A IA (e qualquer outra parte do sistema)
apenas ADICIONA símbolos ao fim da grade, nunca reorganiza os existentes.**
Isso é requisito de arquitetura, não só de UX: `gridPosition` é append-only por
construção (atribuído na criação como `max+1`, sem setter, sem endpoint de
reposicionamento). Remoção futura será soft-hide mantendo a célula reservada.

## Plataforma

PWA (web) primeiro — validação rápida com famílias, sem loja de app, funciona
em qualquer tablet/navegador, offline em sala de aula. Se validar, migração
para Android nativo reutilizando o mesmo backend REST.

## Arquitetura

Monolito Spring Boot 3 (Java 17, Maven). PWA vanilla servido de
`src/main/resources/static/`. H2 em memória com seed automático (migração
fácil para Postgres depois). Um comando (`mvn spring-boot:run`) sobe tudo em
`localhost:8080`.

Pacotes por domínio sob `br.com.ponte`:

- `profile` — perfis de criança
- `consent` — consentimento LGPD
- `symbol` — símbolos da prancha
- `usage` — eventos de uso e resumo
- `prediction` — predição de frase (stub de IA)
- `picto` — geração de pictograma (stub de IA)

## Modelo de dados

- **ChildProfile** — `id`, `displayName` (apelido, sem nome completo —
  minimização de dados LGPD), `createdAt`. Seed com 1 perfil demo.
- **ConsentRecord** — `childProfileId`, `guardianName`, `purpose`,
  `grantedAt`, `revokedAt`. Registrar evento de uso **exige consentimento
  ativo** — regra LGPD nasce no domínio.
- **Symbol** — `id`, `label` (texto falado), `category` (COMIDA, SENTIMENTOS,
  PESSOAS, ACOES, PERSONALIZADO), `imageType` (EMOJI | UPLOAD | AI_GENERATED),
  `imageRef` (emoji ou caminho da imagem), `gridPosition` (append-only),
  `childProfileId` (null = símbolo padrão global).
- **UsageEvent** — `symbolId`, `childProfileId`, `timestamp`, `eventType`
  (SYMBOL_TAP, SENTENCE_SPOKEN, PREDICTION_ACCEPTED). Só IDs, sem texto livre.

Seed inicial: pequeno conjunto de símbolos padrão via emoji nas categorias
comida, sentimentos, pessoas e ações — sem dependência de API externa no MVP.

## API REST (`/api/v1`)

| Endpoint | Função |
|---|---|
| `GET /symbols?childId=` | Globais + personalizados, ordenados por `gridPosition` |
| `POST /symbols/custom` (multipart) | Foto + label + categoria → stub de pictograma → símbolo no fim da grade |
| `POST /usage-events` | Registra toque/frase/predição aceita (rejeita sem consentimento ativo) |
| `GET /usage/summary?childId=&date=` | Contagem por símbolo, totais — alimenta o dashboard |
| `POST /predictions` | Recebe símbolos da barra de frase → frases sugeridas |
| `POST /profiles/{id}/consent` | Registra consentimento do responsável |

## Stubs de IA

Interfaces limpas para plugar chaves de API depois, sem chamadas reais
inventadas:

- `PictogramGenerationService` — stub retorna a foto enviada recortada em
  quadrado. `TODO` + property `ponte.ai.pictogram.api-key`.
- `SentencePredictionService` — stub com templates por categoria ("Eu quero
  comer maçã", "Eu estou feliz"). `TODO` + property
  `ponte.ai.prediction.api-key`.
- Troca por implementação real via `@ConditionalOnProperty` — zero mudança no
  resto do código.

## Frontend PWA

- **Prancha** (`index.html`):
  - Barra de frase no topo: símbolos tocados em sequência, botões falar frase,
    apagar último, limpar.
  - Área de sugestão de predição: aparece com 2+ símbolos na barra; tocar fala
    a frase e registra PREDICTION_ACCEPTED.
  - Grade estável ordenada por `gridPosition`, alvos de toque grandes (≥80px),
    cor de fundo por categoria.
  - Toque em símbolo = fala via Web Speech API (pt-BR) + adiciona à barra +
    registra SYMBOL_TAP.
- **Adicionar símbolo**: botão discreto de cuidador → foto (câmera ou galeria
  via `<input capture>`), nome, categoria → POST → símbolo aparece no fim da
  grade.
- **Dashboard** (`dashboard.html`): top símbolos do dia em barras, total de
  toques, frases faladas.
- **Offline**: `manifest.json` + service worker cache-first para o shell e
  imagens; eventos de uso enfileirados em `localStorage` e reenviados quando
  voltar a conexão. TTS funciona offline com vozes locais do dispositivo.

## LGPD

- Minimização: apelido em vez de nome completo; eventos de uso só com IDs.
- Consentimento registrado antes de coletar uso (regra no domínio).
- Fotos armazenadas em diretório local no MVP; em produção, storage
  criptografado. Endpoint de exclusão de dados fica para pós-MVP, mas o modelo
  (perfil → consentimento → eventos) já permite exclusão em cascata.

## Testes

JUnit + MockMvc focados nos invariantes:

- `gridPosition` append-only e ordenação estável.
- Rejeição de evento de uso sem consentimento ativo.
- Resumo de uso correto por dia.
- Stub de predição retorna frases coerentes com as categorias.

## Marcos

1. **M1** — Backend rodando: entidades, seed de símbolos, `GET /symbols`.
2. **M2** — Prancha PWA: grade + TTS + barra de frase + registro de eventos.
3. **M3** — Foto → stub pictograma → símbolo novo na prancha.
4. **M4** — Predição de frase (stub) + UI de sugestão.
5. **M5** — Dashboard + manifest/service worker (PWA offline completo).

## Fora de escopo do MVP

- Autenticação/login (dashboard aberto no MVP; nota de que produção exige).
- Chamadas reais às APIs de IA (apenas stubs com TODO).
- Multi-prancha, multi-idioma, edição/remoção de símbolos.
- App Android nativo.
