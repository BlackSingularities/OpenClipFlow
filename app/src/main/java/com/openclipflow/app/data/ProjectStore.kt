package com.openclipflow.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.openclipflow.app.model.ExportSettings
import com.openclipflow.app.model.Project
import com.openclipflow.app.model.ProjectJson
import com.openclipflow.app.model.newId
import kotlinx.serialization.Serializable
import java.io.File

/** Krótki opis projektu na ekran główny (bez wczytywania całej osi czasu). */
@Serializable
data class ProjectSummary(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val durationMs: Long,
    val clipCount: Int,
    val aspect: String,
)

/**
 * Projekty jako pliki JSON w pamięci aplikacji (projects/<id>.json) z miniaturą (<id>.jpg).
 * Zapis atomowy: najpierw plik tymczasowy, potem zamiana – przerwany zapis nie psuje projektu.
 */
class ProjectStore(context: Context) {
    private val dir = File(context.filesDir, "projects").apply { mkdirs() }

    private fun file(id: String) = File(dir, "$id.json")
    fun thumbFile(id: String) = File(dir, "$id.jpg")

    fun list(): List<ProjectSummary> = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        .mapNotNull { f -> runCatching { summary(ProjectJson.decode(f.readText())) }.getOrNull() }
        .sortedByDescending { it.updatedAt }

    fun summary(p: Project) = ProjectSummary(p.id, p.name, p.updatedAt, p.durationMs, p.clips.size + p.titles.size, p.settings.aspect.label)

    fun load(id: String): Project? = runCatching { ProjectJson.decode(file(id).readText()) }.getOrNull()

    @Synchronized
    fun save(p: Project) {
        val tmp = File(dir, "${p.id}.json.tmp")
        tmp.writeText(ProjectJson.encode(p))
        if (!tmp.renameTo(file(p.id))) {
            file(p.id).delete()
            tmp.renameTo(file(p.id))
        }
    }

    fun saveThumb(id: String, bmp: Bitmap) = runCatching {
        thumbFile(id).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
    }

    fun thumb(id: String): Bitmap? = thumbFile(id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun delete(id: String) {
        file(id).delete()
        thumbFile(id).delete()
    }

    fun duplicate(id: String): Project? {
        val p = load(id) ?: return null
        val now = System.currentTimeMillis()
        val copy = p.copy(id = newId(), name = p.name + " (kopia)", createdAt = now, updatedAt = now)
        save(copy)
        thumbFile(id).takeIf { it.exists() }?.copyTo(thumbFile(copy.id), overwrite = true)
        return copy
    }

    fun rename(id: String, name: String) {
        val p = load(id) ?: return
        save(p.copy(name = name, updatedAt = System.currentTimeMillis()))
    }
}

/** Ustawienia aplikacji (SharedPreferences). */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("openclipflow", Context.MODE_PRIVATE)

    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(v) = prefs.edit().putBoolean("haptics", v).apply()

    var autoUpdateCheck: Boolean
        get() = prefs.getBoolean("autoUpdate", true)
        set(v) = prefs.edit().putBoolean("autoUpdate", v).apply()

    var showSafeArea: Boolean
        get() = prefs.getBoolean("safeArea", false)
        set(v) = prefs.edit().putBoolean("safeArea", v).apply()

    var showWaveforms: Boolean
        get() = prefs.getBoolean("waveforms", true)
        set(v) = prefs.edit().putBoolean("waveforms", v).apply()

    var rippleDelete: Boolean
        get() = prefs.getBoolean("ripple", true)
        set(v) = prefs.edit().putBoolean("ripple", v).apply()

    var export: ExportSettings
        get() = runCatching { ProjectJson.json.decodeFromString(ExportSettings.serializer(), prefs.getString("export", null)!!) }.getOrDefault(ExportSettings())
        set(v) = prefs.edit().putString("export", ProjectJson.json.encodeToString(ExportSettings.serializer(), v)).apply()

    var lastUpdateCheck: Long
        get() = prefs.getLong("lastUpdateCheck", 0L)
        set(v) = prefs.edit().putLong("lastUpdateCheck", v).apply()
}
