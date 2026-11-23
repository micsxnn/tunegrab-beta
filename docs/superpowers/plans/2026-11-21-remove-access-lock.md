# Remoção completa do bloqueio por senha — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remover do TuneGrab beta toda a trava de acesso por senha, inclusive tela, código do aparelho, consulta remota e recursos do sistema de convites.

**Architecture:** O fluxo normal do `SplashActivity` Decide entre tutorial e `MainActivity` sem consultar estado de acesso. A `MainActivity` deixa de proteger links compartilhados. Os componentes e recursos exclusivos da autenticação são removidos, e um teste de regressão impede que voltem.

**Tech Stack:** Kotlin, Android Views/XML, JUnit 4, Gradle/Kotlin DSL.

**Spec:** `docs/superpowers/specs/2026-11-21-remove-access-lock-design.md`

## Global Constraints

- Remover completamente senha, tela de bloqueio, código do aparelho, consulta remota e persistência de liberação.
- Preservar o tutorial de primeira abertura e o tratamento normal de links compartilhados.
- Não alterar `applicationId`, versão, assinatura, CI ou funcionalidades de busca.
- Não reescrever o histórico Git.
- Seguir TDD: o teste deve falhar antes da remoção e passar depois.

## Review Focus

- Abertura pela primeira vez deve mostrar o tutorial, sem bloqueio.
- Abertura posterior deve chegar diretamente à `MainActivity`, sem internet.
- Link compartilhado ou aberto externamente deve entrar na `MainActivity`, sem verificação de liberação.
- Atualização sobre instalação já liberada não deve reintroduzir tela de bloqueio.
- A busca global por marcadores do sistema antigo deve retornar zero ocorrências operacionais.

---

### Task 1: Teste de regressão e remoção do fluxo

**Files:**
- Create: `app/src/test/java/com/tunegrab/app/access/AccessLockRemovedTest.kt`
- Modify: `app/src/main/java/com/tunegrab/app/SplashActivity.kt`
- Modify: `app/src/main/java/com/tunegrab/app/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Delete: `app/src/main/java/com/tunegrab/app/access/AccessGate.kt`
- Delete: `app/src/main/java/com/tunegrab/app/access/LockActivity.kt`
- Delete: `app/src/main/res/layout/activity_lock.xml`
- Delete: `access/passwords.json`

**Interfaces:**
- Consumes: raiz do projeto Gradle em runtime de teste.
- Produces: teste JVM `AccessLockRemovedTest` que lê fontes versionados e exige ausência dos marcadores operacionais do bloqueio.

- [ ] **Step 1: Adicionar dependência JUnit ao bloco `dependencies`**

Adicionar em `app/build.gradle.kts`:

```kotlin
testImplementation("junit:junit:4.13.2")
```

- [ ] **Step 2: Escrever o teste de regressão que deve falhar**

Criar `app/src/test/java/com/tunegrab/app/access/AccessLockRemovedTest.kt` com um teste que percorra `app/src/main` e `access`, procure marcadores como `AccessGate`, `LockActivity`, `activity_lock`, `lock_`, `senha`, `password` e `invite`, e falhe listando os arquivos encontrados. O teste deve considerar apenas fontes operacionais; a documentação em `docs/` não participa da varredura.

- [ ] **Step 3: Executar o teste e confirmar a falha esperada**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*AccessLockRemovedTest' --stacktrace
```

Expected: FAIL, com `AccessGate`, `LockActivity` e `activity_lock` listados pelo teste.

- [ ] **Step 4: Remover o redirecionamento do Splash**

Em `SplashActivity.kt`:

- remover imports de `AccessGate` e `LockActivity`;
- remover a consulta `AccessGate.isUnlocked(this)`;
- manter o roteamento entre `OnboardingActivity` e `MainActivity` exatamente como está.

- [ ] **Step 5: Remover a proteção da MainActivity**

Em `MainActivity.kt`:

- remover imports de `AccessGate` e `LockActivity`;
- remover o bloco que inicia `LockActivity` e finaliza a activity;
- manter `handleIntent`, links pendentes e `openDownloadFor` sem alterações.

- [ ] **Step 6: Remover a Activity do manifest**

Em `AndroidManifest.xml`:

- remover o bloco `<activity android:name=".access.LockActivity" ...>`;
- remover o comentário do manifesto que descreve a senha e a tela de bloqueio.

- [ ] **Step 7: Remover recursos e implementações exclusivos**

- Apagar `app/src/main/java/com/tunegrab/app/access/AccessGate.kt`.
- Apagar `app/src/main/java/com/tunegrab/app/access/LockActivity.kt`.
- Apagar `app/src/main/res/layout/activity_lock.xml`.
- Apagar `access/passwords.json` e o diretório `access/` se ficar vazio.
- Remover de `strings.xml` somente as strings exclusivas `lock_*`; não remover textos de favoritos ou outras telas que usem a palavra "convite" em sentido diferente.

- [ ] **Step 8: Executar o teste e confirmar que passa**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*AccessLockRemovedTest' --stacktrace
```

Expected: PASS.

- [ ] **Step 9: Buscar marcadores remanescentes**

Run:

```bash
grep -R -n -E 'AccessGate|LockActivity|activity_lock|lock_(password|device|footer|err|sub|title)' app/src/main access 2>/dev/null || true
```

Expected: nenhuma saída operacional.

- [ ] **Step 10: Compilar e revisar o diff**

Run:

```bash
./gradlew :app:compileDebugKotlin --stacktrace
git diff --check
git status --short
```

Expected: compilação sem erros, nenhum whitespace inválido e somente os arquivos planejados modificados ou removidos.

## Fora do escopo

- Reescrever o histórico Git para apagar a senha da chave de assinatura ou arquivos antigos.
- Corrigir separadamente os erros de compilação já existentes em `SearchFragment.kt`; eles pertencem ao plano de CI e busca.
- Criar tag, release ou APK publicado.
