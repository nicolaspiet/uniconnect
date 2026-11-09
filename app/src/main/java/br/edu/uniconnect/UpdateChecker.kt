package br.edu.uniconnect

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val releaseUrl: String,
    val minSdk: Int
)

data class DownloadProgress(val downloadedBytes: Long, val totalBytes: Long) {
    val percent: Int? get() = if (totalBytes > 0) ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100) else null
}

sealed interface UpdateCheckResult {
    data class Available(val update: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Unavailable(val message: String) : UpdateCheckResult
}

/** Public releases only. Never place a GitHub token or credentials in an APK. */
object UpdateChecker {
    const val REPOSITORY = "nicolaspiet/uniconnect"
    const val RELEASES_URL = "https://github.com/$REPOSITORY/releases"
    private const val API_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"
    private const val MAX_APK_BYTES = 100L * 1024 * 1024
    private val downloadLock = Mutex()

    suspend fun check(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val release = JSONObject(readText(API_URL, 512 * 1024))
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@withContext UpdateCheckResult.UpToDate
            val assets = release.getJSONArray("assets")
            val metadataAsset = (0 until assets.length()).map(assets::getJSONObject)
                .firstOrNull { it.optString("name") == "uniconnect-update.json" }
                ?: return@withContext UpdateCheckResult.Unavailable("Esta versão ainda não oferece atualização pelo app.")
            val metadataUrl = metadataAsset.getString("browser_download_url")
            require(UpdatePolicy.releaseAssetUrl(metadataUrl))
            val metadata = JSONObject(readText(metadataUrl, 32 * 1024))
            require(metadata.getInt("schemaVersion") == 1)
            val versionCode = metadata.getLong("versionCode")
            if (versionCode <= installedVersion(context)) return@withContext UpdateCheckResult.UpToDate
            val versionName = metadata.getString("versionName").take(80)
            val minSdk = metadata.getInt("minSdk")
            if (minSdk > Build.VERSION.SDK_INT) return@withContext UpdateCheckResult.Unavailable("A nova versão exige Android mais recente.")
            val assetName = metadata.getString("apkAsset")
            val apk = (0 until assets.length()).map(assets::getJSONObject)
                .firstOrNull { it.optString("name") == assetName && assetName.endsWith(".apk") }
                ?: return@withContext UpdateCheckResult.Unavailable("O APK da nova versão ainda não está disponível.")
            val apkUrl = apk.getString("browser_download_url")
            val releaseUrl = release.getString("html_url")
            val sha256 = metadata.getString("sha256").lowercase()
            val size = apk.getLong("size")
            require(versionCode in 1..Int.MAX_VALUE.toLong())
            require(versionName.isNotBlank() && minSdk > 0)
            require(UpdatePolicy.releaseAssetUrl(apkUrl) && UpdatePolicy.releasePageUrl(releaseUrl))
            require(UpdatePolicy.validSha256(sha256) && size in 1..MAX_APK_BYTES)
            UpdateCheckResult.Available(UpdateInfo(versionCode, versionName, release.optString("body").take(4_000), apkUrl, sha256, size, releaseUrl, minSdk))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: ReleaseHttpException) {
            UpdateCheckResult.Unavailable(when (failure.status) {
                404 -> "Nenhuma versão pública disponível para atualização."
                403, 429 -> "A consulta de versões está temporariamente limitada. Tente mais tarde."
                else -> "Não foi possível consultar atualizações agora. Tente novamente."
            })
        } catch (_: Exception) {
            UpdateCheckResult.Unavailable("Não foi possível consultar atualizações. Verifique a internet e tente novamente.")
        }
    }

