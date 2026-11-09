# UniConnect

Aplicativo Android independente para autenticar no Wi-Fi da Universidade Federal do Delta do Parnaíba. Não representa oficialmente a UFDPar.

[Baixar a versão mais recente](https://github.com/nicolaspiet/uniconnect/releases/latest) · [Contribuir](CONTRIBUTING.md) · [Privacidade](docs/PRIVACY.md)

## Instalar e atualizar

Android 12 ou superior. Baixe o APK na página de releases e abra o arquivo completo no celular. Use o mesmo aplicativo de origem da instalação para autorizar instalações quando o Android solicitar.

A versão **0.6.0 (código 8)** mantém a assinatura dos APKs distribuídos anteriormente. A instalação por cima preserva os dados do aplicativo. Não desinstale para atualizar. O app consulta releases públicas ao abrir e em **Ajustes > Verificar atualizações**, oferece novidades e mostra o progresso do download. Confere tamanho, SHA-256, pacote, versão e assinatura antes de abrir o instalador do Android. Instalação sempre depende da confirmação do usuário.

Se a instalação disser “pacote inválido”, descarte o arquivo baixado e baixe novamente o APK pela release oficial. Não use a página HTML salva como APK nem `app-release-unsigned.apk`. Se continuar, anote a versão instalada, a versão do Android e o erro; a mensagem do instalador sozinha não identifica a causa.

## Primeiro acesso

1. Conecte o aparelho à rede **UFDPar-A**, **UFDPar-B** ou **UFDPar-C**. Os nomes com espaço também são aceitos.
2. Permita localização precisa e ative a localização do aparelho para identificar o nome do Wi-Fi. O app não consulta coordenadas.
3. Quando o login for necessário, informe as credenciais do SIGAA no app. Salvar no aparelho é opcional e usa AES-GCM com Android Keystore.
4. Ative o automático se desejar. Acompanhe o estado na notificação, app e widget. Uma VPN ativa pode bloquear o acesso direto ao portal; o app informa esse caso.

Android conecta ao Wi-Fi salvo; UniConnect autentica o portal. Rede validada não exige novo envio. A autenticação usa o portal HTTPS com scripts originais; alguns retornos do portal ainda exigem teste no campus. A versão atual tem tema claro/escuro em preto e branco, login oculto com progresso e diagnóstico sem valores de campos ou cookies.

## Automático e atalhos

Após ativar, pode voltar à tela inicial ou apagar a tela. Fabricantes podem encerrar o serviço conforme configurações de bateria. Em **Ajustes** o usuário escolhe recuperação após encerramento, retomada após reiniciar, aparência e comportamento do widget. Recuperação e reinício ficam desligados inicialmente; a retomada no boot exige permissões adicionais e autoinício permitido pelo fabricante. “Forçar parada” exige nova interação.

Widget compacto e bloco na central de controles podem ser adicionados em Ajustes. O estado do widget depende do monitoramento ativo e das informações do Android. Pausar cancela retomadas. Falhas que exigem intervenção não devem provocar repetição ilimitada de senha.

## Desenvolvimento

JDK 17, Android SDK 35 e Build Tools 35.0.0:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

`assembleDebug` gera um APK para desenvolvimento. `assembleRelease` gera um APK **sem assinatura**, que não deve ser distribuído. O [workflow de release](.github/workflows/release.yml) assina, verifica certificado e alinhamento e publica APK e metadados. Segredos e chave de assinatura ficam fora do Git.

Leia [CONTRIBUTING.md](CONTRIBUTING.md) e [o processo de release](docs/RELEASES.md). Os testes JVM e verificações de APK não substituem validação do login, instalador e políticas de bateria em aparelhos físicos no campus.
