# PERFIL PROFISSIONAL — PROGRESSO VIVO

> **ESTADO FINAL (2026-10-20): plano CONCLUÍDO e publicado na `main` dos dois
> repositórios (backend `20d8094`, frontend `f6a921d`). Não existe próxima tarefa:
> nenhuma execução automática deve alterar código, testes ou documentos deste plano.**
> Só resta a pergunta aberta à dona (formato/rota já resolvidos; visibilidade do feed
> corporativo) e os itens externos de infraestrutura (CORS do bucket S3, CDN/ACM,
> aprovação do produto de publicação no LinkedIn), que não são código deste repositório.

> Checklist de execução. Marcar com data + resumo do que foi feito + onde (arquivos).
> Ao concluir uma tarefa, atualizar este arquivo NO MESMO commit.

## Fase 0 — Auditoria e documentação

- [x] 2026-10-20 — Auditoria do estado (plan §Auditoria): perfil único, posts com PostType,
  feeds mapeados, jobs/search/notifications com outbox, migrações até V106 (+V102/104/105 de
  outra sessão). Documentos criados: plan, progress (este), decisions, validation.
- [x] 2026-10-25 — Decisões do `decisions.md` conferidas com o código: **username com ponto e
  rota pública já foram resolvidos na Fase 10** (o próprio `decisions.md` marca a pergunta 1 como
  resolvida: a rota pública é `/profissional/{username}` e o formato do @ profissional foi
  decidido). Conferido no código: a rota `profissional_.$username.tsx` existe no front, e o backend
  atende `GET /corporate/{username}` e `GET /corporate/{profileUsername}/posts` (o feed
  corporativo). **Fica só a confirmação de gosto** da visibilidade do feed corporativo (público
  para todos ou só para seguidores) — o plano já tem o padrão definido (público) e a pergunta não
  bloqueia nada.

## Fase 1 — Modelo de domínio

- [x] `PostType.BUSINESS` no enum do feed.
- [x] `CorporateProfile` (domain): campos, estados NOT_CREATED/DRAFT/ACTIVE/DISABLED,
  repositório (port) e casos (criar/editar/desativar/buscar por user e por username).
- [x] Identidade de autoria em `Post` (domain): `authorIdentity` SOCIAL|CORPORATE,
  default SOCIAL; invariante testada (nunca nula; edição preserva).

- [x] Concluída (commit `fa3c193`): 12 testes verdes — CorporateProfileTests (5),
  PostAuthorIdentityTests (4: identidade nunca nula no create/restore, edição e
  mudança de tipo preservam), mais a integração da cadeia de identidade.
- [x] Sobrecarga do `Post.restore` sem identidade (commit `0b4db2c`): toda chamada
  antiga (15 argumentos, ex. MomentExpiryTests) volta como SOCIAL — compila e
  preserva o comportamento.

## Fase 2 — Persistência e migração (V107+)

- [x] Migração idempotente: tabela `corporate_profiles` (1:1 opcional, user_id único),
  coluna `author_identity` em `posts` (default SOCIAL, not null), índices (V107).
- [x] Validação na bancada MariaDB (aplicada 2x sem erro; insert legado → SOCIAL,
  update → CORPORATE; ver `validation.md`). Compatibilidade comprovada: nenhum
  registro existente muda de mãos.
- [x] Adaptador JPA real (Spring Data + mapeamento entidade↔domínio) implementado;
  `CorporateProfilePersistenceTests` (3 cenários) verdes contra H2 com a migração
  V107 aplicada: guardar, buscar por user, e unicidade do @ profissional tratada
  no adaptador (409 USERNAME_TAKEN).

## Fase 3 — Serviço do perfil profissional

- [x] Serviço + controller (/me/business-profile): criar (1:1, um por conta),
  editar detalhes, renomear, ativar (exige headline), desativar. Username em
  namespace próprio com unicidade no banco (409 amigável). (commit 1a10133)

- [x] 2026-10-20 — Username próprio com unicidade real no banco: `uk_corporate_profiles_username`
  (V107) + adaptador que traduz a violação em 409 `USERNAME_TAKEN` (corrida coberta por
  `saveAndFlush`); namespace independente do social. Provado por
  `CorporateProfilePersistenceTests` e `CorporateIdentitySecurityTests` (tentativa de
  registrar o @ de outra conta → 409).
- [x] 2026-10-20 — Criar/editar/desativar + regras de estados com testes H2 do serviço
  (ativação exige headline; DRAFT não publica nem tem página; desativar preserva posts).

## Fase 4 — Importação com consentimento

- [x] Helper "trazer do perfil social": prévia campo a campo + confirmação explícita;
  teste provando que NADA é copiado sem confirmação. `GET
  /me/business-profile/import-preview` só mostra (ajustado aos limites); `POST
  /me/business-profile/import` aplica SOMENTE os campos escolhidos; lista vazia
  recusada (nada em bloco); campo desconhecido recusado. 4 testes verdes.
  (commit do ciclo)

