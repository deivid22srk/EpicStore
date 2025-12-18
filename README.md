# Epic Store

App Android em Kotlin para visualizar jogos da biblioteca da Epic Games Store.

## Recursos

- 🎮 Login com conta Epic Games via OAuth2
- 📚 Visualização da biblioteca de jogos
- 🎨 **Material You (Material 3) Design 1.12.0** - Versão mais recente
- 🎯 **Material Icons** mais recentes
- 🔐 Autenticação segura via navegador padrão
- 🔄 Atualização da biblioteca com pull-to-refresh
- 💾 Armazenamento seguro de tokens com EncryptedSharedPreferences
- 🛡️ Tratamento robusto de exceções

## Tecnologias

- **Kotlin** 1.9.20
- **Material 3 (Material You) 1.12.0** - Versão mais recente
- **AndroidX Core KTX** 1.13.1
- **AppCompat** 1.7.0
- **Lifecycle** 2.8.4
- **Coroutines** 1.8.1
- **Retrofit** 2.11.0 - Versão mais recente
- **OkHttp** 4.12.0
- **Chrome Custom Tabs** 1.8.0
- **Encrypted SharedPreferences** com fallback seguro

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
chmod +x gradlew
./gradlew assembleDebug
```

O APK será gerado em: `app/build/outputs/apk/debug/app-debug.apk`

## Correções Implementadas

### v1.1 - Correções de Estabilidade

✅ **Dependências atualizadas para versões mais recentes:**
- Material 1.11.0 → 1.12.0 (Material You mais recente)
- Core KTX 1.12.0 → 1.13.1
- AppCompat 1.6.1 → 1.7.0
- Lifecycle 2.7.0 → 2.8.4
- Coroutines 1.7.3 → 1.8.1
- Retrofit 2.9.0 → 2.11.0
- Browser 1.7.0 → 1.8.0
- CoordinatorLayout adicionado

✅ **Tratamento de exceções:**
- Try-catch em EncryptedSharedPreferences com fallback
- Logs de erro detalhados
- Tratamento de erros em todas as operações críticas

✅ **Melhorias de estabilidade:**
- Inicialização lazy do SharedPreferences
- Fallback para SharedPreferences regular se EncryptedSharedPreferences falhar
- Tratamento robusto de erros de rede

## GitHub Actions

O workflow `build.yml` está configurado para:
- Compilar APK de debug automaticamente
- Upload do APK como artifact
- JDK 17 + Gradle 8.2

## Licença

Este projeto é apenas para fins educacionais e demonstrativos.

## Inspiração

Baseado no [Legendary Launcher](https://github.com/derrod/legendary) - Launcher alternativo open-source para Epic Games Store.
