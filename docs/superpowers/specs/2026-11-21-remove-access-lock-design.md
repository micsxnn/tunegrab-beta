# Design: remoção completa do bloqueio por senha

**Projeto:** `micsxnn/tunegrab-beta`
**Data:** 2026-11-21

## Objetivo

Remover completamente o sistema de acesso por senha do TuneGrab beta. A instalação deve abrir o aplicativo normalmente, sem tela de bloqueio, senha, código do aparelho, consulta remota de convites ou persistência de liberação.

## Escopo

Remover:

- o redirecionamento do `SplashActivity` para `LockActivity`;
- a proteção da `MainActivity` contra links compartilhados;
- `AccessGate` e `LockActivity`;
- `activity_lock.xml` e os recursos de texto exclusivos da tela de bloqueio;
- o diretório de dados `access/`, incluindo a lista de hashes de senhas;
- as menções a senha, convite e código do aparelho na documentação e comentários do projeto.

Preservar:

- o tutorial de primeira abertura;
- o roteamento normal do `SplashActivity`;
- o tratamento de links compartilhados;
- o `applicationId` e o restante do funcionamento do app;
- a pipeline de CI, assinatura e searches já planejadas.

## Novo fluxo

1. O usuário abre o TuneGrab pelo ícone.
2. `SplashActivity` verifica apenas se o tutorial já foi concluído.
3. Primeira abertura: `OnboardingActivity`.
4. Aberturas seguintes: `MainActivity`.
5. Links compartilhados ou abertos externamente entram diretamente em `MainActivity`.

Não haverá qualquer tela intermediária de acesso. O app não depende de internet, lista remota ou estado local de liberação para abrir.

## Estratégia de teste

Adicionar primeiro um teste de regressão que leia os fontes do aplicativo e falhe enquanto existirem referências ao bloqueio. Depois remover a implementação e executar novamente o teste, confirmando que passa.

O teste será um teste JVM simples e sem Android framework:

- procurar `LockActivity`, `AccessGate`, `activity_lock` e marcadores de senha nas rotas e nos recursos;
- falhar com uma lista clara dos arquivos encontrados;
- passar quando nenhuma referência operacional permanecer.

A verificação final também inclui compilação do módulo e busca global por marcadores remanescentes.

## Riscos

- Um APK já instalado pode continuar com dados locais antigos, mas isso não recria a tela: o código de bloqueio deixará de existir no APK atualizado.
- Instalações novas e atualizações deixarão de exigir rede ou senha.
- Remover arquivos do repositório não altera o histórico Git; este design remove apenas o estado atual. Reescrever o histórico é uma operação separada e não faz parte deste escopo.

## Critérios de aceite

- Não existe `LockActivity` nem `AccessGate` no código atual.
- `activity_lock.xml` e `access/passwords.json` não existem no repositório.
- `SplashActivity` e `MainActivity` não importam nem encaminham para o bloqueio.
- Não existem strings ou comentários operacionais sobre senha, convite ou código do aparelho.
- O teste de regressão falha no estado atual e passa após a remoção.
- O módulo compila sem erros.
