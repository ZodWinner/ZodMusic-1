# ZodMusic

Player de músicas para Android (Kotlin, Jetpack Compose, Media3, Room). Código original, sem anúncios.

## O que o app faz

- Lê as músicas do aparelho e toca em segundo plano, com notificação e controles na tela de bloqueio.
- Troca a capa de qualquer música ou playlist por uma imagem da galeria. A imagem é copiada para a pasta do app.
- Cria playlists, adiciona músicas e muda a ordem com os botões de subir e descer.
- Mini player na parte de baixo e tela de player com barra de progresso.

## Como compilar

1. Instale o Android Studio (versão recente).
2. Abra a pasta `ZodMusic` com **File > Open**. O Studio baixa o Gradle e as dependências sozinho.
3. Conecte o celular com depuração USB ativada, ou use um emulador.
4. Clique em **Run**.

Para gerar um APK: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

## Estrutura

| Arquivo | Função |
| --- | --- |
| `data/Db.kt` | Banco Room: playlists, ordem das músicas e capas personalizadas |
| `data/MediaRepository.kt` | Leitura das músicas (MediaStore) e gravação das capas |
| `PlaybackService.kt` | Serviço de reprodução em segundo plano |
| `PlayerController.kt` | Liga a interface ao serviço |
| `MainViewModel.kt` | Estado e ações do app |
| `ui/Screens.kt` | Todas as telas |

## Próximos passos possíveis

- Arrastar e soltar para reordenar (hoje são botões).
- Fila de reprodução, embaralhar e repetir.
- Editar título e artista das músicas.
- Ícone próprio do app.
