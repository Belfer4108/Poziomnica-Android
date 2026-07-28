@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.poziomnica.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.poziomnica.navigation.Routes
import com.poziomnica.viewmodel.HomeViewModel

data class HomeTile(val title: String, val route: String, val icon: ImageVector, val color: Color)

@Composable
fun HomeScreen(nav: NavHostController, viewModel: HomeViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val tiles = listOf(
        HomeTile("Poziomnica", Routes.LINEAR, Icons.Default.Straighten, Color(0xFF18A058)),
        HomeTile("Powierzchnia", Routes.SURFACE, Icons.Default.Adjust, Color(0xFF2F80ED)),
        HomeTile("Pion", Routes.PLUMB, Icons.Default.VerticalAlignCenter, Color(0xFFE58A00)),
        HomeTile("Aparat", Routes.CAMERA, Icons.Default.PhotoCamera, Color(0xFF7A5CFF)),
        HomeTile("Spadek", Routes.SLOPE, Icons.Default.TrendingDown, Color(0xFFD64B4B)),
        HomeTile("Kątomierz", Routes.PROTRACTOR, Icons.Default.Architecture, Color(0xFF00897B)),
        HomeTile("Luksomierz", Routes.LIGHT, Icons.Default.LightMode, Color(0xFFFFB300)),
        HomeTile("Przeliczniki", Routes.CALCULATORS, Icons.Default.Calculate, Color(0xFF5D6D7E))
    )
    val bg = if (settings.darkTheme) {
        Brush.verticalGradient(listOf(Color(0xFF07100D), Color(0xFF101719), MaterialTheme.colorScheme.background))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFDCEFE4), Color(0xFFF5F8F4), MaterialTheme.colorScheme.background))
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Poziomnica") },
                actions = { AppMenu(nav) }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(bg).padding(14.dp)) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = if (settings.darkTheme) 0.72f else 0.86f),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Poziomnica", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Pomiar lokalny z czujników telefonu. Bez konta, bez internetu.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(columns = GridCells.Adaptive(142.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tiles) { tile ->
                    Card(
                        onClick = { nav.navigate(tile.route) { launchSingleTop = true } },
                        modifier = Modifier.height(108.dp),
                        colors = CardDefaults.cardColors(containerColor = tile.color.copy(alpha = if (settings.darkTheme) 0.18f else 0.08f)),
                        border = BorderStroke(0.6.dp, tile.color.copy(alpha = if (settings.darkTheme) 0.20f else 0.14f))
                    ) {
                        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Surface(color = tile.color.copy(alpha = if (settings.darkTheme) 0.24f else 0.18f), shape = MaterialTheme.shapes.medium) {
                                Icon(tile.icon, null, Modifier.padding(8.dp).size(26.dp), tint = tile.color)
                            }
                            Text(tile.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

fun NavHostController.navigateRoot(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { inclusive = false }
        launchSingleTop = true
    }
}
