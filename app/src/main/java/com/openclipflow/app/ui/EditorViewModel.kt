package com.openclipflow.app.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openclipflow.app.data.AppSettings
import com.openclipflow.app.data.ProjectStore
import com.openclipflow.app.data.ProjectSummary
import com.openclipflow.app.engine.ExportOutput
import com.openclipflow.app.engine.Exporter
import com.openclipflow.app.engine.PreviewEngine
import com.openclipflow.app.media.Frames
import com.openclipflow.app.media.MediaProbe
import com.openclipflow.app.model.AspectRatio
import com.openclipflow.app.model.Clip
import com.openclipflow.app.model.ExportSettings
import com.openclipflow.app.model.History
import com.openclipflow.app.model.MediaAsset
import com.openclipflow.app.model.MediaKind
import com.openclipflow.app.model.Project
import com.openclipflow.app.model.ProjectJson
import com.openclipflow.app.model.SequenceSettings
import com.openclipflow.app.model.TimeFormat
import com.openclipflow.app.model.TimelineOps
import com.openclipflow.app.model.TitleClip
import com.openclipflow.app.model.TrackKind
import com.openclipflow.app.update.ReleaseInfo
import com.openclipflow.app.update.UpdateManager
import com.openclipflow.app.update.UpdateState
import com.openclipflow.app.update.Updates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen { HOME, EDITOR }

/** Identyfikator rzędu napisów w menu ścieżek i wyborze cięcia. */
const val TITLES_LANE = "titles"

/** Panele boczne edytora. */
enum class PanelKind { NONE, MEDIA, CLIP, TITLE, PROJECT, EXPORT, SETTINGS, TRACKS }

/** Zakładki inspektora klipu. */
enum class ClipTab(val label: String) { SPEED("Prędkość"), AUDIO("Dźwięk"), FILTER("Filtry"), ADJUST("Korekcja"), TRANSFORM("Kadr"), TRANSITION("Przejścia") }

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Float) : ExportState
    data class Done(val output: ExportOutput) : ExportState
    data class Failed(val message: String) : ExportState
}

sealed interface UiEvent {
    data class Share(val file: File, val mime: String) : UiEvent
    data class OpenUrl(val url: String) : UiEvent
    data class SaveAs(val name: String, val mime: String) : UiEvent
    data class ExportProjectFile(val name: String) : UiEvent
}

