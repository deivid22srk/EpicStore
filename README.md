# Epic Store

App Android em Kotlin para visualizar jogos da biblioteca da Epic Games Store.

## Recursos

- 🎮 Login com conta Epic Games via OAuth2
- 📚 Visualização da biblioteca de jogos
- 🎨 Material You Design / Material Icons
- 🔐 Autenticação segura via navegador padrão
- 🔄 Atualização da biblioteca com pull-to-refresh
- 💾 Armazenamento seguro de tokens com EncryptedSharedPreferences

## Tecnologias

- **Kotlin** - Linguagem principal
- **Material 3 (Material You)** - Design System
- **Retrofit** - Requisições HTTP
- **OkHttp** - Cliente HTTP
- **Coroutines** - Programação assíncrona
- **ViewModel** - Arquitetura MVVM
- **Chrome Custom Tabs** - Navegador para autenticação
- **Encrypted SharedPreferences** - Armazenamento seguro

## Como funciona a autenticação

O app utiliza o fluxo OAuth2 Authorization Code da Epic Games:

1. Usuário clica em "Login with Epic Games"
2. O app abre o navegador padrão com a página de login da Epic
3. Usuário faz login e autoriza o app
4. Epic Games redireciona para `epicstore://callback` com código de autorização
5. O app troca o código por um access token
6. Token é armazenado de forma segura e usado nas requisições da API

## Endpoints utilizados

- **OAuth**: `https://account-public-service-prod03.ol.epicgames.com/`
- **Library**: `https://library-service.live.use1a.on.epicgames.com/`
- **Catalog**: `https://catalog-public-service-prod06.ol.epicgames.com/`

## Build

### Requisitos

- Android Studio Hedgehog ou superior
- JDK 17
- Android SDK 26+ (Oreo)
- Target SDK 34

### Compilar

```bash
./gradlew assembleDebug
```

O APK será gerado em: `app/build/outputs/apk/debug/app-debug.apk`

## GitHub Actions

O projeto inclui um workflow de CI que:
- Compila o APK de debug automaticamente
- Disponibiliza o APK como artifact
- Executa em push/pull request na branch main

## Licença

Este projeto é apenas para fins educacionais e demonstrativos.

## Inspiração

Baseado no [Legendary Launcher](https://github.com/derrod/legendary) - Launcher alternativo open-source para Epic Games Store.
