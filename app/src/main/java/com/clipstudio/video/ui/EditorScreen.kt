package com.clipstudio.video.ui

import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.clipstudio.video.viewmodel.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onPickVideo: () -> Unit,
    onPickMusic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }

    LaunchedEffect(state.videoUri) {
        if (state.videoUri != null) {
            exoPlayer.setMediaItem(MediaItem.fromUri(state.videoUri!!))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            viewModel.updatePlaybackPosition(exoPlayer.currentPosition.coerceAtLeast(0L))
            delay(100L) // Más rápido para mejor sincronización
        }
    }

    val infiniteTransition = rememberInfiniteTransition()
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse)
    )

    val composeFontFamily = when (state.subtitleFontFamily) {
        SubtitleFontFamilyType.SANS -> FontFamily.SansSerif
        SubtitleFontFamilyType.SERIF -> FontFamily.Serif
        SubtitleFontFamilyType.MONO -> FontFamily.Monospace
        SubtitleFontFamilyType.CURSIVE -> FontFamily.Cursive
    }
    val (composeFontWeight, composeFontStyle) = when (state.subtitleFontStyle) {
        SubtitleFontStyleType.NORMAL -> FontWeight.Normal to FontStyle.Normal
        SubtitleFontStyleType.BOLD -> FontWeight.Bold to FontStyle.Normal
        SubtitleFontStyleType.ITALIC -> FontWeight.Normal to FontStyle.Italic
        SubtitleFontStyleType.BOLD_ITALIC -> FontWeight.Bold to FontStyle.Italic
    }

    val subtitleColors = listOf(0xFFFFFFFF, 0xFFFFD60A, 0xFF00E5FF, 0xFF34D399, 0xFFF472B6)

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("ClipStudio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text("Cortes • Música • Letras animadas", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onPickVideo) {
                Icon(Icons.Rounded.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Video")
            }
        }

        state.statusMessage?.let { msg ->
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(12.dp, 8.dp))
            }
        }

        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth().height(250.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (state.videoUri != null) {
                    AndroidView(
                        factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = true } },
                        update = { view -> view.player = exoPlayer },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("Selecciona un clip local para comenzar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (state.subtitleTextPreview.isNotEmpty()) {
                    val subModifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 50.dp, start = 20.dp, end = 20.dp).let { base ->
                        when (state.subtitleAnimation) {
                            SubtitleAnimation.NONE -> base
                            SubtitleAnimation.FADE -> base.alpha(0.45f + 0.55f * animProgress)
                            SubtitleAnimation.POP -> base.scale(0.92f + 0.12f * animProgress)
                            SubtitleAnimation.SLIDE_UP -> base.graphicsLayer { translationY = (1f - animProgress) * -14f }
                            SubtitleAnimation.HIGHLIGHT -> base.scale(0.97f + 0.05f * animProgress)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(state.subtitleBgColorHex),
                        border = if (state.subtitleAnimation == SubtitleAnimation.HIGHLIGHT) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = subModifier
                    ) {
                        Text(
                            text = state.subtitleTextPreview,
                            color = Color(state.subtitleColorHex),
                            fontFamily = composeFontFamily,
                            fontWeight = composeFontWeight,
                            fontStyle = composeFontStyle,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        if (state.currentSyncIndex >= 0) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sincronizando línea ${state.currentSyncIndex + 1} de ${state.pendingSyncLines.size}", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.pendingSyncLines.getOrNull(state.currentSyncIndex) ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.markCurrentLine() },
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Rounded.TouchApp, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("¡MARCAR AHORA!", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.cancelSync() }) { Text("Cancelar sincronización") }
                }
            }
        } else {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lyrics, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Letras de la canción (Reel)", style = MaterialTheme.typography.titleMedium)
                    }

                    OutlinedTextField(
                        value = state.manualLyricsText,
                        onValueChange = { viewModel.updateManualLyricsText(it) },
                        label = { Text("Pega la letra aquí (una frase por línea)") },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        maxLines = 10
                    )

                    Button(
                        onClick = { viewModel.prepareLyricsSync() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.manualLyricsText.isNotBlank() && state.videoUri != null
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sincronizar letra con el video")
                    }
                    
                    if (state.generatedSubtitles.isNotEmpty()) {
                        Text("${state.generatedSubtitles.size} líneas sincronizadas correctamente.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Estilo y animación del Reel", style = MaterialTheme.typography.titleMedium)
                
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubtitleFontFamilyType.entries.forEach { fontType ->
                        val selected = state.subtitleFontFamily == fontType
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                            modifier = Modifier.clickable { viewModel.selectSubtitleFontFamily(fontType) }
                        ) { Text(fontType.label, modifier = Modifier.padding(10.dp, 6.dp)) }
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubtitleAnimation.entries.forEach { anim ->
                        val selected = state.subtitleAnimation == anim
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                            modifier = Modifier.clickable { viewModel.selectSubtitleAnimation(anim) }
                        ) { Text(anim.label, modifier = Modifier.padding(10.dp, 6.dp)) }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    subtitleColors.forEach { hex ->
                        Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(hex)).clickable { viewModel.selectSubtitleColor(hex) })
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}
