# Lelê Crochê — aplicativo Android

Projeto Android nativo em Kotlin + Jetpack Compose. O objetivo é reconstruir no app as telas e regras do sistema web original, sem usar WebView.

## Estrutura inicial
- `app/`: aplicativo Android nativo.
- `app/src/main/java/com/lelecroche/app/MainActivity.kt`: navegação e primeira estrutura visual.
- `.github/workflows/android.yml`: compilação automática do APK de depuração.

## Referência funcional
O sistema web original possui painel inicial, encomendas/pedidos, peças e receitas, estoque de materiais, compras e movimentações, clientes, produção, financeiro, relatórios, catálogo público, configurações e backup. A API PHP original separa operações autenticadas (`api.php`) do catálogo público (`catalogo.php`).

## Compilação
O workflow **Android APK** compila com Java 17 e Gradle 8.9 quando há push na branch `main` ou execução manual. O arquivo gerado é `app/build/outputs/apk/debug/app-debug.apk`, publicado como artefato do GitHub Actions.

## Estado atual
A estrutura nativa inicial e a navegação-base estão criadas. As telas e operações ainda precisam ser implementadas e validadas módulo por módulo; os indicadores exibem traços em vez de dados inventados. A integração com o backend PHP/MySQL será feita depois da reconstrução local das telas e regras.
