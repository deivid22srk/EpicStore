package com.epicstore.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.epicstore.app.DownloadsActivityCompose
import com.epicstore.app.GameDetailsActivityCompose
import com.epicstore.app.model.Game
import com.epicstore.app.ui.components.GameCard
import com.epicstore.app.viewmodel.UiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: UiState,
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onDownloadsClick: () -> Unit
) {
    val context = LocalContext.current
    val pullToRefreshState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Epic Store") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    if (isLoggedIn) {
                        IconButton(onClick = onDownloadsClick) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Downloads",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        IconButton(onClick = onRefreshClick) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Atualizar",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        IconButton(onClick = onLogoutClick) {
                            Icon(
                                Icons.Default.Logout,
                                contentDescription = "Sair",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!isLoggedIn) {
                LoginContent(
                    onLoginClick = onLoginClick,
                    isLoading = uiState.isLoading
                )
            } else {
                if (uiState.isLoading && uiState.games == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.games != null) {
                    if (uiState.games.isEmpty()) {
                        EmptyGamesContent()
                    } else {
                        Box(
                            modifier = Modifier.nestedScroll(pullToRefreshState.nestedScrollConnection)
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.games) { game ->
                                    val gameName = game.sandboxName ?: game.appName
                                    GameCard(
                                        game = game,
                                        gameName = gameName,
                                        onClick = {
                                            val intent = Intent(context, GameDetailsActivityCompose::class.java).apply {
                                                putExtra("GAME_NAME", gameName)
                                                putExtra("APP_NAME", game.appName)
                                                putExtra("NAMESPACE", game.namespace ?: "")
                                                putExtra("CATALOG_ITEM_ID", game.catalogItemId ?: "")
                                                putExtra("IMAGE_URL", game.imageUrl)
                                            }
                                            context.startActivity(intent)
                                        }
                                    )
                                }
                            }
                            
                            if (pullToRefreshState.isRefreshing) {
                                LaunchedEffect(true) {
                                    onRefreshClick()
                                }
                            }
                            
                            LaunchedEffect(uiState.isLoading) {
                                if (!uiState.isLoading) {
                                    pullToRefreshState.endRefresh()
                                } else if (uiState.games != null) {
                                    pullToRefreshState.startRefresh()
                                }
                            }
                            
                            PullToRefreshContainer(
                                state = pullToRefreshState,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }
                    }
                }
            }
            
            uiState.error?.let { error ->
                LaunchedEffect(error) {
                    coroutineScope.launch {
                        
                    }
                }
            }
        }
    }
}

@Composable
fun LoginContent(
    onLoginClick: () -> Unit,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Epic Store",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Faça login para acessar sua biblioteca de jogos",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onLoginClick,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    "Entrar com Epic Games",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun EmptyGamesContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Nenhum jogo encontrado",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Você não possui jogos na sua biblioteca",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}
