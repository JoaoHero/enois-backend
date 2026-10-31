# Desenvolvimento local — Backend Énóis

Este guia descreve o caminho mínimo para executar o backend,
validar uma alteração e preparar uma contribuição.

O desenvolvimento básico utiliza Java 25, Maven Wrapper e H2.
Não exige Docker, MariaDB ou credenciais de produção.

## 1. Obter o código

```bash
git clone --branch qa https://github.com/Rede-ENois/enois-backend.git
cd enois-backend
```

## 2. Diagnosticar o ambiente

```bash
bash scripts/dev-doctor.sh
```

Corrija os itens FAIL antes de continuar. WARN indica uma
limitação que não necessariamente impede o desenvolvimento.

## 3. Iniciar a aplicação

```bash
./mvnw spring-boot:run
```

O Maven utiliza o perfil dev, configurado no pom.xml.
Esse perfil usa H2 em memória e executa as migrações Flyway.

Mantenha o processo aberto durante as verificações HTTP.

## 4. Verificar a inicialização

Em outro terminal:

```bash
curl -i http://localhost:8081/actuator/health
curl -i http://localhost:8081/version
```

Ambos devem retornar HTTP 200 em uma inicialização saudável.
O health check deve informar status UP.

Encerre a aplicação com Ctrl+C.

## 5. Compilar e testar

Para compilar sem executar testes:

```bash
./mvnw -DskipTests compile
```

Para executar uma classe de testes:

```bash
./mvnw -Dtest=ContractControllerTests test
```

Para executar toda a suíte:

```bash
./mvnw test
```

Consulte CONTRIBUTING.md para as regras de testes,
arquitetura, migrações e contratos da API.

## 6. Preparar uma contribuição

Partindo da branch qa atualizada:

```bash
git fetch origin
git switch qa
git pull --ff-only origin qa
git switch -c docs/minha-contribuicao
```

Após realizar e validar as alterações:

```bash
git diff --check
git status --short
```

Adicione apenas os arquivos relacionados à contribuição
e registre um commit seguindo CONTRIBUTING.md.

### Publicar a contribuição

Crie um fork de `Rede-ENois/enois-backend` na sua conta
pelo GitHub. Não é necessário ter acesso de escrita ao
repositório original.

Configure o remote do seu fork (substitua `SEU_USUARIO`):

```bash
git remote add fork https://github.com/SEU_USUARIO/enois-backend.git
git remote -v
```

Se o remote `fork` já existir, reutilize-o.

Após adicionar apenas os arquivos pretendidos com `git add`,
confira o conteúdo preparado e faça o commit:

```bash
git diff --cached --check
git diff --cached --stat
git commit -m "docs: descreve minha contribuicao"
git push -u fork docs/minha-contribuicao
```

A mensagem do commit é ilustrativa: adapte-a à alteração.

Abra um Pull Request no GitHub selecionando:

- Repositório de destino: `Rede-ENois/enois-backend`.
- Branch de destino: `qa`.
- Origem: sua branch publicada no fork.

Preencha o template oficial de Pull Request, descreva as
mudanças e informe quais verificações foram executadas.
Não inclua secrets, relatórios locais ou arquivos `.env`.

## Problemas comuns

- Java incompatível: execute novamente o Dev Doctor.
- Porta 8081 ocupada: identifique o processo antes de encerrá-lo.
- Falha do Maven: confira a versão do JDK e o primeiro erro do log.
- Teste falhando: execute a classe isoladamente e confira os
  relatórios do Maven em target/surefire-reports/.
- Mudanças inesperadas no Git: inspecione git status antes
  de adicionar, descartar ou remover arquivos.

Não configure secrets de QA ou produção para o fluxo local.
