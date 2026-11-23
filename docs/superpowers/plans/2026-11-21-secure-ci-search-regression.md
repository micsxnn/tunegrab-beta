# Plano: CI confiável, assinatura segura e regressão da busca

**Projeto:** `micsxnn/tunegrab-beta`
**Data:** 2026-11-21
**Estado:** proposta; nenhuma implementação deste plano foi aplicada.

## Objetivo

Restaurar a compilação do Android, impedir regressões na busca integrada e transformar o pipeline em uma entrega segura e verificável. A primeira entrega deve deixar `main` verde, bloquear releases quebradas e eliminar a dependência da chave de assinatura versionada no repositório.

## Estado atual verificado

- O GitHub está autenticado como `micsxnn`; o token tem os escopos `repo` e `workflow`.
- O último workflow de `tunegrab-beta`, executado em 2026-11-16, terminou com `failure` em `:app:compileReleaseKotlin`.
- Causas exatas registradas pelo CI:
  - `SearchFragment.kt:74`: `binding.inputSearch.text?.toString?.trim()` chama `toString()` como se fosse anulável.
  - `SearchFragment.kt:95`: `MainActivity.handleSharedUrl(...)` não existe; a API disponível é `MainActivity.openDownloadFor(...)`.
- O repositório não possui suíte de testes nem dependências `testImplementation`.
- `app/keystore/tunegrab.keystore` está versionado e `app/build.gradle.kts` contém senha do keystore e da chave em texto puro.
- O repositório não possui GitHub Actions Secrets nem variables.
- A release `v0.23.0-beta` está em draft e não possui APK publicado. Portanto, a chave beta compromiseada pode ser substituída sem quebrar atualizações já instaladas.
- `main` não possui ruleset/proteção efetiva; `tunegrab-beta` é público, então attestations do GitHub estão disponíveis no plano atual.
- O repositório estável `micsxnn/TuneGrab` já possui uma pipeline mais avançada: assinatura fora do código, hashes SHA-256, linhagem de rotação e 119 testes JVM. O beta deve portar esse padrão sem copiar sua chave antiga.

## Restrições

- Manter `applicationId = com.tunegrab.app.beta`, `minSdk = 24`, `targetSdk = 34`, AGP `9.1.1`, Kotlin `2.4.10` e Gradle `9.3.1`.
- Não migrar AGP, Kotlin ou a DSL nesta entrega; isso ampliaria risco e não é necessário para corrigir a falha.
- Não publicar uma tag ou release durante a validação do plano.
- A rotação da chave beta deve usar uma chave nova. Remover arquivos do `main` não torna a chave antiga segura, pois ela permanece no histórico público.
- A chave antiga não pode ser reutilizada nem continuar válida para publicar atualizações beta.

## Arquivos afetados

### Modificar

- `.github/workflows/build.yml` — validação de pull requests, permissões mínimas, assinatura via Secrets, checksums e attestations.
- `app/build.gradle.kts` — remover credenciais embutidas e ler a configuração de assinatura por ambiente.
- `app/src/main/java/com/tunegrab/app/ui/SearchFragment.kt` — corrigir os dois erros de compilação e usar a API pública correta.
- `app/src/main/java/com/tunegrab/app/search/SearchViewModel.kt` — remover o acoplamento direto ao singleton, permitindo testes.
- `.gitignore` — impedir que keystores e arquivos de assinatura entrem no repositório.
- `README.md` — documentar builds locais e de CI sem credenciais reais.

### Criar

- `app/src/main/java/com/tunegrab/app/search/SearchService.kt` — interface do provedor de busca e contrato de resultado.
- `app/src/main/java/com/tunegrab/app/search/SearchUrlParser.kt` — extração pura do ID do YouTube.
- `app/src/test/java/com/tunegrab/app/search/SearchUrlParserTest.kt` — testes do parser.
- `app/src/test/java/com/tunegrab/app/search/SearchViewModelTest.kt` — testes de estado, erro, retry e consulta normalizada.
- `docs/SIGNING.md` — geração, armazenamento, rotação e implicações da assinatura beta.
- `docs/RELEASE.md` — procedimento verificável de release.