## Fase 5 — Publicação BUSINESS

- [x] Publicar BUSINESS exige perfil ativo da MESMA conta (resolvido pela sessão,
  nunca do payload); BUSINESS não entra em comunidade; edição não troca
  identidade (IDENDITY_TYPE_MISMATCH → corrigido para IDENTITY_TYPE_MISMATCH).
  (commit 1a10133)

- [x] 2026-10-20 — Validação no backend: só com perfil ACTIVE e autoria da própria conta;
  edição preserva tipo/autoria; testes de negação em `BusinessFeedSeparationTests`
  (DRAFT/alheio → 403 `NO_ACTIVE_CORPORATE_PROFILE`) e `CorporateIdentitySecurityTests`
  (payload não aponta para perfil alheio; 403 também sem perfil).

## Fase 6 — Feeds no backend

- [x] Tuto/Following/perfil social excluem CORPORATE; feed corporativo e página
  do perfil profissional (novas rotas) trazem apenas BUSINESS; cache social sem
  posts corporativos (distribuição filtrada). (commit 1a10133)

- [x] 2026-10-20 — `getFeed` exclui BUSINESS (teste com post BUSINESS + social na mesma
  conta: `BusinessFeedSeparationTests`).
- [x] 2026-10-20 — Feed corporativo novo + perfil profissional novo (só BUSINESS;
  paginação keyset preservada por `created_at < before`).
- [x] 2026-10-20 — Perfil social não mostra BUSINESS; tabela de separação cumprida e
  auditada superfície por superfície (Fase 8 + Fase F da extensão).

## Fase 7 — Vagas e candidaturas

- [x] Candidatura usa a IDENTIDADE PROFISSIONAL quando ela existe e está ATIVA: nome,
  headline, local, avatar e indústria vêm do alterego; reputação, experiência,
  habilidades, currículo e verificação são da conta (iguais para as duas identidades).
  Sem perfil ativo, o retrato continua social — vagas funcionam para todo mundo.
  O retrato congelado na candidatura registra `identity` (SOCIAL|CORPORATE) e a
  indústria. `GET /jobs/{id}/applications` expõe candidate.identity/industry.
  2 testes verdes (com e sem perfil profissional).

## Fase 8 — Busca, notificações, salvos, trending, shares

- [x] Auditoria endpoint por endpoint (2026-10-20):

| superfície | regra | estado |
|---|---|---|
| Feed Tudo | BUSINESS nunca | query exclui BLOG+BUSINESS (testado) |
| Para você / Seguindo | só SOCIAL | query `authorIdentity = SOCIAL` (testado) |
| Perfil social | só SOCIAL | query social (testado) |
| Feed corporativo / página do perfil profissional | só CORPORATE | queries dedicadas (testado) |
| Abas Discussões/Recentes/Projetos | filtro por tipo (QUESTION/PROJECT) | BUSINESS não casa por tipo (testado) |
| Comunidades | BUSINESS não nasce nelas | recusa no publish + consulta por comunidade |
| Busca (`/search`) | não indexa posts (só users/communities/jobs/events/mentors) | sem vazamento por construção |
| Salvos | escolha explícita do usuário | BUSINESS salvo aparece (correto: acesso explícito) |
| Notificações | apontam para o post único (acesso explícito) | só autor/participantes recebem |
| Compartilhamento | link direto do post | acesso explícito, sem prévia em feed |
| Cache do feed social | não recebe CORPORATE | distribuição filtra (testado) |

## Fase 9 — Frontend: alternância e identidade ativa

- [x] Seletor social/profissional no menu da conta (só com perfil existente; opção
  profissional desabilitada sem ACTIVE) + atalho "Perfil profissional" + estado
  persistido no aparelho com fallback seguro (effectiveIdentity nunca devolve
  CORPORATE sem perfil ativo). A escolha é conveniência de UI; o backend decide a
  autoria de cada publicação. (commit no front)

## Fase 10 — Frontend: editor do perfil profissional

- [x] Página /profissional: criar (@ + nome), editar (headline, bio, local, indústria,
  links), ativar/desativar, e importação do perfil social com checkbox por campo
  (só os marcados viajam; a prévia não grava nada).
- [x] 2026-10-20 — Upload de avatar/banner próprios do perfil profissional: dois botões
  (Trocar foto / Trocar capa) com `useMediaUpload` (mesmo caminho S3 do resto do app);
  o PATCH manda o perfil inteiro porque ele substitui todos os campos (evita apagar a
  outra imagem ou os textos ao trocar uma foto).
