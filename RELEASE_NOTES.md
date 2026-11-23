## v0.23.2-beta

### Novidades

- **Busca integrada funcionando de verdade**: a aba Busca agora encontra músicas. A consulta usa o NewPipe (mesma engine do app NewPipe) e, se ele falhar ou vier vazio, cai pro yt-dlp embutido antes de desistir.
- **Erro honesto na tela**: se a busca falhar nos dois caminhos, o motivo aparece na tela com botão "Tentar de novo". "Nada encontrado" só aparece quando é verdade.
- **Duração da faixa**: cada resultado mostra o tempo da música quando essa informação está disponível.

### Correções

- Corrigida a busca integrada, que não achava nenhuma música (a aba nem chegou a rodar de fato nas versões anteriores).
- Corrigido o botão de download dos resultados da busca.
- **Spinner de download no resultado**: enquanto baixa, o card mostra a bolinha girando com a fase (Preparando…, Na fila, Processando arquivo) ou o percentual real (45% · 2,3 MB/s), igual ao da Central de Downloads. Se falhar, mostra o motivo curtinho e volta com o botão "Tentar de novo"; concluído ou cancelado, o card volta ao normal.
- Toque no resultado que já está baixando não enfileira o mesmo vídeo duas vezes.
- Corrigida a compilação do APK de release (o botão de download dos resultados derrubava o build).

### Sobre a versão

- Número interno corrigido: o app agora se identifica como 0.23.2-beta (código 101) — o APK da release v0.23.1-beta anterior se identificava internamente como 0.23.0.
- A beta continua podendo ser instalada ao lado da versão estável (ID de aplicativo diferente).
- Esta é uma versão BETA e pode conter bugs ou comportamentos inesperados. Para reportar problemas, use o rastreador de issues do repositório.
