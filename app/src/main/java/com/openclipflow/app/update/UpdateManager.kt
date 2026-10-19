package com.openclipflow.app.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.openclipflow.app.BuildConfig
import com.openclipflow.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Aktualizacje z GitHub Releases: sprawdzenie najnowszego wydania, pobranie APK i instalacja
 * przez PackageInstaller. Wydania są podpisane stałym kluczem, więc nowa wersja instaluje się
 * na poprzedniej bez odinstalowania. Od Androida 12 – bez potwierdzenia, gdy poprzednią wersję
 * zainstalowała sama aplikacja.
 */
object UpdateManager {
    const val ACTION_STATUS = "com.openclipflow.app.UPDATE_STATUS"
    private const val RESTART_KEY = "update.restart"
    private const val CHANNEL = "updates"
    private const val NOTIFICATION_ID = 4712
    private const val API = "https://api.github.com/repos/" + BuildConfig.UPDATE_REPO + "/releases/latest"

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    suspend fun latest(): ReleaseInfo = withContext(Dispatchers.IO) { Updates.parseRelease(httpGet(API)) }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "OpenClipFlow/" + BuildConfig.VERSION_NAME)
        }
        try {
            if (conn.responseCode != 200) error("Serwer odpowiedział kodem ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun download(context: Context, info: ReleaseInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "OpenClipFlow-${info.version}.apk")
        val part = File(dir, target.name + ".part")
        val conn = (URL(info.assetUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "OpenClipFlow/" + BuildConfig.VERSION_NAME)
        }
        try {
            if (conn.responseCode !in 200..299) error("Pobieranie nie powiodło się (${conn.responseCode})")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastReport = 0L
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0 && read - lastReport > 128 * 1024) {
                            lastReport = read
                            onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (!part.renameTo(target)) error("Nie można zapisać pliku aktualizacji")
            verifySignature(context, target)
            onProgress(1f)
            target
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Sprawdza, czy pobrany APK ma ten sam podpis co zainstalowana aplikacja – inaczej system
     * i tak odrzuci instalację, a użytkownik dostałby niezrozumiały błąd.
     */
    private fun verifySignature(context: Context, apk: File) {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val downloaded = pm.getPackageArchiveInfo(apk.path, flags)?.signingInfo ?: error("Pobrany plik nie jest poprawnym APK")
        val installed = pm.getPackageInfo(context.packageName, flags).signingInfo ?: return
        val a = downloaded.apkContentsSigners.map { it.toCharsString() }.toSet()
        val b = (if (installed.hasMultipleSigners()) installed.apkContentsSigners else installed.signingCertificateHistory).map { it.toCharsString() }.toSet()
        if (a.intersect(b).isEmpty()) {
            apk.delete()
            error("Podpis nowej wersji różni się od zainstalowanej – odinstaluj aplikację i zainstaluj APK z GitHuba ręcznie")
        }
    }

    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    suspend fun install(context: Context, file: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        context.getSharedPreferences("openclipflow_update", Context.MODE_PRIVATE).edit()
            .putLong(RESTART_KEY, System.currentTimeMillis()).apply()
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, UpdateReceiver::class.java).setAction(ACTION_STATUS)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, id, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    /** Po aktualizacji otwiera aplikację ponownie (albo zostawia powiadomienie „dotknij, aby otworzyć”). */
    fun relaunchAfterUpdate(context: Context) {
        val prefs = context.getSharedPreferences("openclipflow_update", Context.MODE_PRIVATE)
        val requested = prefs.getLong(RESTART_KEY, 0L)
        if (requested == 0L || System.currentTimeMillis() - requested > 30 * 60_000L) return
        prefs.edit().remove(RESTART_KEY).apply()
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) ?: return
        runCatching { context.startActivity(launch) }
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Aktualizacje", NotificationManager.IMPORTANCE_HIGH))
        val pending = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("OpenClipFlow zaktualizowany do ${BuildConfig.VERSION_NAME}")
            .setContentText("Dotknij, aby otworzyć aplikację")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, n) }
    }

    fun clearUpdatedNotification(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID) }
    }
}

/** Status instalacji; gdy system wymaga potwierdzenia – pokazuje jego okno. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { context.startActivity(it) } }
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateManager.relaunchAfterUpdate(context)
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "kod $status"
                if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
                    Toast.makeText(context, "Aktualizacja nieudana: $msg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

/** Nowa wersja zainstalowana – system wysyła to do zaktualizowanej aplikacji. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) UpdateManager.relaunchAfterUpdate(context)
    }
}
