# UniConnect — privacidade

UniConnect é um aplicativo pessoal para autenticação no Wi-Fi da UFDPar.

- Não possui servidor, conta própria, analytics, anúncios ou rastreamento.
- Não coleta localização. A permissão é exigida pelo Android para ler o nome da rede Wi-Fi.
- Credenciais salvas ficam criptografadas no aparelho com Android Keystore. Backup e transferência do app estão desativados.
- Credenciais são enviadas somente ao portal HTTPS `login.ufdpar.edu.br:6082`, quando o usuário autoriza a conexão manual ou o modo automático ativado por ele.
- O diagnóstico omite valores de campos, cookies e conteúdo da página.
- O usuário pode pausar o monitoramento pela notificação, app, widget ou atalho rápido.
- Quando a verificação de atualizações está ativada ou é solicitada manualmente, o app consulta releases públicas no GitHub. Nenhuma credencial universitária é incluída. GitHub e sua infraestrutura recebem os dados normais de uma conexão, como endereço IP e User-Agent.
- O download de uma atualização só começa após escolha do usuário. O APK fica temporariamente no cache privado e é conferido por SHA-256, pacote, versão e assinatura. A instalação exige confirmação do Android e, quando necessário, permissão para instalar desta fonte.

O APK compartilhado diretamente é uma versão de teste para Android 12+. Publicação em loja exige assinatura de produção, política hospedada, revisão de permissões e testes por aparelho.
