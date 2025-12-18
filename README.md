# Epic Store - Enhanced Edition v4.0

App Android em Kotlin para visualizar e baixar jogos da biblioteca da Epic Games Store.

## 🎮 Recursos

### ✨ Novo na v4.0
- 🎯 **Tela inicial de permissões** - Gerenciamento centralizado de permissões
- 📊 **Sistema de downloads robusto** - Baseado no Legendary Launcher
- 📈 **Progresso em tempo real** - Velocidade, tamanho, porcentagem
- 🔄 **Retomada de downloads** - Continue de onde parou mesmo após fechar o app
- 📏 **Tamanho do jogo** - Mostra o tamanho real obtido do manifest
- 💾 **Persistência de estado** - Room Database para salvar progresso
- 🚀 **Tela de downloads** - Veja todos os downloads em andamento
- ⚡ **Velocidade em tempo real** - Cálculo preciso de MB/s

### v3.0 - Material You
- 🎨 **Material You (Material Design 3)** - Design moderno e bonito
- 🌈 **Tema claro/escuro** - Suporte completo com dynamic colors
- 🖼️ **Imagens funcionando** - Sistema de fallback inteligente
- 📱 **Edge-to-edge** - Interface moderna sem bordas

### Funcionalidades Base
- ✅ Login via OAuth Device Code Flow
- 📚 Visualização da biblioteca de jogos
- 🔐 Device Auth - Login permanente
- 🔄 Pull-to-refresh

## 🔧 Como Funciona o Sistema de Downloads

### Baseado no Legendary Launcher

O sistema implementa os mesmos conceitos do Legendary:

#### 1. **Resume File** (.resume)
- Salva hash:filename de cada arquivo completo
- Verifica integridade ao retomar
- Permite continuar exatamente de onde parou

#### 2. **Cálculo de Velocidade**
```kotlin
speed = bytesSinceLastUpdate / deltaTime
```
- Atualização a cada 1 segundo (UPDATE_INTERVAL_MS)
- Velocidade instantânea em MB/s
- Mesma lógica do Legendary

#### 3. **Persistência com Room Database**
- Salva estado completo do download
- Sobrevive a reinicializações do app
- Sincronizado em tempo real

#### 4. **Chunk Caching Inteligente**
- Cache de chunks em memória
- Remove chunks não mais necessários
- Otimiza uso de RAM

#### 5. **Download em Background**
- Service em foreground com notificação
- Continua mesmo com app fechado
- Notificação com progresso atualizado

## 📱 Telas

### 1. Tela de Permissões (Nova!)
- Primeira tela ao abrir o app
- Solicita permissão de armazenamento
- Visual Material You
- Pode pular (mas não poderá baixar)

### 2. Tela Principal
- Login com Epic Games
- Lista de jogos com imagens
- Menu com Downloads, Refresh, Logout
- Pull-to-refresh

### 3. Detalhes do Jogo (Melhorada!)
- Imagem em parallax
- **Tamanho do jogo** (obtido do manifest)
- Informações detalhadas
- Card de progresso (se em download)
- Botão de download
- **Retoma automaticamente** ao abrir se estava pausado

### 4. Tela de Downloads (Nova!)
- Lista de todos os downloads
- Progresso individual
- Velocidade em tempo real
- Botões pausar/retomar/cancelar
- Atualização em tempo real

## 🏗️ Arquitetura

### Camadas
```
Presentation (Activities)
    ↓
ViewModel (Estado + Lógica)
    ↓
Repository (API)
    ↓
Database (Persistência)
    ↓
Service (Downloads)
```

### Componentes Principais

#### Download System
- **DownloadService**: Gerencia downloads em background
- **DownloadManager**: Lógica de download (chunks, assembly)
- **AppDatabase**: Room database para persistência
- **DownloadState**: Estado completo do download

#### Models
- **Game**: Informações do jogo
- **DownloadState**: Estado de download
- **Manifest**: Estrutura de arquivos e chunks
- **ChunkInfo**: Informação de cada chunk

#### UI
- **PermissionsActivity**: Tela inicial de permissões
- **MainActivity**: Lista de jogos
- **GameDetailsActivity**: Detalhes + Download
- **DownloadsActivity**: Lista de downloads

## 🔧 Tecnologias

### Principais
- **Kotlin** 1.9.20
- **Material 3 (Material You)** 1.12.0
- **Room Database** 2.6.1 - Persistência
- **WorkManager** 2.9.0 - Background tasks
- **KSP** 1.9.20-1.0.14 - Kotlin Symbol Processing