    /** Partial downloads are deleted. A retry always starts with a fresh, verified APK. */
    suspend fun download(context: Context, update: UpdateInfo, onProgress: (DownloadProgress) -> Unit): File = downloadLock.withLock {
        withContext(Dispatchers.IO) {
            require(UpdatePolicy.releaseAssetUrl(update.apkUrl) && UpdatePolicy.validSha256(update.sha256))
            require(update.sizeBytes in 1..MAX_APK_BYTES)
            val directory = File(context.cacheDir, "updates").apply { check(mkdirs() || isDirectory) }
            directory.listFiles()?.filter { it.extension == "part" }?.forEach { it.delete() }
            val partial = File(directory, "uniconnect-${update.versionCode}.part")
            val complete = File(directory, "uniconnect-${update.versionCode}.apk")
            var connection: HttpURLConnection? = null
            try {
                connection = connect(update.apkUrl)
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                var lastProgress = 0L
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count == -1) break
                            downloaded += count
                            if (downloaded > update.sizeBytes || downloaded > MAX_APK_BYTES) throw IOException("O arquivo recebido não corresponde à atualização.")
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - lastProgress >= 200) {
                                withContext(Dispatchers.Main.immediate) { onProgress(DownloadProgress(downloaded, update.sizeBytes)) }
                                lastProgress = now
                            }
                        }
                    }
                }
                if (downloaded != update.sizeBytes || UpdatePolicy.hex(digest.digest()) != update.sha256.lowercase()) {
                    throw IOException("O download veio incompleto ou corrompido. Tente novamente.")
                }
                check(partial.renameTo(complete)) { "Não foi possível finalizar o download." }
                verifyPackage(context, complete, update)
                withContext(Dispatchers.Main.immediate) { onProgress(DownloadProgress(downloaded, update.sizeBytes)) }
                complete
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                complete.delete()
                throw cancelled
            } catch (failure: Exception) {
                complete.delete()
                throw IOException(when (failure) {
                    is SecurityException -> failure.message ?: "A assinatura da atualização não corresponde a este aplicativo."
                    else -> "Download interrompido. Verifique a conexão e tente novamente."
                }, failure)
            } finally {
                connection?.disconnect()
                partial.delete()
            }
        }
    }

    fun canRequestInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(context: Context): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun installIntent(context: Context, file: File): Intent {
        val directory = File(context.cacheDir, "updates").canonicalFile
        require(file.canonicalFile.parentFile == directory && file.extension == "apk" && file.isFile)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .apply { clipData = ClipData.newRawUri("UniConnect APK", uri) }
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Suppress("DEPRECATION")
    private fun installedVersion(context: Context): Long = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    @Suppress("DEPRECATION")
    private fun verifyPackage(context: Context, file: File, update: UpdateInfo) {
        val manager = context.packageManager
        val archive = manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw SecurityException("O arquivo recebido não é um APK válido.")
        val installed = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val expected = installed.signingInfo?.apkContentsSigners?.map { UpdatePolicy.hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }?.toSet()
        val actual = archive.signingInfo?.apkContentsSigners?.map { UpdatePolicy.hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }?.toSet()
        if (archive.packageName != context.packageName || archive.longVersionCode != update.versionCode || archive.versionName != update.versionName) {
            throw SecurityException("O APK não corresponde à versão anunciada. Baixe a versão oficial na página de releases.")
        }
        if (archive.longVersionCode <= installed.longVersionCode) {
            throw SecurityException("Esta versão já está instalada ou é mais antiga que a atual.")
        }
        if (archive.applicationInfo?.minSdkVersion?.let { it > Build.VERSION.SDK_INT } == true) {
            throw SecurityException("A atualização exige uma versão mais recente do Android.")
        }
        if (expected.isNullOrEmpty() || actual != expected) {
            throw SecurityException("Esta atualização não tem a mesma assinatura do aplicativo instalado. Use a distribuição original.")
        }
    }

    private suspend fun readText(url: String, limit: Int): String {
        val connection = connect(url)
        try {
            return connection.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = stream.read(buffer)
                    if (count == -1) break
                    if (output.size() + count > limit) throw IOException("Resposta de atualização muito grande.")
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally { connection.disconnect() }
    }

    private fun connect(address: String): HttpURLConnection {
        var url = address
        repeat(6) {
            require(UpdatePolicy.downloadHostUrl(url))
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "UniConnect-Android")
            connection.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream;q=0.9, */*;q=0.8")
            try {
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location") ?: throw IOException("Redirecionamento inválido.")
                    url = URI(url).resolve(location).toString()
                    connection.disconnect()
                } else {
                    if (status != 200) throw ReleaseHttpException(status)
                    return connection
                }
            } catch (failure: Exception) {
                connection.disconnect()
                throw failure
            }
        }
        throw IOException("Redirecionamentos demais ao baixar atualização.")
    }

    private class ReleaseHttpException(val status: Int) : IOException()
}

/** Pure policy functions are kept separate so hostile release URLs can be unit tested. */
internal object UpdatePolicy {
    private val downloadHosts = setOf("api.github.com", "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com", "github-releases.githubusercontent.com")
    private fun uri(value: String): URI? = runCatching { URI(value) }.getOrNull()?.takeIf {
        it.scheme == "https" && it.rawUserInfo == null && it.fragment == null && (it.port == -1 || it.port == 443)
    }
    fun releaseAssetUrl(value: String): Boolean = uri(value)?.let {
        it.host == "github.com" && it.path.startsWith("/${UpdateChecker.REPOSITORY}/releases/download/") && !it.path.contains("/../")
    } == true
    fun releasePageUrl(value: String): Boolean = uri(value)?.let {
        it.host == "github.com" && it.path.startsWith("/${UpdateChecker.REPOSITORY}/releases/tag/")
    } == true
    fun downloadHostUrl(value: String): Boolean = uri(value)?.host in downloadHosts
    fun validSha256(value: String): Boolean = value.matches(Regex("[a-fA-F0-9]{64}"))
    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
