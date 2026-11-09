# UniConnect: pesquisa e decisão inicial

Pesquisa consultada em 9 de novembro de 2026. Projeto pessoal para o Redmi Note 13 com Android/HyperOS. A versão exata do sistema ainda precisa ser confirmada. A primeira versão exige Android 12 ou superior.

## Evidências específicas da UFDPar

Atualização 0.1.1: a captura enviada pelo usuário confirmou o SSID **UFDPar-A**, com hífen. O aplicativo aceita as variantes A/B com hífen ou espaço, usando a mesma regra na interface e na consulta. O nome com espaço do tutorial não era suficiente para reconhecer a rede real.

A instituição é a Universidade Federal do Delta do Parnaíba, não UFPR. O [tutorial oficial](https://ufdpar.edu.br/ufdpar/como-acessar-a-rede-wi-fi) informa as redes **UFDPar A** e **UFDPar B**, acesso pelo `conecta.ufdpar.edu.br` e autenticação com usuário e senha do SIG/SIGAA.

O usuário forneceu `https://login.ufdpar.edu.br:6082/php/uid.php?vsys=1&rule=4&url=...`. A porta e o caminho são compatíveis com o Authentication Portal da Palo Alto. Isso é uma identificação provável, não confirmação da versão, do HTML ou do método de envio. O parâmetro `url` indica o destino original de navegação; ele não deve ser usado como destino de credenciais. `vsys` e `rule` podem depender da rede/política, portanto os valores fornecidos são apenas o ponto de partida do diagnóstico.

O [portal público](https://conecta.ufdpar.edu.br/) informa que a autenticação exige conexão à rede da universidade. A consulta local do hostname de login falhou com `Could not resolve host`. Não obtivemos o formulário real e não fizemos tentativas de login.

A universidade também oferece eduroam. Entretanto, o [guia oficial](https://ufdpar.edu.br/protic/paginas/guia-eduroan.pdf) descreve autenticação adicional para visitantes na UFDPar. Não há evidência suficiente para prometer que eduroam elimina o portal no caso deste usuário. Se a PROTIC confirmar acesso direto para a conta dele, um perfil institucional via geteduroam será mais simples e econômico que manter um app próprio.

## Como funciona

Conectar ao ponto de acesso Wi-Fi e obter um endereço IP não significa que o acesso à internet está autorizado. Um portal cativo restringe o tráfego até a autenticação. No modelo Palo Alto, a autenticação pode estabelecer um mapeamento entre usuário e IP, com tempo de expiração definido pela instituição. Cookies também podem participar do fluxo. O app deve reproduzir o login autorizado do próprio usuário, não remover as regras de acesso.

O Android informa `NET_CAPABILITY_CAPTIVE_PORTAL` e `NET_CAPABILITY_VALIDATED`. A ausência do primeiro não prova internet liberada: as sondagens do sistema podem falhar. `NET_CAPABILITY_INTERNET` descreve capacidade pretendida, não acesso confirmado. Uma resposta HTTP 200 do formulário também não prova sucesso de login.

## Alternativas avaliadas

- **Perfil eduroam institucional:** melhor quando resolve a necessidade sem portal. Pouca manutenção e reconexão gerenciada pelo Android; depende da política efetiva da UFDPar.
- **Kotlin nativo + HTTPS:** recomendação para automatizar o portal existente. Permite selecionar a rede Wi-Fi, tratar cookies/tokens e validar o resultado. Exige observar o formulário real e testar no campus.
- **WebView:** alternativa se o portal depender de JavaScript ou SSO. Mais pesada e sensível a mudanças de página; login com MFA pode continuar exigindo interação. Não usar JavaScript injetado indiscriminadamente nem ignorar erros de certificado.
- **Tasker/MacroDroid:** úteis para um experimento pessoal, mas continuam sujeitos às permissões e restrições de segundo plano. Não eliminam a necessidade de compreender o protocolo de login.
- **Acessibilidade para clicar no navegador:** frágil, dependente da interface e permissões amplas. Não recomendada para este caso.
- **WorkManager periódico:** inadequado para login imediato ao chegar ao campus. O intervalo mínimo é 15 minutos e a execução pode atrasar. Além disso, condições de conectividade podem não ser satisfeitas antes de liberar o portal.
- **WifiNetworkSuggestion:** ajuda o sistema a selecionar uma rede, mas não envia automaticamente o formulário do portal. Seu evento de pós-conexão está associado a redes sugeridas e não constitui uma solução genérica para qualquer Wi-Fi salvo.

## Arquitetura recomendada para a autenticação

1. Observar redes Wi-Fi por `ConnectivityManager.registerNetworkCallback`; não observar apenas a rede padrão, que pode continuar sendo a rede móvel.
2. Identificar o SSID autorizado. O nome da rede sozinho não autentica o servidor: exigir HTTPS válido para o hostname exato do portal.
3. Usar `Network.openConnection` ou cliente com sockets **e DNS** vinculados à rede Wi-Fi. Não enviar o login pelo 4G/5G.
4. Buscar o formulário, entender o destino, campos, cookies, tokens dinâmicos e eventual JavaScript. Não fixar nomes de campos sem confirmação.
5. Armazenar credenciais localmente com AES-GCM e chave no Android Keystore, sem backup ou logs de senha. A chave não pode exigir biometria a cada uso se a autenticação for totalmente automática. Isso é um compromisso explícito de proteção versus automação.
6. Submeter uma tentativa por vez, impor intervalo e limite de tentativas e interromper diante de erro de credenciais. Evitar bloqueio da conta por repetição automática.
7. Confirmar acesso pela mesma rede: capacidades atualizadas e, se necessário, uma sondagem HTTPS com resposta esperada. Não usar apenas status HTTP do login.

### Execução sem abrir o app a cada conexão

Um callback existe enquanto o processo está ativo; ele não mantém o processo vivo. Para monitoramento contínuo, estudar um serviço em primeiro plano ativado por ação do usuário, com notificação e botão de parada. Android 12+ restringe iniciar esse serviço do segundo plano. Android 14+ exige tipo de serviço apropriado e suas permissões. Não classificar automaticamente como `dataSync`: não é um mecanismo de monitoramento ilimitado, e há restrições adicionais em versões recentes.

A interação com o gateway pode justificar `connectedDevice`, mas o enquadramento final deve ser validado junto com o acesso ao SSID em segundo plano. Ter esse tipo de serviço não garante, por si só, acesso contínuo à informação de localização. Avaliar a necessidade de serviço `location` ou autorização de localização em segundo plano conforme a versão e o fluxo escolhido, solicitando somente o necessário. Uma publicação no Google Play exige avaliação própria das políticas de permissões e de serviços.

SSID é informação sensível à localização. O protótipo usa `ACCESS_FINE_LOCATION` e `FLAG_INCLUDE_LOCATION_INFO` e explica a necessidade de localização precisa e localização do dispositivo ativada. `NEARBY_WIFI_DEVICES` não substitui indiscriminadamente todas as verificações de localização.

No HyperOS, autoinício e restrições de bateria precisam de teste no aparelho real. Os nomes e caminhos variam por versão. Mesmo após ajustes não se deve prometer execução após “Forçar parada”. A primeira versão não solicita permissões de segundo plano nem inicia serviço contínuo: o diagnóstico é feito com a tela aberta.

## Entrega atual e próximo passo

Implementado: projeto Kotlin/Compose, observação de Wi-Fi, consulta HTTPS por rede selecionada, relatório limitado à estrutura dos formulários, compartilhamento explícito e testes de origem/redação de dados. Não existe armazenamento de senha, submissão de formulário ou autenticação automática nesta versão.

No campus, abrir a versão de diagnóstico conectado à UFDPar A/B, conceder localização precisa, tocar em **Diagnosticar portal** e compartilhar o relatório. Ele inclui a versão do Android, dispensando procurá-la nas configurações. Se a estrutura revelar um fluxo JavaScript/SSO, poderá ser necessário obter um exemplo sanitizado da requisição; não compartilhar senha, cookies, tokens ou HAR bruto.

## Fontes oficiais

- [UFDPar: acesso ao Wi-Fi](https://ufdpar.edu.br/ufdpar/como-acessar-a-rede-wi-fi)
- [UFDPar: guia eduroam](https://ufdpar.edu.br/protic/paginas/guia-eduroan.pdf)
- [Palo Alto: Authentication Policy and Authentication Portal](https://docs.paloaltonetworks.com/ngfw/administration/user-id/user-id-concepts/user-mapping/authentication-policy-and-captive-portal)
- [Android: leitura do estado da rede](https://developer.android.com/develop/connectivity/network-ops/reading-network-state)
- [Android: Network](https://developer.android.com/reference/android/net/Network)
- [Android: NetworkCallback](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback)
- [Android: permissões de Wi-Fi](https://developer.android.com/develop/connectivity/wifi/wifi-permissions)
- [Android: restrições de serviços em primeiro plano](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Android: tipos de serviço em primeiro plano](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Android: PeriodicWorkRequest](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest)
- [Android: sugestões de Wi-Fi](https://developer.android.com/develop/connectivity/wifi/wifi-suggest)
- [Android: Keystore](https://developer.android.com/privacy-and-security/keystore)
- [Xiaomi: controle de autoinício no HyperOS](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1624)

## Validação no aparelho (pendente)

Testar SSID com localização permitida/negada e desligada; Wi-Fi universitário com dados móveis ativos; desconexão durante a consulta; certificado inválido; formulário vazio ou redirecionado; limite de tamanho; rotação de tela. Para a futura automação: senha errada, sessão expirada, reconexão, reinício, Doze e restrições do HyperOS. A autenticação real só poderá ser considerada validada depois desses testes dentro da rede.

## Atualização 0.2.0: evidência no aparelho e primeiro login assistido

O usuário confirmou Android 15/API 35 e SSID UFDPar-A. Com VPN ativa, a consulta vinculada ao Wi-Fi falhou com SocketException/ErrnoException EPERM. Sem VPN, o mesmo diagnóstico retornou HTTP 200 com certificado válido, apesar de a sessão já estar autenticada. Isso aponta para uma restrição no acesso direto com VPN ativa, não para rejeição por já estar logado. A configuração específica da VPN/Android ainda não foi identificada.

O formulário observado contém `user`, `passwd`, `ok` e campos ocultos `buttonClicked`, `redirect_url`, `err_flag`, `inputStr`, `escapeUser`, `preauthid`, com onze scripts. Ausência de atributo `onsubmit` não significa ausência de handlers registrados via JavaScript. Um exemplo público de Palo Alto mostra preparação de preauthid e escapeUser em handler de submit, mas não comprova o código exato desta universidade.

Decisão revisada: entregar primeiro login assistido via WebView restrito à origem HTTPS do portal, que executa os scripts reais e aciona o botão nativo. O usuário pode salvar credenciais criptografadas com AES-GCM/Android Keystore. Não se implementou um POST adivinhado. A versão 0.2.0 não inclui monitoramento em segundo plano nem confirmação automática de sucesso; o usuário confere a resposta do portal. A automação contínua ainda depende de teste do login e análise do fluxo real.

Testes pendentes: login com sessão expirada e VPN desativada, senha incorreta sem repetição, recarregamento após erro, rotação/fechamento da tela, perda do Wi-Fi, armazenamento/recuperação/exclusão das credenciais no Keystore e compatibilidade dos scripts com bloqueio de recursos externos. Não solicitar saída forçada da sessão atual apenas para testar.

## Versão 0.3.0

O usuário confirmou saída da sessão e login assistido funcionando. Implementado serviço em primeiro plano iniciado pela tela visível, tipos location e connectedDevice, com permissão precisa para SSID e notificação obrigatória no fluxo. Não solicita localização em segundo plano nem reinício no boot. Reutiliza o script do login validado em WebView com diretório de dados e processo separados, vinculando só esse processo ao Wi-Fi. Falhas pausam a automação; credenciais ausentes e VPN ativa são comunicadas por notificação. O comportamento real em background e tela apagada permanece pendente de validação no HyperOS.

## Versão 0.4.0: acesso e continuidade opcionais

Implementados widget e TileService acionados pelo usuário, via Activity breve e transparente. Isso permite iniciar o serviço com permissões em uso sem depender de lançamento arbitrário do segundo plano. Recuperação com START_STICKY e inicialização no boot são opções desligadas por padrão. O receiver de boot não inicia serviço location sem ACCESS_BACKGROUND_LOCATION; oferece lembrete quando não consegue. Parada explícita desativa retomadas. A pausa por erro é persistida e preservada na recuperação. O serviço não é protegido contra Forçar parada, e políticas do HyperOS continuam relevantes.

## Versão 0.5.0: widget compacto e estado atualizado

O widget foi reduzido para uma célula aproximadamente 180×56 dp, com gradiente e duas linhas. Atualizações periódicas consultam `ConnectivityManager.getNetworkCapabilities` para perceber logout mesmo quando `NetworkCallback` não emite novo evento. O widget mostra “Aguardando Wi-Fi”, “Internet disponível”, “Atenção necessária” ou a etapa curta informada pelo serviço. Toque alterna conexão/pausa por padrão; essa ação e o detalhe podem ser desligados em Ajustes. A tela inicial mantém diagnóstico recolhido para reduzir poluição visual.
