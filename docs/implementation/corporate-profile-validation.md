# PERFIL PROFISSIONAL — VALIDAÇÃO

> Registro das validações reais: comandos executados e resultados. Nada de "deveria passar".

## 2026-10-20 — Fase 0

- Auditoria documentada no plan (estudo do código real, não suposições).
- Documentos criados e **commitados** no `rede` (backend) — `32123181` ("docs(perfil-corporativo):
  plano de execucao em fases ... + progress/decisions/validation"). A pendência anotada aqui
  (commitar no próximo ciclo) ficou desatualizada: os documentos entraram no mesmo dia e o plano
  inteiro foi publicado depois.
- Automação de 40 minutos: CRIADA de fato — id automation-66c1edea-6784-4bdf-9f72-99cb2b08f06c,
  título "Implementar o plano do perfil profissional a cada 40 minutos", agenda real de 40 em 40
  minutos. Ela lê estes documentos e retoma o trabalho; remove a si mesma no gate final.
- Sem testes novos nesta fase (nada de código mudou ainda).

## 2026-10-20 — Fases 1 e 2

**Testes (worktree isolado, H2, migração V107 aplicada pelo Flyway):**

- `mvn test -Dtest='PostAuthorIdentityTests,CorporateProfileTests,CorporateProfilePersistenceTests'`
  → **12 testes, 0 falhas** (exit 0):
  - `PostAuthorIdentityTests` (4): publicação social é SOCIAL por padrão; BUSINESS é
    CORPORATE; identidade nula não existe (restore legado vira SOCIAL); editar
    conteúdo/tipo preserva a identidade.
  - `CorporateProfileTests` (5): nasce DRAFT sem nada copiado do social; formatos de @
    aceitos e recusados; ciclo DRAFT→ACTIVE→DISABLED→reabrir sem perder dados;
    renomear a apresentação não muda o @.
  - `CorporateProfilePersistenceTests` (3): guarda e busca por user/username; **a
    unicidade do @ profissional é do banco** (segunda conta com o mesmo @ → 409
    USERNAME_TAKEN); o namespace profissional não colide com nada do social.
- `MomentExpiryTests` exigia a assinatura antiga do restore: **sobrecarga adicionada**
  (commit `0b4db2c`) — toda chamada legada compila e volta SOCIAL.

**Bancada MariaDB 11.4 (docker enois-bench66, banco scratch `enois_corp`):**

- V107 aplicada **duas vezes** sem erro (idempotente): tabela `corporate_profiles`
  criada com índice único em `username`; coluna `author_identity varchar(20) NOT NULL
  DEFAULT 'SOCIAL'` adicionada em `posts` (com a tabela na forma real do V1+alterações).
- Insert legado sem informar identidade → gravado como **SOCIAL**; update explícito →
  **CORPORATE** lido de volta. Banco scratch descartado após a validação.
- Compatibilidade comprovada: nenhum registro existente muda de mãos (default SOCIAL).


## 2026-10-20 — Fases 3–6 (serviço, BUSINESS, separação de feeds)

**Testes (árvore compartilhada e worktree isolado):**

- `BusinessFeedSeparationTests` (3, verdes): a mesma conta publica um social e um
  BUSINESS — o social aparece em Tuto/Following/perfil social, o BUSINESS não; o
  BUSINESS existe apenas no feed corporativo e na página do perfil profissional;
  BUSINESS sem perfil ativo → 403 NO_ACTIVE_CORPORATE_PROFILE; editar não troca
  identidade (400 IDENTITY_TYPE_MISMATCH); perfil inativo não tem página (404).
- `PostAuthorIdentityTests` (4) e `CorporateProfileTests` (5): verdes (fase 1).
- **Suíte completa no worktree isolado (commit 6472cbe): 346 testes, 2 falhas —
  ambas em módulos de OUTRAS sessões** (media LazyMigration e SupportCategories,
  trabalho em voo no repositório), nenhuma no escopo deste plano. Módulos deste
  plano (feed, corporate, linkedin share) 100% verdes.

**Correções feitas durante a validação:**

- As queries "sociais" do repositório estavam invertidas (retornavam o que NÃO
  era social); corrigidas para `authorIdentity = :social` (commit 6472cbe).
- `FeedDistributionService`: post CORPORATE não entra no fanout do cache social.
- `FeedCorporateController` + rotas `/feed/corporate` e `/corporate/{username}/posts`.
- `CorporateProfileController` (endpoints /me/business-profile) com a regra de
  ativação exigindo headline.


## 2026-10-20 — Extensão: personalização (Fases A, B, C)

- `PersonalizationTests` (5 verdes): defaults vazios + persistência; valores
  inválidos recusados (taxonomia e módulos); conflito liked+blocked → 409;
  limpar volta ao padrão; tema preservado na mesma linha.
- `PersonalizationFeedTests` (1 verde, ponta a ponta): comunidade de tecnologia
  → post herda o assunto → leitor bloqueia → post some do feed DO LEITOR →
  autor (sem bloqueio) continua vendo → desbloquear traz de volta.
- Correções: V108 dividida em instruções únicas (H2); tema da conta virou
  nullable; grafia TECNOLOGIA (a taxonomia é em português).


## 2026-10-20 — Fase 4 (importação com consentimento)

- `CorporateImportConsentTests` (4 verdes): a prévia mostra headline/bio do social SEM
  gravar nada; importar só "headline" traz só ela (bio continua vazia, nome
  profissional preservado); seleção vazia → 400 NO_FIELDS_SELECTED; campo desconhecido
  → 400 INVALID_IMPORT_FIELD.
- Correção durante a validação: usernames únicos por teste (o H2 da classe é o mesmo —
  username repetido entre testes dava 409).


## 2026-10-20 — Fase 7 (vagas/candidaturas)

- `JobCorporateApplicationTests` (2 verdes): candidato com perfil profissional ATIVO →
  retrato congelado identity=CORPORATE, nome/headline/indústria do alterego; candidato
  sem perfil → identity=SOCIAL, nome social. `JobServiceTests` segue 20/20 (o fake de
  repositório corporativo vazio foi adicionado ao construtor do serviço).
- Correções: reputação mockada (>150) como nos testes de vaga existentes; o apply
  responde 201 (não 200).


## 2026-10-20 — Fase 8 (auditoria busca/notificações/salvos)

- Auditoria endpoint por endpoint registrada no progress (tabela). Achados: a busca não
  indexa posts (sem vazamento por construção); salvos e link direto são acesso explícito
  (corretos); abas por tipo não casam com BUSINESS.
- `BusinessFeedSeparationTests` ampliado: as abas DISCUSSIONS e PROJECTS também provam
  ausência do post BUSINESS (3/3 verdes).


## 2026-10-20 — Fases 9/10 (frontend do perfil profissional)

- `npx eslint` nos arquivos tocados: 0; `tsc --noEmit`: 9 erros (baseline
  pré-existente, nenhum nos arquivos do plano); `npx vitest run`: 95 testes verdes
  (os 2 "arquivos falhos" são as regras de ESLint, pré-existentes).
- Página /profissional e seletor de identidade no menu implementados; commit local
  no front (push bloqueado pelo plano).


## 2026-10-20 — Fase 11 (compositor e feed corporativo)

- `active-identity.test.ts` (3 verdes): efetiva é CORPORATE só com perfil ATIVO;
  sem perfil/DRAFT/DISABLED → SOCIAL; escolha social continua social.
- `npx vitest run`: 98 verdes; `tsc` no baseline (nenhum erro nos arquivos do plano);
  eslint 0 nos arquivos tocados (3 erros de prettier pré-existentes em linhas de
  outras sessões no feed.tsx, fora dos meus hunks).


## 2026-10-20 — Extensão Fase D (módulos desativáveis, frontend)

- `module-paths.test.ts` (3 verdes): mapa área→módulo, incluindo /acontecendo→EVENTS;
  rotas fora de módulos desativáveis ficam livres (/feed, /perfil).
- `npx vitest run`: 101 verdes; eslint 0 nos arquivos tocados; tsc no baseline.


## 2026-10-20 — Extensão Fase E (interface das três seções)

- Diálogo de Uso saudável com as três seções; eslint 0; `tsc` no baseline;
  `npx vitest run`: 101 verdes. O conflito liked×blocked é impedido na própria tela
  (o backend continua validando e recusando com 409).


## 2026-10-20 — Extensão Fase F (segurança e superfícies)

- `mvn -q test -Dtest='PersonalizationSecurityTests'`: **3/3 verdes** (39,6 s)
  1. preferência não é alterável para outra conta: payload com `userId` alheio é
     ignorado; a vítima mantém `likedTopics=[TECNOLOGIA]` e nenhum bloqueio;
  2. desativar MESSAGES/JOBS/COMMUNITIES/EVENTS na preferência não bloqueia a API:
     `GET /conversations`, `/jobs`, `/communities`, `/events` respondem 200;
  3. post com assunto bloqueado continua acessível por `GET /feed/posts/{id}` → 200
     (o filtro vale para o feed, não é censura do conteúdo).
- `mvn -q test -Dtest='PersonalizationSurfaceAuditTests'`: **3/3 verdes** (62,6 s)
  1. salvos seguem o que foi salvo: post de TECNOLOGIA salvo antes do bloqueio sai
     do `GET /feed?tab=ALL` do leitor e permanece em `GET /feed/saved`;
  2. notificação segue a publicação BUSINESS: comentário de outra conta gera
     NEW_COMMENT para a autora com href `/api/feed/posts/{id}` (worker da outbox
     aguardado na própria asserção);
  3. busca pública não devolve posts (só usuários/comunidades/vagas/eventos/
     mentores), logo não há vazamento de BUSINESS pela busca.
- Primeira execução da auditoria: 2/3 — o teste de busca publicava BUSINESS sem
  ativar o perfil (403 `NO_ACTIVE_CORPORATE_PROFILE`). Corrigido no teste (patch de
  headline + activate, o fluxo real) e reexecutado: 3/3. Nenhuma mudança de produção
  foi necessária: as três superfícies já cumpriam a tabela de separação.


## 2026-10-20 — Fase 12 (segurança da identidade profissional)

- `mvn -q test -Dtest='CorporateIdentitySecurityTests'`: **5/5 verdes**
  1. /me/business-profile (GET/POST/activate) sem sessão → 401 `UNAUTHENTICATED`;
  2. perfil ATIVO de outra conta não publica pela minha sessão → 403
     `NO_ACTIVE_CORPORATE_PROFILE` (mesmo mandando `username`/`authorUsername` no payload);
     registrar o @ alheio → 409 `USERNAME_TAKEN`;
  3. terceiros não editam nem apagam post BUSINESS → 403 `POST_OWNER_MISMATCH` (e o post
     segue no ar para a dona);
  4. perfil DRAFT não tem página pública (404 `PROFILE_NOT_FOUND`) nem publica BUSINESS;
  5. desativar para de publicar (403) mas não apaga os posts existentes (200 por link).
- Correção de contrato encontrada por esses testes: `CorporateProfileController` e
  `PersonalizationController` usavam `@RequestHeader("X-Session-Token")` obrigatório, então
  requisição sem sessão devolvia 400 "cabeçalho ausente" em vez de 401 `UNAUTHENTICATED`.
  Ambos passaram a `required=false` + `currentUserResolver.resolve` (o padrão do resto da
  API). `PersonalizationSecurityTests` ganhou o caso sem sessão: **4/4 verdes** na
  reexecução conjunta (também aplicou a V109 no H2 sem quebra).


## 2026-10-20 — Fase 13 (compatibilidade com dados existentes)

- Bancada MariaDB (`enois-bench66`, banco scratch `enois_mig13`): tabela `posts` no formato
  antigo (sem `author_identity`), 3 posts legados inseridos; V107 aplicada → os 3 ficaram
  `SOCIAL`; insert legado depois da migração continua funcionando e nasce `SOCIAL`
  (`select count(*) = sum(author_identity='SOCIAL')` → 4/4). Nenhuma migração de usuários é
  obrigatória: o perfil profissional é opt-in.


## 2026-10-20 — Fase 14 (desempenho dos feeds novos)

- Bancada MariaDB (`enois_perf14`): 50.000 posts simulados (15.000 CORPORATE, 1/4 com tópico).
- **Antes da V109**: `EXPLAIN` do feed corporativo (`status='ACTIVE' AND
  author_identity='CORPORATE' ORDER BY created_at DESC LIMIT 20`) → `key=idx_posts_created`,
  `rows≈50001`, `Using where` (varre a tabela inteira e descarta 70%).
- **Depois da V109** (`idx_posts_identity_status_created`): `key=idx_posts_identity_status_created`,
  `rows≈26478`, sem filesort. Perfil profissional: `key=idx_posts_author_status_created`,
  `rows≈100`. Feed social (exclui BLOG/BUSINESS): `key=idx_posts_created`, como antes.
- Migração V109 aplicada no H2 de teste na rodada conjunta (9/9 verdes) e no MariaDB da
  bancada sem erro (`create index if not exists`).
- Diretriz 4 (valores da conta compartilhados) em `sharedAccountValuesAreNotDuplicatedByTheCorporateIdentity`:
  reputação/seguidores/conexões de `GET /me` são idênticos antes e depois de criar+ativar o
  perfil profissional; `GET /corporate/{username}` não expõe `reputation`, `followersCount`,
  `connectionsCount` nem `email` (esses valores vivem na conta, não na apresentação).
  Reexecução da classe: **6/6 verdes** (118,8 s, V109 aplicada no H2).


## 2026-10-20 — Assinatura por identidade (correção de apresentação)

- `BusinessFeedSeparationTests` ampliado: **3/3 verdes**. O post BUSINESS aparece no feed
  corporativo e em `/corporate/{username}/posts` com `authorName = "Autor Profissional"`,
  `authorUsername = "bizsep.pro"` e `authorIdentity = "CORPORATE"`; o post SOCIAL da MESMA
  conta continua `SOCIAL` no feed Tudo (nome pessoal).
- `PostView` ganhou `authorIdentity`; o enriquecimento resolve a apresentação CORPORATE com
  UMA consulta em lote por página (`CorporateProfileRepository.findByUserIds`) — o fake do
  port em `JobServiceTests` ganhou o método (20/20 verdes).
- Higiene do H2 compartilhado entre classes: `PersonalizationFeedTests`,
  `PersonalizationSecurityTests` e `PersonalizationSurfaceAuditTests` apagam as comunidades
  que criam (`@AfterEach`) e `GroupCommunityIsolationTests` apaga a sua no fim. Sem isso as
  comunidades vazavam para `ContractControllerTests`, que exige a listagem global vazia
  (era exatamente o que a execução completa pegou). Rodada conjunta das classes de
  personalização + feed BUSINESS: **16/16 verdes**.


## 2026-10-20 — Fase 10 restante + autoria no front (frontend)

- Rota pública `/profissional/{username}` em `profissional_.$username.tsx` (irmã do editor;
  o routeTree foi regenerado pelo `vite build` com id `/profissional_/$username` e
  `fullPath /profissional/$username`): capa, avatar, headline, bio, local, indústria,
  links e só as publicações BUSINESS do @, com paginação por cursor e estados de
  carregamento/404.
- Editor `/profissional` ganhou upload de avatar e capa (`useMediaUpload`, mesmo caminho
  S3 do resto do app). O PATCH manda o perfil inteiro: o serviço substitui todos os
  campos, então trocar uma imagem sem reenviar os textos (ou a outra imagem) apagaria o
  resto — o mesmo tipo de armadilha do PUT de vaga.
- `AuthorInfo` linka pela identidade (`authorIdentity`): CORPORATE → `/profissional/{username}`,
  SOCIAL → `/perfil/{username}`.
- Verificação: `npx vite build` ok (16,58 s); `npx tsc --noEmit` mantém os 9 erros do
  baseline, nenhum nos arquivos tocados; eslint 0 nos arquivos novos/corrigidos;
  `npx vitest run`: 101 verdes (os 2 "arquivos falhos" são as regras de ESLint
  pré-existentes).


## 2026-10-20 — Notificações e módulos desativados (frontend)

- `notification-module.test.ts`: **4/4 verdes** — tipos do backend (MAIÚSCULAS) mapeados
  para MESSAGES/EVENTS/JOBS/MENTORING/DAILY_GAMES; prefixos `GROUP_`/`NEW_GROUP_` →
  GROUPS; comentário/rede ficam sempre visíveis; o filtro só esconde o módulo desativado.
- O sino (`NotificationsPopover`) usa a mesma fonte única (`useDisabledModules`, de
  /me/preferences) para filtrar a lista.


## 2026-10-20 — Fase 15 (suíte completa e aceite)

- Backend, worktree isolado do commit (`/tmp/rede-full`, HEAD `2b06f8f` (com os dois commits
  de estabilização): `mvn test` → **Tests run: 575, Failures: 0, Errors: 0, Skipped: 1**,
  exit 0. Nenhum teste do plano foi desativado.
- Front: `npx vitest run` → 101 testes verdes; `npm run test:eslint-rules` → verde (os
  dois "arquivos falhos" do vitest são as regras de ESLint, que têm runner próprio);
  `npx vite build` → ok (16,58 s); `npx tsc --noEmit` → os mesmos 9 erros pré-existentes
  do baseline, nenhum nos arquivos do plano.
- Verificação de tela da rota pública: API mock (perfil + um post BUSINESS) + `vite dev`
  + Chrome headless `--dump-dom` → o DOM trouxe "Ana Profissional", a headline, a bio e o
  texto do post com `authorIdentity: CORPORATE`. Não houve screenshot: o Chrome deste
  host travou no modo screenshot; a prova é o DOM renderizado com os dados.
- Bloqueios alheios ao plano corrigidos para o gate: V102 sem commit (coluna
  `external_jobs.submitted_by` exigida pelo código commitado); `GroupCommunityIsolationTests`
  e os testes de personalização deixavam comunidades no H2 compartilhado;
  `SupportCategoriesTests` filtrava por `toEmail`, que o painel mascara como "***";
  `LazyMigrationPersistenceTests` dependia de `@Transactional` de teste para ver a conta
  criada por outra conexão (agora o update direto usa `TransactionTemplate`).


## 2026-10-20 — Fase 16 (gate final e push único)

- Gate do backend em worktree isolado, JÁ COM `enois/main` mesclado (nenhum conflito):
  `mvn test` → **Tests run: 581, Failures: 0, Errors: 0, Skipped: 1**, BUILD SUCCESS.
- Gate do frontend em worktree isolado, já com `origin/main` mesclado: conflitos em
  `src/texts.ts` (formatação + chaves novas dos dois lados) e em
  `src/components/post/link-previews.tsx` (a correção de pontuação final veio do main).
  Resolução preservou a união: as chaves de personalização/perfil profissional
  continuam presentes e a correção de GIF/pontuação do main entrou. Resultado:
  `vite build` ok, `vitest` **105 verdes**, `tsc` nos mesmos 9 erros de baseline.
- Push único (fast-forward) para `main` nos dois repositórios, a partir dos commits de
  merge do gate. Os repositórios de trabalho originais não foram reescritos: as sessões
  concorrentes seguem com as alterações não commitadas delas.
