package com.openclipflow.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.FileProvider
import com.openclipflow.app.ui.AppRoot
import com.openclipflow.app.ui.EditorViewModel
import com.openclipflow.app.ui.ImportTarget
import com.openclipflow.app.ui.PanelKind
import com.openclipflow.app.ui.Screen
import com.openclipflow.app.ui.UiEvent
import com.openclipflow.app.ui.theme.OpenClipFlowTheme
import com.openclipflow.app.update.UpdateManager

/** Akcje platformy wywoływane z interfejsu (okna wyboru plików systemu). */
interface PlatformActions {
    fun pickMedia(target: ImportTarget, kinds: Array<String> = arrayOf("video/*", "image/*", "audio/*"))
    fun openProjectFile()
    /** Prosi o dostęp do mikrofonu (lektor); [onGranted] – gdy jest. */
    fun withMicrophone(onGranted: () -> Unit)
}

class MainActivity : ComponentActivity(), PlatformActions {
    private val vm: EditorViewModel by viewModels()

    private val pickMedia = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.onPicked(uris) }
    private val pickProject = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importProjectFile) }
    private val saveExport = registerForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri -> uri?.let(vm::writeExportTo) }
    private val saveAudio = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/mp4")) { uri -> uri?.let(vm::writeExportTo) }
    private var micGranted: (() -> Unit)? = null
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) micGranted?.invoke() else vm.toast("Bez dostępu do mikrofonu nie da się nagrać lektora")
        micGranted = null
    }
    private val saveProject = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::writeProjectFile) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        }
        UpdateManager.clearUpdatedNotification(this)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            OpenClipFlowTheme {
                LaunchedEffect(Unit) { vm.events.collect(::onEvent) }
                BackHandler(enabled = vm.screen == Screen.EDITOR) {
                    when {
                        vm.panel != PanelKind.NONE -> vm.panel = PanelKind.NONE
                        vm.selection.isNotEmpty() -> vm.select(null)
                        else -> vm.closeEditor()
                    }
                }
                AppRoot(vm, this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        if (vm.screen == Screen.EDITOR) vm.pause()
    }

    /** „Udostępnij do OpenClipFlow” / „Otwórz w…” z galerii – nowy projekt z tymi plikami. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.parcelableExtra(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.parcelableArrayListExtra(Intent.EXTRA_STREAM).orEmpty()
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> listOfNotNull(intent.data)
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        vm.requestImport(if (vm.screen == Screen.EDITOR) ImportTarget.TIMELINE else ImportTarget.NEW_PROJECT)
        vm.onPicked(uris)
    }

    private fun Intent.parcelableExtra(key: String): Uri? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, Uri::class.java) else @Suppress("DEPRECATION") getParcelableExtra(key)

    private fun Intent.parcelableArrayListExtra(key: String): List<Uri>? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(key, Uri::class.java) else @Suppress("DEPRECATION") getParcelableArrayListExtra(key)

    override fun pickMedia(target: ImportTarget, kinds: Array<String>) {
        vm.requestImport(target)
        runCatching { pickMedia.launch(kinds) }.onFailure { vm.toast("Brak aplikacji do wybierania plików") }
    }

    override fun withMicrophone(onGranted: () -> Unit) {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            onGranted()
        } else {
            micGranted = onGranted
            micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun openProjectFile() {
        runCatching { pickProject.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }
    }

    private fun onEvent(e: UiEvent) {
        when (e) {
            is UiEvent.Share -> {
                val uri = FileProvider.getUriForFile(this, "$packageName.files", e.file)
                val send = Intent(Intent.ACTION_SEND).setType(e.mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(send, "Udostępnij film"))
            }
            is UiEvent.OpenUrl -> try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(e.url)))
            } catch (_: ActivityNotFoundException) {
                vm.toast("Brak przeglądarki")
            }
            is UiEvent.SaveAs -> if (e.mime.startsWith("audio")) saveAudio.launch(e.name) else saveExport.launch(e.name)
            is UiEvent.ExportProjectFile -> saveProject.launch(e.name)
        }
    }
}