/** Do czego wybierane są pliki w systemowym oknie wyboru. */
enum class ImportTarget { LIBRARY, TIMELINE, OVERLAY, NEW_PROJECT, REPLACE }

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val context get() = getApplication<Application>()
    val store = ProjectStore(app)
    val settings = AppSettings(app)
    val preview = PreviewEngine(app)
    val events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)

    var screen by mutableStateOf(Screen.HOME)
    var projects by mutableStateOf(emptyList<ProjectSummary>())
        private set
    var thumbsVersion by mutableIntStateOf(0)
        private set

    var project by mutableStateOf(Project())
        private set
    private val history = History()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    var selection by mutableStateOf(emptySet<String>())
    var playheadMs by mutableLongStateOf(0L)
        private set
    /** Skala osi czasu: piksele (dp) na sekundę. */
    var zoom by mutableFloatStateOf(80f)

    var panel by mutableStateOf(PanelKind.NONE)
    var clipTab by mutableStateOf(ClipTab.SPEED)
    var message by mutableStateOf<String?>(null)
    var messageToken by mutableIntStateOf(0)
        private set
    var busy by mutableStateOf<String?>(null)

    var haptics by mutableStateOf(settings.haptics)
    var showSafeArea by mutableStateOf(settings.showSafeArea)
    var showWaveforms by mutableStateOf(settings.showWaveforms)
    var rippleDelete by mutableStateOf(settings.rippleDelete)
    var exportSettings by mutableStateOf(settings.export)
    var exportState by mutableStateOf<ExportState>(ExportState.Idle)
        private set
    private var exportJob: Job? = null

    var update by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set
    var updateBannerHidden by mutableStateOf(false)

    var importTarget = ImportTarget.LIBRARY
    var replaceClipId: String? = null

    /** Edycja ciągła (suwak, przeciąganie): historia zapisuje stan sprzed gestu tylko raz. */
    private var liveBase: Project? = null
    private var playbackJob: Job? = null

    init {
        refreshProjects()
        preview.onEnded = { playheadMs = project.durationMs }
        observeProject()
        if (settings.autoUpdateCheck && System.currentTimeMillis() - settings.lastUpdateCheck > 6 * 3600_000L) checkUpdates(silent = true)
    }

    @OptIn(FlowPreview::class)
    private fun observeProject() {
        // Podgląd: przebudowa kompozycji chwilę po ostatniej zmianie (płynne przeciąganie suwaków).
        viewModelScope.launch {
            snapshotFlow { if (screen == Screen.EDITOR) project else null }
                .filterNotNull()
                .distinctUntilChanged()
                .debounce(180)
                .collect { p -> preview.load(p, p.settings, playheadMs) }
        }
        // Autozapis.
        viewModelScope.launch {
            snapshotFlow { if (screen == Screen.EDITOR) project else null }
                .filterNotNull()
                .distinctUntilChanged()
                .debounce(700)
                .collect { p -> withContext(Dispatchers.IO) { store.save(p) } }
        }
    }

    fun toast(text: String) {
        message = text
        messageToken++
    }

    // ------------------------------------------------------------------ projekty

    fun refreshProjects() {
        viewModelScope.launch {
            projects = withContext(Dispatchers.IO) { store.list() }
            thumbsVersion++
        }
    }

    fun newProject(aspect: AspectRatio = AspectRatio.WIDE, name: String? = null) {
        val n = name ?: "Projekt ${projects.size + 1}"
        openProject(Project(name = n, settings = SequenceSettings(aspect = aspect)))
        viewModelScope.launch(Dispatchers.IO) { store.save(project) }
    }

    fun openProject(id: String) {
        viewModelScope.launch {
            val p = withContext(Dispatchers.IO) { store.load(id) }
            if (p == null) toast("Nie można otworzyć projektu") else openProject(p)
        }
    }

    private fun openProject(p: Project) {
        history.clear()
        liveBase = null
        project = p
        selection = emptySet()
        playheadMs = 0
        panel = PanelKind.NONE
        updateHistoryFlags()
        screen = Screen.EDITOR
        viewModelScope.launch {
            val missing = withContext(Dispatchers.IO) { p.assets.count { !MediaProbe.isAvailable(context, it.uri) } }
            if (missing > 0) toast("Brak dostępu do $missing plików – zaimportuj je ponownie")
        }
    }

    fun closeEditor() {
        pause()
        val p = project
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                store.save(p)
                makeThumb(p)
            }
            refreshProjects()
        }
        preview.release()
        panel = PanelKind.NONE
        screen = Screen.HOME
    }

    private suspend fun makeThumb(p: Project) {
        val first = p.clipsOn(p.mainTrack.id).firstOrNull() ?: p.clips.firstOrNull { p.asset(it.assetId)?.kind != MediaKind.AUDIO } ?: return
        val asset = p.asset(first.assetId) ?: return
        val bmp: Bitmap = Frames.frame(context, asset, first.sourceInMs + 500, 240) ?: return
        store.saveThumb(p.id, bmp)
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(id) }
            refreshProjects()
        }
    }

    fun duplicateProject(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.duplicate(id) }
            refreshProjects()
            toast("Utworzono kopię projektu")
        }
    }

    fun renameProject(id: String, name: String) {
        if (name.isBlank()) return
        if (screen == Screen.EDITOR && project.id == id) {
            edit { it.copy(name = name.trim()) }
        } else {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { store.rename(id, name.trim()) }
                refreshProjects()
            }
        }
    }

    /** Eksport pliku projektu (.ocf.json) – kopia zapasowa albo przeniesienie na inne urządzenie. */
    fun exportProjectFile() {
        events.tryEmit(UiEvent.ExportProjectFile(project.name + ".ocf.json"))
    }

    fun writeProjectFile(target: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(target, "w")?.use { it.write(ProjectJson.encode(project).toByteArray()) }
                }
            }.onSuccess { toast("Zapisano plik projektu") }.onFailure { toast("Nie można zapisać: ${it.message}") }
        }
    }

    fun importProjectFile(uri: Uri) {
        viewModelScope.launch {
            val p = runCatching {
                withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Pusty plik")
                    ProjectJson.decode(text).copy(id = com.openclipflow.app.model.newId(), updatedAt = System.currentTimeMillis()).also { store.save(it) }
                }
            }.getOrElse {
                toast("To nie jest plik projektu OpenClipFlow")
                return@launch
            }
            refreshProjects()
            openProject(p)
        }
    }

    // ------------------------------------------------------------------ edycja i historia

    private fun updateHistoryFlags() {
        canUndo = history.canUndo
        canRedo = history.canRedo
    }

    /** Zmiana projektu zapisywana w historii jako jeden krok. */
    fun edit(transform: (Project) -> Project?): Boolean {
        commitLive()
        val before = project
        val after = transform(before) ?: return false
        if (after == before) return false
        history.record(before)
        project = after.copy(updatedAt = System.currentTimeMillis())
        updateHistoryFlags()
        pruneSelection()
        return true
    }

    /** Zmiana w trakcie gestu (bez zapisu w historii do [commitLive]). */
    fun editLive(transform: (Project) -> Project?) {
        if (liveBase == null) liveBase = project
        val after = transform(project) ?: return
        project = after
    }

    fun commitLive() {
        val base = liveBase ?: return
        liveBase = null
        if (base != project) {
            history.record(base)
            project = project.copy(updatedAt = System.currentTimeMillis())
            updateHistoryFlags()
        }
    }

    fun undo() {
        commitLive()
        val prev = history.undo(project) ?: return toast("Nie ma czego cofnąć")
        project = prev
        updateHistoryFlags()
        pruneSelection()
    }

    fun redo() {
        commitLive()
        val next = history.redo(project) ?: return toast("Nie ma czego ponowić")
        project = next
        updateHistoryFlags()
        pruneSelection()
    }

    private fun pruneSelection() {
        val ids = project.clips.map { it.id }.toSet() + project.titles.map { it.id }
        if (!ids.containsAll(selection)) selection = selection.intersect(ids)
        if (selection.isEmpty() && (panel == PanelKind.CLIP || panel == PanelKind.TITLE)) panel = PanelKind.NONE
    }

    // ------------------------------------------------------------------ zaznaczenie

    val selectedClip: Clip? get() = selection.singleOrNull()?.let { project.clip(it) }
    val selectedTitle: TitleClip? get() = selection.singleOrNull()?.let { project.title(it) }

    fun select(id: String?, additive: Boolean = false) {
        selection = when {
            id == null -> emptySet()
            additive -> if (id in selection) selection - id else selection + id
            else -> setOf(id)
        }
        if (selection.isEmpty() && (panel == PanelKind.CLIP || panel == PanelKind.TITLE)) panel = PanelKind.NONE
        else if (panel == PanelKind.CLIP && selectedClip == null) panel = if (selectedTitle != null) PanelKind.TITLE else PanelKind.NONE
        else if (panel == PanelKind.TITLE && selectedTitle == null) panel = if (selectedClip != null) PanelKind.CLIP else PanelKind.NONE
    }

    fun selectAll() {
        selection = project.clips.map { it.id }.toSet() + project.titles.map { it.id }
    }

    fun openClipPanel(tab: ClipTab) {
        if (selectedClip == null) return toast("Zaznacz klip na osi czasu")
        clipTab = tab
        panel = PanelKind.CLIP
    }

    // ------------------------------------------------------------------ odtwarzanie

    fun seek(ms: Long, fromUser: Boolean = true) {
        val t = ms.coerceIn(0, maxOf(0, project.durationMs))
        playheadMs = t
        if (fromUser) preview.seekTo(t)
    }

    fun togglePlay() = if (preview.isPlaying) pause() else play()

    fun play() {
        if (project.durationMs <= 0) return toast("Dodaj klipy na oś czasu")
        if (playheadMs >= project.durationMs - 40) seek(0)
        preview.play()
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            delay(60)
            while (isActive && preview.isPlaying) {
                playheadMs = preview.currentPositionMs().coerceIn(0, project.durationMs)
                delay(16)
            }
        }
    }

    fun pause() {
        preview.pause()
        playbackJob?.cancel()
        playheadMs = preview.currentPositionMs().coerceIn(0, maxOf(0, project.durationMs)).takeIf { preview.ready } ?: playheadMs
    }

    fun stepFrames(n: Int) {
        pause()
        seek(playheadMs + n * TimeFormat.frameMs(project.settings.fps))
    }

    fun jumpToEdge(next: Boolean) {
        pause()
        val edges = (project.clips.flatMap { listOf(it.startMs, it.endMs) } + project.titles.flatMap { listOf(it.startMs, it.endMs) } + project.markers.map { it.timeMs } + 0L + project.durationMs).distinct().sorted()
        val t = if (next) edges.firstOrNull { it > playheadMs + 5 } else edges.lastOrNull { it < playheadMs - 5 }
        seek(t ?: if (next) project.durationMs else 0)
    }

    // ------------------------------------------------------------------ import

    fun requestImport(target: ImportTarget) {
        importTarget = target
    }

    fun onPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val target = importTarget
        busy = "Importowanie ${uris.size} plików…"
        viewModelScope.launch {
            val assets = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    MediaProbe.probe(context, uri)
                }
            }
            busy = null
            val skipped = uris.size - assets.size
            if (assets.isEmpty()) return@launch toast("Nie rozpoznano żadnego pliku multimedialnego")
            if (target == ImportTarget.NEW_PROJECT) {
                val first = assets.first()
                val aspect = if (first.displayHeight > first.displayWidth) AspectRatio.PORTRAIT else AspectRatio.WIDE
                newProject(aspect)
            }
            if (target == ImportTarget.REPLACE) {
                replaceClipId?.let { replaceMedia(it, assets.first()) }
                replaceClipId = null
                return@launch
            }
            edit { p ->
                var out = p.copy(assets = p.assets + assets.filter { a -> p.assets.none { it.uri == a.uri } })
                if (target != ImportTarget.LIBRARY) {
                    var at = playheadMs
                    val added = mutableSetOf<String>()
                    // Nakładka: pierwsza ścieżka nad główną (tworzona, gdy jej brak), obraz w obrazie.
                    if (target == ImportTarget.OVERLAY && out.videoTracks.size < 2) out = TimelineOps.addTrack(out, TrackKind.VIDEO)
                    val overlayTrack = out.videoTracks.getOrNull(1)?.id
                    assets.forEach { a ->
                        val asset = out.assets.first { it.uri == a.uri }
                        val r = when (target) {
                            ImportTarget.NEW_PROJECT -> TimelineOps.appendAsset(out, asset)
                            ImportTarget.OVERLAY -> TimelineOps.insertAsset(out, asset, at, overlayTrack.takeIf { asset.kind != MediaKind.AUDIO })
                            else -> TimelineOps.insertAsset(out, asset, at)
                        }
                        if (r != null) {
                            var clip = r.second
                            out = r.first
                            if (target == ImportTarget.OVERLAY && clip.trackId != out.mainTrack.id && out.track(clip.trackId)?.kind == TrackKind.VIDEO) {
                                clip = clip.copy(transform = clip.transform.copy(scale = 0.4f, offsetX = 0.27f, offsetY = -0.27f))
                                out = TimelineOps.replace(out, clip)
                            }
                            at = if (target == ImportTarget.OVERLAY) at else clip.endMs
                            added += clip.id
                        }
                    }
                    selection = added
                }
                out
            }
            if (target != ImportTarget.LIBRARY && assets.isNotEmpty()) seek(playheadMs)
            toast(if (skipped > 0) "Zaimportowano ${assets.size}, pominięto $skipped" else "Zaimportowano ${assets.size}")
        }
    }

    fun addAssetToTimeline(asset: MediaAsset, trackId: String? = null) {
        var created: Clip? = null
        val ok = edit { p ->
            TimelineOps.insertAsset(p, asset, playheadMs, trackId)?.let { (np, c) -> created = c; np }
        }
        if (!ok) return toast("Nie można dodać – ścieżka zablokowana")
        created?.let { selection = setOf(it.id) }
    }

    fun removeAsset(asset: MediaAsset) {
        val used = project.clips.count { it.assetId == asset.id }
        edit { p -> p.copy(assets = p.assets - asset, clips = p.clips.filter { it.assetId != asset.id }) }
        toast(if (used > 0) "Usunięto plik i $used klipów" else "Usunięto z biblioteki")
    }

    private fun replaceMedia(clipId: String, asset: MediaAsset) {
        edit { p ->
            val c = p.clip(clipId) ?: return@edit null
            val assets = if (p.assets.any { it.uri == asset.uri }) p.assets else p.assets + asset
            val a = assets.first { it.uri == asset.uri }
            val track = p.track(c.trackId) ?: return@edit null
            if (!TimelineOps.accepts(track, a)) return@edit null
            val maxDur = if (a.isStill) c.durationMs else minOf(c.durationMs, (a.durationMs / c.speed).toLong())
            TimelineOps.replace(p.copy(assets = assets), c.copy(assetId = a.id, sourceInMs = 0, durationMs = maxDur))
        }.let { ok -> toast(if (ok) "Podmieniono materiał" else "Ten plik nie pasuje do ścieżki") }
    }

    // ------------------------------------------------------------------ operacje montażowe

    /** Wybór ścieżki do cięcia (gdy nic nie zaznaczono, a głowica przecina kilka ścieżek). */
    var splitChooser by mutableStateOf(false)
    /** Menu ścieżki otwarte z nagłówka na osi czasu (id ścieżki albo [TITLES_LANE]). */
    var trackMenu by mutableStateOf<String?>(null)

    /** Ścieżki (i rząd napisów), które głowica w tej chwili przecina. */
    fun tracksUnderPlayhead(): List<String> {
        val t = playheadMs
        val ids = project.tracks.filter { tr -> !tr.locked && project.clips.any { it.trackId == tr.id && it.startMs < t && t < it.endMs } }.map { it.id }
        return ids + listOfNotNull(TITLES_LANE.takeIf { project.titles.any { it.startMs < t && t < it.endMs } })
    }

    /** Narzędzie „Podziel”: z zaznaczeniem – zaznaczone; bez – pyta o ścieżkę, gdy jest wybór. */
    fun splitTool() {
        if (selection.isEmpty() && tracksUnderPlayhead().size > 1) splitChooser = true else splitAtPlayhead()
    }

    /** Tnie tylko jedną ścieżkę (albo rząd napisów) w miejscu głowicy. */
    fun splitTrack(trackId: String) {
        val t = playheadMs
        val ok = edit { p ->
            if (trackId == TITLES_LANE) {
                var out = p
                p.titles.forEach { tt -> TimelineOps.splitTitle(out, tt.id, t)?.let { out = it } }
                out.takeIf { it != p }
            } else {
                if (p.track(trackId)?.locked == true) return@edit null
                TimelineOps.splitAt(p, t, p.clips.filter { it.trackId == trackId }.map { it.id }.toSet().ifEmpty { return@edit null })
            }
        }
        val name = if (trackId == TITLES_LANE) "napisy" else project.track(trackId)?.name ?: ""
        toast(if (ok) "Rozcięto ścieżkę „$name” w ${TimeFormat.short(t)}" else "Głowica nie przecina klipu na tej ścieżce")
    }

    fun selectTrackClips(trackId: String) {
        selection = if (trackId == TITLES_LANE) project.titles.map { it.id }.toSet() else project.clips.filter { it.trackId == trackId }.map { it.id }.toSet()
        if (selection.isEmpty()) toast("Ścieżka jest pusta")
    }

    // ------------------------------------------------------------------ przenikania

    /** Ustawia przenikanie z poprzedniego ujęcia na klipie [clipId] (0 = twarde cięcie). */
    fun setDissolve(clipId: String, ms: Long, live: Boolean = false) {
        val change: (Project) -> Project? = { p -> p.clip(clipId)?.let { TimelineOps.replace(p, it.copy(dissolveInMs = ms)) } }
        if (live) editLive(change) else edit(change)
        if (!live && ms > 0) {
            val c = project.clip(clipId) ?: return
            val possible = com.openclipflow.app.model.Dissolves.maxPossible(project, c)
            if (possible < com.openclipflow.app.model.Dissolves.MIN_MS) toast("Brak zapasu materiału – skróć któryś klip, żeby przenikanie miało z czego powstać")
            else if (possible < ms) toast("Przenikanie skrócone do ${TimeFormat.duration(possible)} (zapas materiału)")
        }
    }

    fun setDissolveAll(ms: Long) {
        val ok = edit { p -> com.openclipflow.app.model.Dissolves.applyToAll(p, ms) }
        toast(if (!ok) "Brak stykających się klipów na głównej ścieżce" else if (ms > 0) "Przenikanie na wszystkich cięciach" else "Usunięto przenikania")
    }

    /** Otwiera ustawienia przejścia dla cięcia przed klipem [nextClipId]. */
    fun openCut(nextClipId: String) {
        pause()
        select(nextClipId)
        clipTab = ClipTab.TRANSITION
        panel = PanelKind.CLIP
    }

    fun splitAtPlayhead() {
        val t = playheadMs
        val sel = selection
        val ok = edit { p ->
            // Bez zaznaczenia tniemy wszystko pod głowicą, z zaznaczeniem – tylko zaznaczone.
            val clipIds = if (sel.isEmpty()) null else sel.filter { p.clip(it) != null }.toSet()
            val titleIds = if (sel.isEmpty()) p.titles.map { it.id } else sel.filter { p.title(it) != null }
            var out = p
            if (clipIds == null || clipIds.isNotEmpty()) TimelineOps.splitAt(out, t, clipIds)?.let { out = it }
            titleIds.forEach { id -> TimelineOps.splitTitle(out, id, t)?.let { out = it } }
            out.takeIf { it != p }
        }
        toast(if (ok) "Rozcięto w ${TimeFormat.short(playheadMs)}" else "Głowica nie przecina zaznaczonego klipu")
    }

    fun deleteSelection(ripple: Boolean = rippleDelete) {
        if (selection.isEmpty()) return toast("Nic nie zaznaczono")
        val n = selection.size
        edit { p -> TimelineOps.delete(p, selection, ripple) }
        selection = emptySet()
        panel = PanelKind.NONE
        toast(if (n == 1) "Usunięto" else "Usunięto $n elementów")
    }

    fun duplicateSelection() {
        if (selection.isEmpty()) return toast("Nic nie zaznaczono")
        var created = emptySet<String>()
        edit { p -> TimelineOps.duplicate(p, selection)?.let { (np, ids) -> created = ids; np } }
        if (created.isNotEmpty()) selection = created
    }

    fun detachAudio() {
        val c = selectedClip ?: return toast("Zaznacz klip wideo")
        val ok = edit { p -> TimelineOps.detachAudio(p, c.id)?.first }
        toast(if (ok) "Dźwięk przeniesiony na ścieżkę audio" else "Klip nie ma dźwięku do odłączenia")
    }

    fun closeGaps(trackId: String = project.mainTrack.id) {
        val ok = edit { p -> TimelineOps.closeGaps(p, trackId) }
        toast(if (ok) "Usunięto przerwy" else "Brak przerw na ścieżce")
    }

    fun toggleMarker() {
        edit { p -> TimelineOps.toggleMarker(p, playheadMs) }
    }

    fun updateClip(c: Clip, live: Boolean = false) {
        if (live) editLive { p -> TimelineOps.replace(p, c) } else edit { p -> TimelineOps.replace(p, c) }
    }

    fun setSpeed(clipId: String, speed: Float, live: Boolean) {
        if (live) editLive { p -> TimelineOps.setSpeed(p, clipId, speed) } else edit { p -> TimelineOps.setSpeed(p, clipId, speed) }
    }

    fun setDuration(clipId: String, ms: Long) {
        edit { p -> TimelineOps.setDuration(p, clipId, ms) }
    }

    fun applyToAll(c: Clip) {
        edit { p ->
            p.copy(clips = p.clips.map {
                if (it.id != c.id && p.track(it.trackId)?.kind == TrackKind.VIDEO) it.copy(filter = c.filter, filterStrength = c.filterStrength, color = c.color) else it
            })
        }
        toast("Wygląd zastosowany do wszystkich klipów")
    }

    // ------------------------------------------------------------------ ścieżki

    fun updateTrack(id: String, transform: (com.openclipflow.app.model.Track) -> com.openclipflow.app.model.Track) {
        edit { p -> p.copy(tracks = p.tracks.map { if (it.id == id) transform(it) else it }) }
    }

    fun addTrack(kind: TrackKind) {
        edit { p -> TimelineOps.addTrack(p, kind) }
    }

    fun removeTrack(id: String) {
        val ok = edit { p -> TimelineOps.removeTrack(p, id) }
        if (!ok) toast("Tej ścieżki nie można usunąć")
    }

    // ------------------------------------------------------------------ napisy

    fun addTitle(template: TitleClip? = null) {
        var created: TitleClip? = null
        edit { p -> TimelineOps.addTitle(p, template?.text ?: "Twój tekst", playheadMs, template).let { (np, t) -> created = t; np } }
        created?.let {
            selection = setOf(it.id)
            panel = PanelKind.TITLE
        }
    }

    fun updateTitle(t: TitleClip, live: Boolean = false) {
        if (live) editLive { p -> TimelineOps.updateTitle(p, t) } else edit { p -> TimelineOps.updateTitle(p, t) }
    }

    // ------------------------------------------------------------------ ustawienia projektu

    fun updateSequence(s: SequenceSettings) {
        edit { p -> p.copy(settings = s) }
    }

    fun setHapticsPref(v: Boolean) { haptics = v; settings.haptics = v }
    fun setSafeAreaPref(v: Boolean) { showSafeArea = v; settings.showSafeArea = v }
    fun setWaveformsPref(v: Boolean) { showWaveforms = v; settings.showWaveforms = v }
    fun setRipplePref(v: Boolean) { rippleDelete = v; settings.rippleDelete = v }

    // ------------------------------------------------------------------ eksport

    fun updateExportSettings(s: ExportSettings) {
        exportSettings = s
        settings.export = s
    }

    fun startExport() {
        if (project.durationMs <= 0) return toast("Oś czasu jest pusta")
        if (exportState is ExportState.Running) return
        pause()
        val p = project
        val s = exportSettings
        exportState = ExportState.Running(0f)
        exportJob = viewModelScope.launch {
            exportState = try {
                ExportState.Done(Exporter.export(context, p, s) { exportState = ExportState.Running(it) })
            } catch (e: kotlinx.coroutines.CancellationException) {
                ExportState.Idle
            } catch (e: Exception) {
                ExportState.Failed(e.message ?: "Nieznany błąd")
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        exportState = ExportState.Idle
        toast("Eksport przerwany")
    }

    fun resetExport() {
        if (exportState !is ExportState.Running) exportState = ExportState.Idle
    }

    fun shareExport() {
        val out = (exportState as? ExportState.Done)?.output ?: return
        events.tryEmit(UiEvent.Share(out.file, if (out.file.extension == "m4a") "audio/mp4" else "video/mp4"))
    }

    fun saveExportAs() {
        val out = (exportState as? ExportState.Done)?.output ?: return
        events.tryEmit(UiEvent.SaveAs(out.file.name, if (out.file.extension == "m4a") "audio/mp4" else "video/mp4"))
    }

    fun writeExportTo(target: Uri) {
        val out = (exportState as? ExportState.Done)?.output ?: return
        viewModelScope.launch {
            runCatching { Exporter.copyTo(context, out.file, target) }
                .onSuccess { toast("Zapisano") }
                .onFailure { toast("Nie można zapisać: ${it.message}") }
        }
    }

    /** Zapisuje bieżącą klatkę źródła spod głowicy (główna ścieżka) jako zdjęcie w galerii. */
    fun saveFrame() {
        val c = TimelineOps.clipAt(project, project.mainTrack.id, playheadMs) ?: return toast("Pod głowicą nie ma obrazu")
        val asset = project.asset(c.assetId) ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val t = c.sourceInMs + ((playheadMs - c.startMs) * c.speed).toLong()
                val bmp = Frames.fullFrame(context, asset, t) ?: return@withContext false
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "${project.name}-${TimeFormat.short(playheadMs).replace(':', '-')}.jpg")
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/OpenClipFlow")
                }
                val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return@withContext false
                context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) } != null
            }
            toast(if (ok) "Klatka zapisana w Obrazy/OpenClipFlow" else "Nie można zapisać klatki")
        }
    }

    // ------------------------------------------------------------------ aktualizacje

    val updatePending: Boolean
        get() = update is UpdateState.Available || update is UpdateState.Downloading || update is UpdateState.Ready || update is UpdateState.Installing

    fun checkUpdates(silent: Boolean = false) {
        if (update is UpdateState.Checking || update is UpdateState.Downloading) return
        update = UpdateState.Checking
        viewModelScope.launch {
            update = try {
                val info = UpdateManager.latest()
                settings.lastUpdateCheck = System.currentTimeMillis()
                if (Updates.isNewer(info.version, UpdateManager.currentVersion)) UpdateState.Available(info) else UpdateState.UpToDate
            } catch (e: Exception) {
                if (silent) UpdateState.Idle else UpdateState.Failed(e.message ?: "Brak połączenia")
            }
            if (!silent && update is UpdateState.UpToDate) toast("Masz najnowszą wersję")
        }
    }

    fun updateNow() {
        when (val u = update) {
            is UpdateState.Available -> download(u.info)
            is UpdateState.Ready -> install(u.info, u.file)
            else -> Unit
        }
    }

    private fun download(info: ReleaseInfo) {
        update = UpdateState.Downloading(info, 0f)
        viewModelScope.launch {
            update = try {
                val f = UpdateManager.download(context, info) { update = UpdateState.Downloading(info, it) }
                UpdateState.Ready(info, f)
            } catch (e: Exception) {
                UpdateState.Failed(e.message ?: "Pobieranie nieudane")
            }
            (update as? UpdateState.Ready)?.let { install(it.info, it.file) }
        }
    }

    private fun install(info: ReleaseInfo, file: File) {
        if (!UpdateManager.canInstall(context)) {
            toast("Zezwól na instalowanie aplikacji z OpenClipFlow i dotknij ponownie")
            UpdateManager.openInstallPermissionSettings(context)
            return
        }
        update = UpdateState.Installing(info)
        viewModelScope.launch {
            runCatching { UpdateManager.install(context, file) }.onFailure {
                update = UpdateState.Failed(it.message ?: "Instalacja nieudana")
            }
            delay(8000)
            if (update is UpdateState.Installing) update = UpdateState.Ready(info, file)
        }
    }

    fun openReleasePage() {
        val url = (update as? UpdateState.Available)?.info?.pageUrl ?: "https://github.com/${com.openclipflow.app.BuildConfig.UPDATE_REPO}/releases"
        events.tryEmit(UiEvent.OpenUrl(url))
    }

    override fun onCleared() {
        preview.release()
        super.onCleared()
    }
}
