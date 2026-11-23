package com.clipstudio.video.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class SubtitleSegment(
    val id: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String
)

sealed class TranscriptionResult {
    data class Success(val segments: List<SubtitleSegment>, val fullText: String) : TranscriptionResult()
    data class Error(val message: String) : TranscriptionResult()
}

class TranscriptionService(private val context: Context) {

    suspend fun transcribeMedia(
        mediaUri: Uri,
        apiKey: String,
        languageCode: String
    ): TranscriptionResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext TranscriptionResult.Error(
                "Configura tu API Key de transcripción en la sección de Subtítulos antes de transcribir."
            )
        }

        val inputStream = try {
            context.contentResolver.openInputStream(mediaUri)
        } catch (_: Exception) {
            null
        } ?: return@withContext TranscriptionResult.Error(
            "No se pudo leer el archivo multimedia seleccionado."
        )

        val mediaBytes = try {
            inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            return@withContext TranscriptionResult.Error(
                "Error leyendo el archivo: ${e.message ?: "desconocido"}"
            )
        }

        if (mediaBytes.isEmpty()) {
            return@withContext TranscriptionResult.Error("El archivo seleccionado está vacío.")
        }

        val boundary = "----ClipStudioBoundary${System.currentTimeMillis()}"
        val lineEnd = "\r\n"
        val twoHyphens = "--"

        var connection: HttpURLConnection? = null
        try {
            val url = URL("https://api.openai.com/v1/audio/transcriptions")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                connectTimeout = 30_000
                readTimeout = 60_000
                setRequestProperty("Authorization", "Bearer $cleanKey")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            DataOutputStream(connection.outputStream).use { out ->
                out.writeBytes(twoHyphens + boundary + lineEnd)
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"clip_audio.mp4\"$lineEnd")
                out.writeBytes("Content-Type: application/octet-stream$lineEnd$lineEnd")
                out.write(mediaBytes)
                out.writeBytes(lineEnd)

                out.writeBytes(twoHyphens + boundary + lineEnd)
                out.writeBytes("Content-Disposition: form-data; name=\"model\"$lineEnd$lineEnd")
                out.writeBytes("whisper-1$lineEnd")

                out.writeBytes(twoHyphens + boundary + lineEnd)
                out.writeBytes("Content-Disposition: form-data; name=\"language\"$lineEnd$lineEnd")
                out.writeBytes(languageCode + lineEnd)

                out.writeBytes(twoHyphens + boundary + lineEnd)
                out.writeBytes("Content-Disposition: form-data; name=\"response_format\"$lineEnd$lineEnd")
                out.writeBytes("verbose_json$lineEnd")

                out.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
                out.flush()
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = BufferedReader(InputStreamReader(stream)).use { reader ->
                reader.readText()
            }

            if (code !in 200..299) {
                return@withContext TranscriptionResult.Error(
                    "Error de transcripción (HTTP $code): ${responseBody.take(180)}"
                )
            }

            val json = JSONObject(responseBody)
            val fullText = json.optString("text", "").trim()
            val segmentsArray = json.optJSONArray("segments")
            val parsedSegments = mutableListOf<SubtitleSegment>()

            if (segmentsArray != null) {
                for (i in 0 until segmentsArray.length()) {
                    val segObj = segmentsArray.optJSONObject(i) ?: continue
                    val startSec = segObj.optDouble("start", 0.0)
                    val endSec = segObj.optDouble("end", startSec + 2.0)
                    val segText = segObj.optString("text", "").trim()
                    if (segText.isNotEmpty()) {
                        parsedSegments.add(
                            SubtitleSegment(
                                id = i + 1,
                                startMs = (startSec * 1000.0).toLong(),
                                endMs = (endSec * 1000.0).toLong(),
                                text = segText
                            )
                        )
                    }
                }
            }

            if (parsedSegments.isEmpty() && fullText.isNotEmpty()) {
                parsedSegments.add(
                    SubtitleSegment(
                        id = 1,
                        startMs = 0L,
                        endMs = 5_000L,
                        text = fullText
                    )
                )
            }

            TranscriptionResult.Success(
                segments = parsedSegments,
                fullText = fullText
            )
        } catch (e: Exception) {
            TranscriptionResult.Error(
                "Fallo de red o transcripción: ${e.message ?: "revisa tu conexión"}"
            )
        } finally {
            connection?.disconnect()
        }
    }
}
