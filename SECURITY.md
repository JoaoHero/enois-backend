# Política de Segurança do Énóis (backend)

> Uma falha de segurança **nunca** vira issue ou PR pública. Reporte pelo canal privado abaixo.
> O frontend tem a política equivalente em
> [`enois-frontend/SECURITY.md`](https://github.com/Rede-ENois/enois-frontend/blob/main/SECURITY.md).

## Como reportar (canal privado)

1. **E-mail — canal principal:** **seguranca@enois.ia.br** (se preferir, `suporte@enois.ia.br`
   encaminha para a mesma fila). Quem recebe é o time do Énóis, e o conteúdo não é publicado.
2. **O que incluir:**
   - **versão/commit** em que o problema aparece (`git log -1`, a imagem em produção, a versão do app);
   - **mínimo que reproduz**: requisição ou payload, rota e o que fazer;
   - **comportamento observado × esperado** (o que o servidor devolve, o que deveria devolver);
   - **impacto estimado** (execução remota, vazamento de dado de outra pessoa, bypass de
     autorização, negação de serviço, automação indevida da conta). Prova de conceito é bem-vinda;
     explorations já automatizadas e massivas, não.
3. **Não** abra issue pública, PR público nem comente em issue aberta antes da correção
   combinada — isso expõe quem usa a plataforma.

**Não** inclua em reportes, issues, prints ou PRs: dados pessoais de usuários, credenciais, tokens,
segredos de sessão ou detalhes exploráveis em público.

## Escopo

**Em escopo** (este repositório, GPLv3):

- A API pública (`/api`): autenticação, autorização, isolamento de dados entre usuários e
  aplicações, idempotência, limites e validação, carteira (Faísca) e escrow, contratos.
- A moderação e o que ela decide: sanções, denúncias e a política de publicações.
- As integrações oficiais (LinkedIn, OAuth, importadores) e o portal de desenvolvedores.
- Migrações e o esquema do banco, insofar que permitam acesso indevido ou perda de dados.
- Dependências declaradas (`pom.xml`) e as imagens usadas no CI.

**Fora de escopo:** engenharia social, phishing contra o time, DDoS/ataques de volume, a
infraestrutura de quem usa o Énóis, programas escritos por terceiros dentro da plataforma, e
qualquer associação com marcas de terceiros.

## Compromisso de resposta (open source, melhor esforço)

- **Triagem em até 5 dias úteis**, pelo time do Énóis.
- Gravidade pelo impacto real: execução remota > bypass de autorização > vazamento de segredo ou de
  dado de outra pessoa > negação de serviço > endurecimento.
- A correção vai na branch ativa **com teste de regressão no mesmo commit** (é o portão de qualidade
  do repositório) e a publicação do aviso acompanha a versão que traz a correção.
- **Divulgação coordenada:** pedimos **até 90 dias** entre o reporte privado e a divulgação
  pública, proporcionais ao risco. Damos crédito a quem reportar, salvo se preferir o anonimato.
- Relatórios de automação abusiva (bots, spam, automação de interação social) também entram por
  aqui: eles viram ocorrência na moderação, com creditação interna, e não banimento automático.

## Safe harbor

Pesquisa de boa-fé neste repositório é bem-vinda: não tomamos ação contra quem segue esta política
(escopo respeitado, sem extrair dado de terceiro, sem degradar o serviço, sem divulgar antes da
correção). Teste automatizado agressivo contra `enois.ia.br` fora do seu próprio fork/clone não é
pesquisa — é abuso.

## O que já está em vigor neste repositório

- **Negar por padrão**: só o que tem autorização explícita é exposto.
- **Autorização no backend** em toda operação protegida (nunca só no frontend).
- **DTOs com campos explícitos** — nenhuma entidade interna é serializada direto.
- **Minimização (LGPD)**: poucos dados, retenção proporcional, sem documentos/biometria no Énóis.
- **Credibilidade e identidade** são decididas só no servidor; o cliente não as declara.
- **Sem automação de interação social**: nada de publicar post/comentar/curtir automaticamente
  (ver `docs/PUBLISHING_POLICY.md`).
- **Sessão e credencial com hash**, revogação de sessão e de credencial de integração.
- **Segredos só por variável de ambiente/secret do ambiente**: nenhuma propriedade de chave tem valor
  padrão no código, e o deploy não grava segredo em manifesto versionado. Isso vale daqui em diante:
  o histórico do git já conteve chaves de terceiros, e toda chave que passou pelo repositório é
  tratada como comprometida e rotacionada — nunca reaproveitada.
- **Motor de risco** (`shared/risk`): sinais combinados viram nível, e a decisão fica registrada
  com o motivo e a versão da regra (ver `docs/PLANO_SEGURANCA_HUMANA.md`).
- **Defesa contra varredura** (`shared/security/AbuseGuardFilter`): quem erra demais em pouco tempo
  (404 de rota que não existe, 401/403 de acesso, 400/405/415 de payload, método ou tipo inválido)
  é barrado na hora com **429 + `Retry-After`**, e o bloqueio escala na reincidência. A conta é por
  ERRO, não por requisição: quem usa o app produz 200 e, às vezes, um 409 de estado — nada disso
  conta. O `health`/`actuator` do cluster fica fora (derrubá-lo derrubaria o pod).

## Referências

OWASP API Security Top 10 e OWASP ASVS 5.0 (nível 2 como referência mínima de verificação). Isso
**não** equivale a certificação nem substitui uma avaliação de segurança independente.

---

### Resumo (EN)

**Do not open a public issue for security vulnerabilities.** Report privately to
**seguranca@enois.ia.br**, including the affected version/commit, a minimal reproduction, the
observed vs expected behaviour and the estimated impact. Triage within 5 business days; coordinated
disclosure for up to 90 days; the advisory ships with the fixing release. Good-faith research
within scope is welcome. The scope, the commitments and what is already in force are described
above.