### Remover do versionamento

- `app/keystore/tunegrab.keystore` — mover para armazenamento privado do usuário e usar apenas os Secrets do GitHub.

## Tarefas

### 1. Corrigir a compilação quebrada de `main`

**Arquivos:** `app/src/main/java/com/tunegrab/app/ui/SearchFragment.kt`

1. Trocar a leitura do campo de texto por uma conversão não anulável, por exemplo `binding.inputSearch.text?.toString().orEmpty().trim()`.
2. Substituir `activity.handleSharedUrl(url, autoplay = false)` por `activity.openDownloadFor(url)`.
3. Remover imports não usados de `SearchFragment.kt` (`DownloadService`, `YouTubeSearchService` e outros que o compilador sinalizar).
4. Compilar a variante release.

**Verificação:**

```bash
./gradlew :app:compileReleaseKotlin --stacktrace
```

**Critério de aceite:** a tarefa termina sem erros e o workflow anterior deixa de falhar em `compileReleaseKotlin`.

### 2. Tornar a lógica da busca testável

**Arquivos:** `SearchService.kt`, `SearchUrlParser.kt`, `YouTubeSearchService.kt`, `SearchViewModel.kt`, novos testes.

1. Criar uma interface pequena para buscar vídeos; `YouTubeSearchService` passa a implementá-la sem mudar o comportamento atual junto ao NewPipeExtractor.
2. Extrair a extração do ID para uma função pura, cobrindo URLs `watch`, `youtu.be`, `embed` e `v`, além de entradas inválidas.
3. Injetar o provedor no `SearchViewModel` com uma implementação padrão, preservando `YouTubeSearchService` para a produção.
4. Criar testes JVM para:
   - consulta vazia e espaços;
   - estado `isSearching` durante a operação;
   - sucesso com resultados;
   - erro exibido e retry;
   - normalização da consulta;
   - extração de video ID.
5. Adicionar `junit:junit:4.13.2`, `kotlinx-coroutines-test` e a configuração mínima de testes JVM ao `app/build.gradle.kts`.

**Verificação:**

```bash
./gradlew :app:testDebugUnitTest --stacktrace
```

**Critério de aceite:** todos os testes passam sem aparelho e sem acesso à rede.

### 3. Remover credenciais e chave do repositório

**Arquivos:** `app/build.gradle.kts`, `.gitignore`, `app/keystore/tunegrab.keystore`, `docs/SIGNING.md`.

1. Gerar uma chave de assinatura beta nova, com validade de pelo menos 25 anos, guardada fora do workspace e do repositório.
2. Guardar a chave como Secret do GitHub em formato base64 e guardar alias/senhas em Secrets separados. Não registrar valores em issue, log, README ou plano.
3. Remover do versionamento o keystore comprometido e adicionar `*.keystore`, `*.jks`, `*.p12` e `signing.properties` ao `.gitignore`.
4. Configurar `app/build.gradle.kts` para ler `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD` do ambiente; quando ausentes, o build release local deve falhar com mensagem clara em vez de usar credenciais padrão.
5. Manter a linhagem de rotação apenas se existir uma justificativa de compatibilidade; para o beta ainda não publicado, a recomendação é começar com a chave nova, sem linhagem da chave comprometida.
6. Documentar perda da chave, uso do `apksigner`, rotação e porque não se deve reativar a chave antiga.

**Verificação:**

```bash
git ls-files | grep -Ei '\.(jks|keystore|p12)$' && exit 1 || true
grep -R -n -E 'tunegrab2025|storePassword[[:space:]]*=[[:space:]]*"' app .github docs --exclude-dir=build
```

**Critério de aceite:** nenhum keystore ou senha aparece no índice do Git, no código ou no workflow.

### 4. Dividir validação e publicação no GitHub Actions

**Arquivo:** `.github/workflows/build.yml`.