- [x] 2026-10-20 — Rota pública do perfil profissional em `/profissional/{username}`
  (arquivo `profissional_.$username.tsx`, irmã do editor — decisão 6 em decisions.md):
  capa, avatar, headline, bio, local, indústria, links e SÓ as publicações BUSINESS
  daquele @, com paginação por cursor; perfil DRAFT/DISABLED/inexistente mostra estado
  vazio (o backend responde 404).

## Fase 11 — Frontend: compositor e feeds

- [x] 2026-10-20 — Opção BUSINESS no compositor com indicação clara de destino (banner
  "Publicando como {nome} — esta publicação vai para o feed corporativo"); feed
  corporativo como aba "Corporativo" (só com perfil ATIVO) e troca automática de
  identidade ativa no compositor. (commit no front)
- [x] 2026-10-20 — Assinatura por identidade (corrige vazamento de apresentação): o
  backend agora manda `authorIdentity` no PostView e enriquece posts CORPORATE com
  nome, @ e avatar do PERFIL PROFISSIONAL (uma consulta em lote por página); posts
  SOCIAL mantêm a pessoa. No front, o nome do autor leva a `/profissional/{username}`
  quando CORPORATE e a `/perfil/{username}` quando SOCIAL. Teste: o post BUSINESS da
  mesma conta aparece no feed corporativo com "Autor Profissional"/"bizsep.pro" e no
  feed social o post social continua SOCIAL.

## Fase 12 — Segurança

- [x] 2026-10-20 — Revisão de autorização + testes de negação
  (`CorporateIdentitySecurityTests`, 5 verdes): endpoints /me/business-profile exigem
  sessão; perfil ativo de OUTRA conta não publica pela minha sessão (403) e o @ de
  outra conta não pode ser registrado (409 `USERNAME_TAKEN`); terceiros não editam nem
  apagam post BUSINESS (403 `POST_OWNER_MISMATCH`); DRAFT não tem página pública (404)
  nem publica; desativar para de publicar mas não apaga. Contrato corrigido:
  `CorporateProfileController` e `PersonalizationController` declaravam
  `@RequestHeader("X-Session-Token")` obrigatório e devolviam 400 "cabeçalho ausente"
  sem sessão; agora `required=false` + resolver → 401 `UNAUTHENTICATED`, como o resto
  da API (teste próprio em `PersonalizationSecurityTests`, 4 verdes). Diretriz 4 da dona
  também testada: criar/ativar o perfil profissional não altera reputação, seguidores
  nem conexões da conta, e a página profissional não carrega cópia desses valores
  (`sharedAccountValuesAreNotDuplicatedByTheCorporateIdentity`). 6/6 verdes.

## Fase 13 — Migração de usuários existentes

- [x] 2026-10-20 — Compatibilidade comprovada na bancada MariaDB (`enois_mig13`):
  3 posts legados inseridos sem a coluna; ao aplicar a V107 os 3 ficaram SOCIAL; um
  insert legado pós-migração continua funcionando e nasce SOCIAL. Nada é migrado
  obrigatoriamente: quem nunca criar perfil continua com `GET /me/business-profile` =
  `exists:false` e o feed/perfil social funcionando (testes de feed verdes).

## Fase 14 — Desempenho

- [x] 2026-10-20 — V109 `idx_posts_identity_status_created (author_identity, status,
  created_at)`. Bancada `enois_perf14` com 50.000 posts simulados (15.000 CORPORATE):
  antes da V109 o feed corporativo varria `idx_posts_created` (rows≈50001, Using
  where); depois usa o composto (rows≈26478, sem filesort). Perfil profissional usa
  `idx_posts_author_status_created` (rows≈100); feed social segue em
  `idx_posts_created`. Paginação keyset preservada (`created_at < before`).

## Fase 15 — Testes e critérios de aceite

- [x] 2026-10-20 — Suíte COMPLETA do backend verde, num worktree isolado do commit:
  `mvn test` → **575 testes, 0 falhas, 0 erros, 1 ignorado**, exit 0. Front:
  `npx vitest run` 101 verdes; `npm run test:eslint-rules` verde; `npx vite build` ok;
  `npx tsc --noEmit` mantém os 9 erros pré-existentes do baseline, nenhum nos arquivos
  do plano. Página pública também renderizada de verdade: com uma API mock, o Chrome
  headless mostrou nome, headline, bio e o post BUSINESS assinado pela identidade
  profissional.
- [x] 2026-10-20 — Bloqueios de suíte alheios ao plano corrigidos para o gate: a V102
  (`external_jobs.submitted_by`) estava sem commit embora o código commitado exigisse a
  coluna (9 erros em vagas externas); comunidades criadas em testes vazavam para o
  contrato de página vazia (H2 compartilhado); o log de e-mail mascara o destinatário
  ("***") e um teste filtrava por ele; e o teste de CAS usava `@Transactional` de teste,
  que escondia a conta recém-criada numa leitura por outra conexão.

