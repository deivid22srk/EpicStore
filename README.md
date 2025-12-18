# Epic Store

App Android em Kotlin para visualizar jogos da biblioteca da Epic Games Store.

## 🎮 Recursos

- ✅ Login com conta Epic Games via **OAuth Device Code Flow**
- 📚 Visualização da biblioteca de jogos
- 🎨 **Material You (Material 3) 1.12.0** - Versão mais recente
- 🎯 **Material Icons** mais recentes
- 🌐 Autenticação via navegador padrão
- 🔐 **Device Auth** - Login permanente (não precisa fazer login toda vez)
- 🔄 Atualização da biblioteca com pull-to-refresh
- 💾 Armazenamento seguro de credenciais

## 🔑 Como funciona a autenticação

O app usa o **OAuth Device Code Flow** (mesmo método do Legendary Launcher):

1. **App cria um Device Code** usando client credentials
2. **Abre o navegador** com URL de autorização
3. **Usuário faz login** na Epic Games e autoriza
4. **App faz polling** aguardando autorização (a cada 10 segundos)
5. **Recebe access token** quando usuário autoriza
6. **Troca para Android token** usando exchange code
7. **Cria Device Auth permanente** (deviceId + secret)
8. **Próximos logins** usam device_auth automaticamente

### Por que Device Code Flow?

- ✅ **Não precisa redirect_uri** - funciona sem servidor
- ✅ **Método oficial** da Epic Games para dispositivos
- ✅ **Login permanente** com device_auth
- ✅ **Mesmo método** do Legendary Launcher
- ✅ **Funciona 100%** - testado e comprovado

## 🔧 Tecnologias

- **Kotlin** 1.9.20
- **Material 3 (Material You) 1.12.0** - Versão mais recente
- **AndroidX Core KTX** 1.13.1
- **AppCompat** 1.7.0
- **Lifecycle** 2.8.4
- **Coroutines** 1.8.1
- **Retrofit** 2.11.0 - Versão mais recente
- **OkHttp** 4.12.0
- **Chrome Custom Tabs** 1.8.0
- **CoordinatorLayout** 1.2.0

## 📡 Endpoints utilizados

- **OAuth**: `https://account-public-service-prod03.ol.epicgames.com/`
- **Library**: `https://launcher-public-service-prod06.ol.epicgames.com/`

## 🚀 Build

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

## 📝 Changelog

### v2.0 - Device Code Flow (ATUAL)

✅ **Método de autenticação completamente reescrito:**
- OAuth Device Code Flow (método oficial Epic Games)
- Não usa redirect_uri (resolve erro "redirectUrl inválido")
- Usa tokens SWITCH e ANDROID (mesmo do Legendary)
- Device Auth permanente (não precisa login toda vez)
- Polling automático aguardando autorização
- Exchange para Android token

✅ **Biblioteca de jogos:**
- Endpoint correto: `launcher-public-service-prod06.ol.epicgames.com`
- Função `get_owned_games()` implementada
- Lista todos os jogos da biblioteca do usuário

### v1.2 - Correções de Tema

✅ Tema alterado para `.NoActionBar`
✅ Toolbar customizada

### v1.1 - Dependências

✅ Material 1.12.0 mais recente
✅ CoordinatorLayout adicionado
✅ Try-catch robusto

## 🎯 Baseado em scripts Python que funcionam

Este app foi reescrito usando o **MESMO método** das scripts Python que você forneceu:
- `auth_helper.py` - Device Code Flow
- `epic_games.py` - API calls e device_auth_login
- Tokens SWITCH e ANDROID confirmados

## 📱 Como usar

1. Abra o app
2. Clique em "Login with Epic Games"
3. Navegador abre com página de autorização
4. Faça login na Epic Games
5. Clique em "Autorizar"
6. Volte ao app - login detectado automaticamente!
7. Biblioteca de jogos carrega

## ⚙️ GitHub Actions

O workflow `build.yml` compila APK de debug automaticamente.

## 📜 Licença

Para fins educacionais e demonstrativos.

## 🙏 Inspiração

- [Legendary Launcher](https://github.com/derrod/legendary) - OAuth Device Code Flow
- Scripts Python fornecidas - Implementação funcional testada