1. Executar em `pull_request`, pushes de `main` e tags `v*`.
2. Criar um job `validate` com `contents: read` para `testDebugUnitTest`, `lintDebug` e `assembleDebug`.
3. Manter release em job separado, condicionado a tag `v*`, com `contents: write` somente nesse job.
4. Atualizar `actions/checkout` para `v5` e `actions/setup-java` para `v5`; manter `gradle/actions/setup-gradle` em versão compatível com o runner atual.
5. Decodificar o keystore de `KEYSTORE_BASE64` em `$RUNNER_TEMP`, sem imprimir o conteúdo, e exportar as demais variáveis de assinatura somente ao job de release.
6. Falhar com mensagem explícita se qualquer Secret obrigatório estiver ausente.
7. Gerar `SHA256SUMS` para os APKs e anexá-lo à release.
8. Gerar attestation de provenance do APK com `actions/attest@v4`, usando `id-token: write`, `attestations: write` e `contents: read` conforme a documentação oficial do GitHub.
9. Manter `concurrency` e impedir publicação de drafts sem APK final.

**Verificação:**

```bash
gh workflow view build.yml --repo micsxnn/tunegrab-beta
gh run list --repo micsxnn/tunegrab-beta --workflow build.yml --limit 10
```

**Critério de aceite:** pull request e `main` passam sem publicar release; uma tag de teste em branch isolada produz APK assinado, checksum e attestation verificável.

### 5. Configurar proteção de branch e regras de release

**Recurso:** ruleset do repositório `micsxnn/tunegrab-beta`.

1. Criar ruleset para `main` exigindo pull request e o check `validate` bem-sucedido.
2. Exigir que branches candidatas estejam atualizadas e permitir bypass somente por owners, com justificativa.
3. Impedir push direto e force-push para `main`.
4. Documentar release: tag `v*`, pipeline verde, `RELEASE_NOTES.md`, draft aberto, APK assinado, checksum e attestation.

**Verificação:**

```bash
gh api repos/micsxnn/tunegrab-beta/rulesets
gh api repos/micsxnn/tunegrab-beta/branches/main/protection
```

**Critério de aceite:** `main` fica protegida e exige o novo check antes do merge.

### 6. Validar a entrega completa sem publicar

1. Rodar testes, lint, build debug e release com uma chave temporária fora do repositório.
2. Inspecionar o APK com `apksigner verify --verbose --print-certs` e confirmar o certificado novo.
3. Comparar o SHA-256 do APK com `SHA256SUMS`.
4. Verificar a attestation com `gh attestation verify`.
5. Testar instalação e atualização em Android 7/8 e Android 13+, confirmando que a aplicação beta abre e a busca leva ao fluxo correto de download.
6. Executar uma busca real, abrir um resultado e cancelar o download para confirmar que nenhum estado fica preso.
7. Revisar o diff e executar uma revisão de código focada em segurança do workflow, assinatura e tratamento de falhas.

**Verificação final:**

```bash
./gradlew clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease --stacktrace
git status --short
git diff --check
```

**Critério de aceite:** todos os comandos passam, o APK usa somente a chave nova, a release continua sem publicação automática e o repositório não contém segredos.

## Ordem de execução

1. Tarefa 1, para restaurar a compilação.
2. Tarefa 2, para proteger a busca contra regressões.
3. Tarefa 3, com uma nova chave beta.
4. Tarefa 4, com validação em pull requests e release isolada.
5. Tarefa 5, depois que o nome e o status do check `validate` estiverem estáveis.
6. Tarefa 6, antes de criar qualquer tag ou publicar a beta.

## Critérios de sucesso finais

- `main` e pull requests executam `validate` com sucesso.
- `compileReleaseKotlin` não apresenta erros.
- Testes JVM cobrem o parser e o estado do ViewModel sem rede.
- Nenhum keystore ou senha permanece versionado.
- A chave beta antiga não assina novos APKs.
- Todo APK publicado tem checksum e attestation de provenance.
- `main` exige pull request e check verde.
- A release continua sendo publicada apenas por tag após revisão e validação manual.
