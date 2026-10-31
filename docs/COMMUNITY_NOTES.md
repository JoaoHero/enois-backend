# Nota de rodapé da comunidade

Correção/contexto enviado por outra pessoa a um post — o mecanismo do Énóis contra desinformação.
A comunidade se corrige; a moderação só confere antes de exibir.

## Como funciona

1. Quem **não é o autor do post** abre “Adicionar nota de rodapé” no card e escreve de 10 a 500
   caracteres (`POST /feed/posts/{postId}/notes`). A nota nasce `PENDING`.
2. A moderação vê a fila em `/admin/notes` e aprova ou nega (`PATCH /admin/notes/{id}`).
   Negar exige um motivo (o autor da nota vê o motivo).
3. Só a nota `APPROVED` aparece no rodapé do post, como “Nota da comunidade · Verificada pela
   comunidade”. A leitura é pública (`GET /feed/posts/{postId}/notes`).
4. **Uma nota aprovada por post**: ao aprovar a primeira, as demais pendentes do mesmo post viram
   `DISCARDED` automaticamente — sem penalidade — e o post deixa de aceitar novas notas
   (`409 NOTE_ALREADY_APPROVED`).
5. O autor vê a própria nota no card: “em análise”, “negada: motivo” ou “descartada” (quando outra
   foi aprovada).

## Credibilidade

- Enviar nota **aprovada** — ou que ainda está em análise — **não** altera a credibilidade:
  a comunidade se corrigindo é o objetivo.
- Enviar nota **negada** (informação falsa) reduz a credibilidade com a mesma escala usada para
  vaga abandonada: 25, depois 75, depois 150 (teto próprio), via `NoteCredibilityReader` no
  componente de integridade do `ReputationService`.
- Uma nota **aprovada** é um fact-check da comunidade e dá **+5 pontos** de credibilidade, contando
  no máximo 5 notas aprovadas (teto +25).
- Nota `PENDING` ou `DISCARDED` não altera a credibilidade.
- Uma nota por pessoa por post (`409 NOTE_ALREADY_EXISTS`); o autor do post não pode anotar o
  próprio post (`403 NOTE_ON_OWN_POST`).

## Onde vive

- `post_notes` (V190): id, post, autor, corpo, status, revisor, motivo e datas.
- Enriquecimento em lote no feed: `PostView.communityNotes` e `PostView.myNote` chegam na mesma
  resposta da listagem (sem request por post).
- A purga de conteúdo do usuário (ban/spam) apaga as notas do autor e as notas dos posts apagados.
