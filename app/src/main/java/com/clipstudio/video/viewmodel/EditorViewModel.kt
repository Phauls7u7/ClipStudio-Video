package com.clipstudio.video.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SubtitleSegment(
    val id: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String
)

enum class SubtitleFontFamilyType(val label: String) {
    SANS("Sans Moderna"),
    SERIF("Editorial Serif"),
    MONO("Monoespaciada"),
    CURSIVE("Manuscrita")
}

enum class SubtitleFontStyleType(val label: String) {
    NORMAL("Normal"),
    BOLD("Negrita"),
    ITALIC("Cursiva"),
    BOLD_ITALIC("Negrita + Cursiva")
}

enum class SubtitleAnimation(val label: String) {
    NONE("Sin animación"),
    FADE("Aparecer / desvanecer"),
    POP("Pop / rebote"),
    SLIDE_UP("Deslizar arriba"),
    HIGHLIGHT("Resaltado dinámico")
}

data class ClipSegment(val id: Int, val startMs: Long, val endMs: Long?)

data class EditorUiState(
    val videoUri: Uri? = null,
    val musicUri: Uri? = null,
    val playbackPositionMs: Long = 0L,
    val segments: List<ClipSegment> = listOf(ClipSegment(id = 1, startMs = 0L, endMs = null)),
    val saturation: Float = 1.0f,
    val brightness: Float = 0.0f,
    val contrast: Float = 1.0f,
    
    val subtitleFontFamily: SubtitleFontFamilyType = SubtitleFontFamilyType.SANS,
    val subtitleFontStyle: SubtitleFontStyleType = SubtitleFontStyleType.BOLD,
    val subtitleColorHex: Long = 0xFFFFFFFF,
    val subtitleBgColorHex: Long = 0xA6000000,
    val subtitleAnimation: SubtitleAnimation = SubtitleAnimation.POP,
    
    val manualLyricsText: String = "",
    val pendingSyncLines: List<String> = emptyList(),
    val currentSyncIndex: Int = -1,
    val generatedSubtitles: List<SubtitleSegment> = emptyList(),
    val subtitleTextPreview: String = "Tus letras aparecerán aquí",
    val statusMessage: String? = null
)

class EditorViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    fun onVideoSelected(uri: Uri?) {
        _uiState.update {
            it.copy(
                videoUri = uri,
                playbackPositionMs = 0L,
                segments = listOf(ClipSegment(id = 1, startMs = 0L, endMs = null)),
                generatedSubtitles = emptyList(),
                currentSyncIndex = -1,
                statusMessage = if (uri != null) "Video cargado" else null
            )
        }
    }

    fun onMusicSelected(uri: Uri?) {
        _uiState.update { it.copy(musicUri = uri) }
    }

    fun updatePlaybackPosition(positionMs: Long) {
        _uiState.update { state ->
            val activeSubtitle = state.generatedSubtitles.firstOrNull { seg ->
                positionMs in seg.startMs..seg.endMs
            }
            state.copy(
                playbackPositionMs = positionMs,
                subtitleTextPreview = activeSubtitle?.text ?: if (state.generatedSubtitles.isEmpty()) state.subtitleTextPreview else ""
            )
        }
    }

    fun splitClipAtCurrentPosition() {
        val currentMs = _uiState.value.playbackPositionMs
        if (currentMs <= 0L) return
        _uiState.update { state ->
            val nextId = (state.segments.maxOfOrNull { it.id } ?: 0) + 1
            val updated = state.segments.toMutableList().apply {
                val lastIndex = lastIndex
                if (lastIndex >= 0) this[lastIndex] = this[lastIndex].copy(endMs = currentMs)
                add(ClipSegment(id = nextId, startMs = currentMs, endMs = null))
            }
            state.copy(segments = updated, statusMessage = "Clip dividido")
        }
    }

    fun updateSaturation(value: Float) { _uiState.update { it.copy(saturation = value) } }
    fun updateContrast(value: Float) { _uiState.update { it.copy(contrast = value) } }
    fun resetColorAdjustments() { _uiState.update { it.copy(saturation = 1.0f, contrast = 1.0f) } }

    fun selectSubtitleFontFamily(f: SubtitleFontFamilyType) { _uiState.update { it.copy(subtitleFontFamily = f) } }
    fun selectSubtitleFontStyle(f: SubtitleFontStyleType) { _uiState.update { it.copy(subtitleFontStyle = f) } }
    fun selectSubtitleAnimation(a: SubtitleAnimation) { _uiState.update { it.copy(subtitleAnimation = a) } }
    fun selectSubtitleColor(c: Long) { _uiState.update { it.copy(subtitleColorHex = c) } }

    fun updateManualLyricsText(text: String) {
        _uiState.update { it.copy(manualLyricsText = text) }
    }

    fun prepareLyricsSync() {
        val currentState = _uiState.value
        val lines = currentState.manualLyricsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Pega primero la letra de la canción") }
            return
        }
        _uiState.update { 
            it.copy(
                pendingSyncLines = lines,
                currentSyncIndex = 0,
                generatedSubtitles = emptyList(),
                statusMessage = "Reproduce el video y marca cada línea al ritmo de la música"
            )
        }
    }

    fun markCurrentLine() {
        val currentState = _uiState.value
        if (currentState.currentSyncIndex < 0 || currentState.currentSyncIndex >= currentState.pendingSyncLines.size) return

        val currentMs = currentState.playbackPositionMs
        val currentLineText = currentState.pendingSyncLines[currentState.currentSyncIndex]
        
        val updatedSubtitles = currentState.generatedSubtitles.toMutableList()
        
        // Cerrar el tiempo de la línea anterior si existe
        if (updatedSubtitles.isNotEmpty()) {
            val last = updatedSubtitles.last()
            updatedSubtitles[updatedSubtitles.lastIndex] = last.copy(endMs = currentMs)
        }

        // Agregar la nueva línea
        updatedSubtitles.add(
            SubtitleSegment(
                id = currentState.currentSyncIndex,
                startMs = currentMs,
                endMs = currentMs + 5000L, // Por defecto dura 5 segundos o hasta que se marque la siguiente
                text = currentLineText
            )
        )

        val nextIndex = currentState.currentSyncIndex + 1
        _uiState.update { 
            it.copy(
                generatedSubtitles = updatedSubtitles,
                currentSyncIndex = if (nextIndex < it.pendingSyncLines.size) nextIndex else -1,
                statusMessage = if (nextIndex >= it.pendingSyncLines.size) "¡Sincronización terminada!" else "Línea registrada"
            )
        }
    }

    fun cancelSync() {
        _uiState.update { it.copy(currentSyncIndex = -1, statusMessage = "Sincronización detenida") }
    }
}
