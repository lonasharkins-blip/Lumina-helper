# Lumina Helper

Aplicativo Android original para mapear instrumentos virtuais exibidos na tela e reproduzir músicas por meio de toques controlados pelo usuário.

## Objetivo

O Lumina Helper será capaz de:

- importar músicas em formato MIDI;
- criar perfis de instrumentos com qualquer quantidade de teclas;
- mapear teclas em qualquer posição da tela;
- atender instrumentos pequenos, como o piano de emoji do JJS;
- atender layouts maiores, incluindo instrumentos de 15 e 21 teclas;
- ajustar velocidade, transposição, oitava e trilhas;
- iniciar, pausar e encerrar a reprodução por um controle flutuante;
- salvar configurações separadas para cada jogo e instrumento.

O projeto é desenvolvido do zero. Não utiliza código, identidade visual, músicas ou arquivos do Dodo Music.

## Estado atual

A primeira etapa cria a fundação Android e o modelo de instrumentos configuráveis. Consulte [PROJECT_STATUS.md](PROJECT_STATUS.md) para acompanhar exatamente o que já funciona.

## Tecnologia

- Android 7.0 ou mais recente (API 24+)
- Kotlin
- Jetpack Compose
- Serviço de acessibilidade usado somente após ativação explícita

## Compilação

O fluxo **Build APK** compila automaticamente um APK de teste no GitHub Actions. O APK gerado aparece nos artefatos da execução.

Para compilar localmente, use JDK 17, Android SDK 36 e Gradle 8.13:

```bash
gradle :app:assembleDebug
```

O APK será criado em `app/build/outputs/apk/debug/app-debug.apk`.
