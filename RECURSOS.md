# Watv Player 0.3 — recursos e limites

Aplicativo próprio inspirado nas funções identificadas estaticamente no APK HeroPlay fornecido pelo usuário. Não incorpora o código, as imagens, as credenciais nem os serviços desse APK. A análise estática encontrou nomes de telas, módulos e textos; ela não comprova que todos os recursos do HeroPlay funcionam nem garante um inventário completo.

## Implementados nesta versão

- URL M3U, arquivo M3U e Xtream via exportação get.php.
- Gzip, UTF-8/UTF-16/Windows-1252, linhas CR/LF, listas sem cabeçalho, redirecionamentos, URLs com espaços e diagnóstico sem expor credenciais.
- tvg-id, tvg-logo e cabeçalhos User-Agent/Referer de EXTVLCOPT e sufixos de URL. Logos/capas fornecidos por tvg-logo são exibidos no catálogo, com cache e limite de imagem.
- Múltiplas listas persistidas com AES-GCM e chave no Android Keystore; ativação local, atualização, renomeação, edição de URL e remoção.
- Perfis com avatar, favoritos, minha lista, coleções, histórico e progresso independentes.
- Canais ao vivo, filmes e séries classificados por URL/categoria/nome. Agrupamento por série e ordenação de episódios quando o título contém S01E01.
- Busca, filtros e ordenação por título, uso e histórico.
- Retomar VOD e próximo episódio identificado por nome.
- EPG XMLTV/XML.GZ, com associação exata pelo tvg-id. Exige uma URL EPG válida.
- PIN global de responsável, categorias bloqueadas por perfil e limitação de tentativas. Alterações de perfil, fontes e configurações exigem PIN quando ativo.
- Player com áudio/legendas disponíveis no stream, velocidade, enquadramento, tela cheia, seleção de fontes equivalentes e limite de resolução adaptativa.
- PiP do sistema em aparelhos Android 8+ que ofereçam o recurso.
- Busca opcional de sinopse/elenco no TMDB com token de leitura próprio configurado no aparelho. O usuário escolhe o resultado correspondente.

## Não equivalentes / dependências ainda abertas

- Ativação de aparelhos e distribuição remota de listas: precisam de servidor/API próprio, regras de licença e autenticação. Não há integração com os servidores HeroPlay.
- Capas, carrosséis visuais, grade EPG em linha do tempo e mini player flutuante dentro do catálogo não estão reproduzidos; o mini player atual é PiP.
- Adaptação automática de qualidade ocorre entre faixas do mesmo stream quando fornecidas. Troca entre URLs de canais equivalentes é manual.
- Separação de temporadas depende do padrão SxxExx do provedor; não há consumo completo de endpoints nativos de séries Xtream.
- O player é Media3, não mpv; formatos e codecs disponíveis podem diferir conforme o aparelho.
- Não inclui tradução completa da interface, DRM, gravação, catch-up, download offline de vídeos, backup portátil nem integração Chromecast. A presença desses recursos não foi confirmada no APK de referência.
- Limite de resposta: 20 MB descompactados. Fontes maiores precisam ser divididas.
- Atualização dos APKs debug pode exigir desinstalar o anterior, apagando seus dados. A assinatura de distribuição estável precisa ser configurada antes de uso comercial.

## Validação

O workflow executa testes Java de parsing/rede, compilação, testes instrumentados Android 11 para telas/filtros/perfis/armazenamento/EPG/PIN e verificação da assinatura.
Consulte o resultado do workflow para saber se todas as etapas passaram.
A fonte real do usuário e aparelhos físicos ainda precisam de teste; não foram fornecidos dados de acesso.