## Fase 16 — Gate final

- [x] 2026-10-20 — Revisão cruzada dos commits do plano (68 arquivos no backend, 38 no
  front): nenhum segredo, `.env` ou arquivo temporário; nenhum teste desativado.
- [x] 2026-10-20 — Gate em worktree isolado: `enois/main` mesclado sem conflitos e a
  suíte completa rodada NO MERGE → **581 testes, 0 falhas, 0 erros, 1 ignorado,
  BUILD SUCCESS** (a linha do main já trazia os testes novos de jogos/mensagens).
- [x] 2026-10-20 — Frontend no gate: `origin/main` mesclado no worktree; conflitos em
  `texts.ts` e `link-previews.tsx` resolvidos preservando OS DOIS lados (as chaves da
  personalização/perfil profissional + as correções de GIF/pontuação do main);
  `npx vite build` ok, `npx vitest run` **105 verdes**, `tsc` no baseline de 9 erros.
- [x] 2026-10-20 — Push único para `main` nos dois repositórios (backend e frontend).
- [x] 2026-10-20 — Automação de 40 minutos removida.


---

# EXTENSÃO: PERSONALIZAÇÃO (progresso)

- [x] Fase A — Preferências no backend (commit 96b4c39): user_preferences ampliada
  (liked_topics, blocked_topics, disabled_modules; tema agora nullable); GET/PUT
  /me/preferences; validação item a item (taxonomia real = a das comunidades; selos
  ficam fora); conflito liked+blocked recusado; tema preservado ao atualizar listas.
  Testes: 5 verdes.
- [x] Fase B — Classificação de posts (commit 96b4c39): coluna topic em posts
  (V108), herdada do tópico da comunidade na criação; post solto nasce sem
  classificação (nunca bloqueado).
- [x] Fase C — Aplicação nos feeds (commit 96b4c39): Tudo e Para você/Following
  excluem os assuntos bloqueados na consulta (sem buracos de paginação);
  "O que quero ver" aumenta a prioridade no Para você (posts escolhidos primeiro,
  o resto segue cronológico — não é filtro). Teste: 1 verde (bloquear tira do
  feed do leitor, autor continua vendo, desbloquear traz de volta).
- [x] Fase D — Módulos desativáveis (frontend): navegação filtra pelos módulos
  desativados (fonte única /me/preferences); acesso direto a URL desativada mostra
  a tela explicativa com "Reativar esta área"; EVENTOS governa /acontecendo
  (dependência do plano); mapa módulo→rota testado (3 testes).
- [x] 2026-10-20 — Notificações respeitam os módulos desativados:
  `moduleForNotification`/`isNotificationHidden` mapeiam os tipos reais do backend para
  MESSAGES/EVENTS/JOBS/MENTORING/DAILY_GAMES/GROUPS (4 testes) e o sino filtra pela mesma
  fonte única (/me/preferences) — desativar um módulo não pode esconder o menu e deixar
  a notificação voltar por outro caminho. Cards de módulo fora da própria área não
  existem hoje no feed (as sugestões são de pessoas); navegação + gate + sino cobrem as
  superfícies reais.
- [x] Fase E — Interface das três seções em Uso saudável: "O que quero ver" e
  "Assuntos que não gosto" como chips da taxonomia real (marcar num lado tira do
  outro — o backend recusa o conflito), "O que não quero ver" como switches com a
  nota da dependência Eventos→Acontecendo agora; salvar grava a fonte única no
  backend e o lembrete local. (commit no front)
- [x] Fase F — Testes da seção 10.5 + auditoria das superfícies
  (`PersonalizationSecurityTests`, 3 verdes): a conta das preferências é sempre a da
  SESSÃO (um payload com `userId` alheio não altera a outra conta); desativar módulos
  não revoga acesso — `/conversations`, `/jobs`, `/communities` e `/events` seguem
  200, porque preferência é de experiência, não de autorização; assunto bloqueado
  não esconde o post por link direto (`GET /feed/posts/{id}` → 200), ou seja, não há
  censura global.
- [x] Fase F — Auditoria busca/notificações/salvos com as preferências
  (`PersonalizationSurfaceAuditTests`, 3 verdes): **salvos** seguem o que foi salvo
  (post de assunto bloqueado continua em `/feed/saved`, mesmo saindo do feed);
  **notificações** seguem a publicação, inclusive BUSINESS (comentário gera
  NEW_COMMENT com href `/api/feed/posts/{id}` — a identidade de autoria não interfere);
  **busca pública** lista usuários/comunidades/vagas/eventos/mentores e nunca posts,
  então BUSINESS não vaza por ela. Critérios 10.1–10.4 já estavam cobertos pelos
  testes das Fases A–C (preferências 5 verdes, aplicação nos feeds 1 verde).
