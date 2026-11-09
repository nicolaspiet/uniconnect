# Contribuindo com UniConnect

UniConnect autentica o portal da UFDPar pela conexão Wi-Fi selecionada. Ele não representa oficialmente a universidade. Android conecta à rede Wi-Fi; UniConnect envia o login quando autorizado pelo usuário.

## Ambiente

- JDK 17.
- Android SDK 35 e Build Tools 35.0.0.
- Android Studio é opcional; o Gradle Wrapper está incluído.

Configure `ANDROID_HOME` ou um `local.properties` local. Não envie esse arquivo ao repositório.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Fluxo de contribuição

1. Abra uma issue descrevendo o problema ou melhoria. Mudanças pequenas podem ir diretamente por pull request.
2. Crie uma branch com uma mudança focada.
3. Preserve as preferências do usuário e trate cancelamento, queda do Wi-Fi e respostas de erro do portal.
4. Rode os checks acima e descreva no pull request o que foi realmente validado. Testes locais não substituem teste no campus.

## Segurança e privacidade

- Nunca inclua senhas, matrículas, cookies, tokens, dumps completos do portal, keystores ou certificados privados.
- Credenciais só podem sair pelo portal HTTPS autorizado. SSID não prova a identidade do servidor.
- Não desative validação TLS nem permita qualquer endereço como destino de credenciais.
- Não repita tentativas de senha indefinidamente. Uma falha deve permitir correção pelo usuário.
- Novos dados persistidos exigem revisão de backup, criptografia e uso entre processos.
- Evite bibliotecas de analytics, publicidade e serviços remotos desnecessários.

Para relatar uma vulnerabilidade, use a opção privada **Report a vulnerability** na aba Security do GitHub, quando habilitada. Não publique credenciais nem uma exploração com dados reais em issues.

## Organização

- `app/src/main/java/br/edu/uniconnect`: interface, autenticação, monitoramento e atualizações.
- `app/src/main/res`: widget, ícones e recursos Android.
- `app/src/test`: testes JVM com dados simulados.
- `docs`: pesquisa, privacidade e distribuição.
- `tools/release_metadata.py`: manifesto público de atualização e checksum do APK assinado.

## Releases

O mantenedor incrementa `versionCode` e `versionName`, usa uma chave de assinatura estável e publica a tag correspondente. Veja [o processo de release](docs/RELEASES.md). Contribuidores não precisam de acesso às chaves.
