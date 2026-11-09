# Chamados TI Android

Cliente nativo em Kotlin + Jetpack Compose para o Sistema de Chamados. O Android Gradle project fica na raiz deste repositório e usa duas fontes de dados:

| Flavor | Fonte |
| --- | --- |
| `fake` | Dados simulados locais para desenvolvimento visual e jornadas de UI. |
| `remote` | API Spring Boot, com modelos Kotlin gerados de `contracts/backend/openapi.yaml`. |

## Requisitos

- JDK 21 (o código compila para Java 17)
- Android SDK Platform 37 e Build Tools 37.0.0
- Gradle Wrapper incluído

Abra a raiz deste repositório no Android Studio ou execute:

```bash
./gradlew testFakeDebugUnitTest testRemoteDebugUnitTest
./gradlew lintFakeDebug lintRemoteDebug
./gradlew detektFakeDebug detektRemoteDebug
```

O flavor remoto gera os modelos antes da compilação:

```bash
./gradlew :app:openApiGenerate
```

O snapshot de contrato é fornecido pelo repositório integrador em `contracts/backend/openapi.yaml`; não edite modelos em `app/build/generated/`. As respostas gravadas do backend, quando disponíveis, ficam em `contracts/backend/responses.json`.

Para apontar o app a outro ambiente, passe uma URL sem credenciais:

```bash
./gradlew installRemoteDebug -Pchamados.apiBaseUrl=https://example.invalid/api/
```

## Release

O artefato distribuído é `remoteRelease`, com R8 e o mesmo `applicationId` (`br.com.portosoftware.chamados`). A assinatura é sempre fornecida fora do repositório por `CHAMADOS_SIGNING_PROPERTIES` ou pela CI. Nunca coloque keystores, senhas, tokens, URLs privadas ou dados de teste neste repositório.

Os workflows do GitHub Actions publicam um APK de staging em mudanças relevantes na branch principal e um release de produção em tags `android-v<versionName>`. Cada release inclui APK, `mapping.txt` e `SHA256SUMS`.
