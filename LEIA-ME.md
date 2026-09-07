# Watv Player — primeira versão 0.1

Projeto Android nativo para celular e TV Box Android, em português. Este pacote contém o código-fonte; não contém um APK pronto.

## Implementado

- Entrada por URL M3U ou arquivo M3U.
- Xtream Codes com servidor, usuário e senha, usando a exportação M3U (get.php). O provedor precisa disponibilizar esse endpoint.
- Busca por nome, filtro de categoria e favoritos locais.
- Player Media3 com HLS, DASH e formatos progressivos compatíveis com o aparelho.
- Botões nativos com foco para controle remoto, controles de reprodução e nova tentativa.
- Ícone provisório e entrada no launcher Android/Android TV.
- Liberação do player ao sair e reconexão ao retornar.

## Gerar o APK

1. Instale Android Studio e JDK 17. No SDK Manager, instale Android SDK Platform 35 e Build Tools 35.0.0.
2. Instale Gradle 8.11.1 (https://gradle.org/releases/) e adicione sua pasta bin ao PATH.
3. Extraia o ZIP e abra a pasta WatvPlayer no Android Studio.
4. O pacote não inclui o Gradle Wrapper binário. No terminal dessa pasta, execute: gradle wrapper --gradle-version 8.11.1
5. Sincronize o projeto. É necessário acesso à internet para baixar dependências.
6. Conecte o celular/TV Box com depuração habilitada e execute o módulo app.
7. Para gerar APK de teste no Windows: .\gradlew.bat assembleDebug
   Em Linux/macOS: ./gradlew assembleDebug
8. O APK será criado em app/build/outputs/apk/debug/app-debug.apk.
9. Para distribuição, use Build > Generate Signed Bundle / APK e proteja a chave de assinatura.

Não há chave de assinatura nem conta de publicação incluída. Identificador provisório: com.watv.player. Requer Android 7.0 ou superior; codecs dependem do aparelho.

## Usar

Escolha Lista M3U e informe a URL completa ou escolha Xtream Codes e preencha servidor (com http/https e porta), usuário e senha diretamente no aparelho.

Selecione um canal para assistir. Na tela do player, use Favoritar. Na TV, use setas e OK; Voltar retorna à lista. O botão Todos os canais alterna para favoritos.

## Dados e limites

Credenciais e URLs ficam apenas na memória da sessão. É necessário entrar novamente após o processo ser encerrado. Só os identificadores SHA-256 dos favoritos são persistidos; alterar a URL de um canal muda seu identificador. Não há telemetria nem servidor próprio do Watv.

HTTP é permitido para compatibilidade; prefira HTTPS, pois HTTP transmite dados sem criptografia. Credenciais Xtream são enviadas na URL de get.php ao servidor informado.

Sem EPG, organização específica de filmes/séries, DRM, Chromecast, login persistente, múltiplos perfis ou cabeçalhos personalizados de provedores. Arquivos locais precisam conter URLs HTTP/HTTPS absolutas. Limite de lista: 20 MB. Um manifesto HLS individual não é uma lista de canais.

## Validação

O leitor de listas e a rede passaram em 11 verificações automatizadas com Java 17, cobrindo nomes/categorias, URLs, duplicatas, identificação de favoritos, codificação das credenciais, respostas inválidas, HLS, redirecionamento HTTP e recusa de acesso.

XMLs verificados quanto à sintaxe. Não foi possível compilar o projeto Android ou executar em aparelho: este ambiente não tem SDK Android/Gradle. Não foi testada uma fonte real do usuário. Interface, reprodução, ciclo de vida e foco do controle remoto ainda precisam de validação em aparelhos.

Para repetir os testes na raiz do projeto:
    javac -d build-tests app/src/main/java/com/watv/player/Playlist.java tests/PlaylistTest.java
    java -cp build-tests com.watv.player.PlaylistTest

Antes de distribuir, testar: fontes reais M3U/Xtream; senha inválida; perda de rede; favoritos após reconectar; rotação; saída e retorno; setas/OK/Voltar na TV; HLS/TS do provedor. O banner é provisório.

## Referências

- Player: https://developer.android.com/media/media3/exoplayer/hello-world
- Ciclo de vida: https://developer.android.com/media/implement/playback-app
- AGP: https://developer.android.com/build/releases/agp-8-9-0-release-notes
- Media3: https://developer.android.com/jetpack/androidx/releases/media3

Dependências fixadas para reprodução do projeto, sem alegação de serem as mais recentes.

## Compilação pelo GitHub Actions

A configuração .github/workflows/android.yml está preparada, mas ainda não foi executada.
Publique o conteúdo da pasta WatvPlayer na raiz de um repositório, na branch main.
Um envio para main ou a opção Actions > Gerar APK Watv Player > Run workflow iniciará a compilação.
Após sucesso, o artefato Watv-Player-APK conterá o APK debug assinado e seu SHA-256.
A chave debug do runner é temporária: builds futuros podem exigir desinstalar a versão anterior, perdendo favoritos.
Esse fluxo não publica o aplicativo em lojas.