### Networking
- **Retrofit** 2.11.0
- **OkHttp** 4.12.0
- **Glide** 4.16.0

### AndroidX
- **Core KTX** 1.13.1
- **AppCompat** 1.7.0
- **Lifecycle** 2.8.4
- **Coroutines** 1.8.1

## 📡 APIs Utilizadas

- **OAuth**: `account-public-service-prod03.ol.epicgames.com`
- **Library**: `library-service.live.use1a.on.epicgames.com`
- **Catalog**: `catalog-public-service-prod06.ol.epicgames.com`
- **Launcher**: `launcher-public-service-prod06.ol.epicgames.com`

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

APK: `app/build/outputs/apk/debug/app-debug.apk`

## 💾 Estrutura de Arquivos

### Download Directory
```
/storage/emulated/0/EpicStoreHG/
├── {appName}/              # Pasta do jogo
│   ├── .resume             # Arquivo de retomada
│   ├── file1.pak
│   ├── file2.pak
│   └── ...
```

### Resume File Format
```
sha1hash:filename
sha1hash:filename
...
```

## 🎯 Fluxo de Download

1. **Usuário clica em Download**
2. **Verifica permissões**
3. **Busca manifest** do jogo
4. **Calcula tamanho total**
5. **Cria DownloadState** no banco
6. **Baixa chunks únicos**
7. **Atualiza progresso** a cada 1s
8. **Calcula velocidade** instantânea
9. **Monta arquivos** a partir dos chunks
10. **Salva em .resume** cada arquivo completo
11. **Limpa cache** de chunks não mais necessários
12. **Finaliza** e marca como completo

### Se o App Fechar
1. **Estado salvo** no Room Database
2. **Arquivos completos** salvos em .resume
3. **Ao abrir novamente** - mostra progresso
4. **Ao retomar** - lê .resume
5. **Continua** exatamente de onde parou

## 🎨 Material You Design

### Color System
- Dynamic colors baseados em Epic Blue (#0078F2)
- Light/Dark theme automático
- Surface tints e elevation overlays

### Components
- Cards com 20dp corner radius
- Buttons com 16dp corner radius
- Material 3 Typography
- Circular e Linear progress indicators

### Layouts
- Edge-to-edge
- Collapsing toolbar com parallax
- Constraint layouts otimizados
- Proper spacing e padding

## 📝 Logs Detalhados

O app gera logs completos para debug:

```
D/EpicGamesRepository: ✓ Image loaded for Fortnite
D/DownloadService: Manifest: fortnite v25.20
D/DownloadService: Files: 5432, Chunks: 2341, Size: 45234 MB
D/DownloadService: Downloading chunks: 234/2341 (15.2MB/s)
D/DownloadService: Resuming download, 234 files already completed
```

## 🔒 Segurança

- Armazenamento seguro com EncryptedSharedPreferences
- HTTPS em todas as conexões
- Tokens salvos de forma segura
- Manifest integrity checks

## 📱 Como Usar

### Primeira Vez
1. Abra o app
2. **Tela de Permissões** aparece
3. Conceda permissão de armazenamento
4. Faça login com Epic Games
5. Veja sua biblioteca

### Download de Jogos
1. Toque em um jogo
2. Veja detalhes e tamanho
3. Toque em "DOWNLOAD"
4. Acompanhe progresso na notificação
5. Veja detalhes em "Downloads" no menu

### Retomar Download
1. Se o app fechar durante download
2. Abra o app novamente
3. Vá em "Downloads" ou nos detalhes do jogo
4. O progresso é mostrado
5. Toque para continuar

## 🐛 Debug

### Logs Úteis
- `adb logcat -s DownloadService`
- `adb logcat -s EpicGamesRepository`
- `adb logcat -s GameDetailsActivity`

### Verificar Downloads
```bash
adb shell ls -lh /storage/emulated/0/EpicStoreHG/
adb shell cat /storage/emulated/0/EpicStoreHG/{appName}/.resume
```

## 🎯 Inspiração

- [Legendary Launcher](https://github.com/derrod/legendary) - Sistema de downloads
  - Resume file system
  - Chunk caching
  - Speed calculation
  - Progress tracking

## 📜 Licença

Para fins educacionais e demonstrativos.

---

**Made with ❤️ - Epic Store Enhanced Edition**
