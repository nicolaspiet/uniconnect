# Distribuição e atualizações

## Como funciona no app

O aplicativo consulta a última release pública estável de `nicolaspiet/uniconnect` no GitHub, sem conta ou token. A release precisa conter:

- Um APK assinado.
- `uniconnect-update.json`, com versão, versão mínima do Android, nome do APK e SHA-256.
- Opcionalmente notas da versão, exibidas no app a partir da release.

`versionCode` é a fonte de ordenação. Tags e nomes de versão servem para apresentação. Releases em rascunho e pré-lançamentos não são oferecidos pelo canal estável.

O download informa progresso, tem limites de tamanho e tempo, apaga arquivos parciais e pode ser tentado novamente se a conexão cair. Antes de oferecer a instalação, confere SHA-256, nome do pacote, versão interna do APK e certificado de assinatura do app instalado. Os arquivos ficam no cache privado; apenas o APK concluído é compartilhado com o instalador pelo FileProvider.

A instalação exige confirmação do instalador do Android. A permissão **Permitir desta fonte** é pedida somente quando necessária. Nenhum APK é instalado silenciosamente.

## Canal público e compatibilidade

O repositório ou um canal de distribuição equivalente precisa estar publicamente acessível. Um repositório privado responde 404 a celulares sem sessão e não oferece atualizações públicas. Nunca inclua um token GitHub no aplicativo.

A assinatura precisa permanecer estável. APKs antigos assinados com uma chave de desenvolvimento só aceitam atualização assinada com a mesma chave. Trocar a chave pode exigir desinstalação e apagar credenciais e preferências. Não prometa atualização sem reinstalar antes de verificar as assinaturas das versões já distribuídas.

## Preparar uma release

1. Incremente `versionCode` e defina `versionName` em `app/build.gradle.kts`.
2. Atualize notas e documentação relevantes.
3. Execute `./gradlew testDebugUnitTest lintDebug assembleRelease`.
4. Teste login válido, credencial incorreta, Wi-Fi desligado durante login, VPN ativa, sessão já conectada, cancelamento e atualização em aparelho físico.
5. Confirme que o APK usa a chave persistente esperada.

O workflow `.github/workflows/release.yml` roda quando uma tag `v<versionName>` é enviada. Ele verifica testes e lint, assina o APK, gera metadados e publica todos os arquivos na mesma release. A tag deve coincidir exatamente com o `versionName` do APK. O mantenedor deve configurar estes repository secrets antes de enviar a tag:

- `ANDROID_KEYSTORE_BASE64`: conteúdo base64 da chave persistente.
- `ANDROID_KEYSTORE_PASSWORD`: senha do keystore.
- `ANDROID_KEY_ALIAS`: alias da chave.
- `ANDROID_KEY_PASSWORD`: senha da chave.

Sem esses valores o workflow falha antes de publicar. Chaves nunca entram no Git. Faça backup seguro da chave e das senhas fora do repositório.

## Publicação manual

Depois de assinar e verificar o APK com `apksigner`, gere o manifesto:

```sh
python3 tools/release_metadata.py dist/uniconnect-v0.6.0.apk \
  --aapt "$ANDROID_HOME/build-tools/35.0.0/aapt" \
  --tag v0.6.0 --output dist/uniconnect-update.json
```

Publique APK, `.sha256` e `uniconnect-update.json` como assets da mesma release pública estável. A ferramenta lê versão e pacote do próprio APK. Não edite checksums manualmente.

## Verificação do canal

Teste com uma versão anterior assinada com a mesma chave: consultar, aceitar modal, baixar, cancelar durante download, repetir após perda de Wi-Fi, abrir permissão de instalação, voltar, instalar e preservar credenciais. Um checksum ou assinatura inválida deve bloquear a instalação. Verifique também ausência de releases, HTTP 404/429 e aparelho sem internet: nenhum desses casos pode bloquear o login no campus.

## Primeira release pública

A distribuição 0.6.0 usa `versionCode=8` e o mesmo certificado das versões 0.1–0.5 já compartilhadas. A chave foi preservada fora do repositório e configurada em GitHub Secrets para o workflow. Não gere uma nova chave a cada release. O certificado público SHA-256 esperado é `0fd3fa2ab06128d64b216ed74879905e1d4a24c6f07d378526fabe5f58b184b1`; configure também a repository variable `ANDROID_SIGNER_SHA256` com esse valor. O workflow recusa outra assinatura.

A configuração de debug do Gradle gera sua própria chave por ambiente; um APK de debug de outro computador pode não atualizar a distribuição oficial. Use apenas os APKs assinados publicados nas releases para atualizar instalações existentes. Nunca publique keystore, JSON de senhas ou dados privados.
