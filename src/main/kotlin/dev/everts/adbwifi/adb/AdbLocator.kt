package dev.everts.adbwifi.adb

import com.android.tools.idea.adb.AdbFileProvider
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Files
import java.nio.file.Path

/**
 * Busca el ejecutable de adb. Primero pregunta al plugin Android (el mismo adb que usa Studio) y,
 * si no lo encuentra, prueba `ANDROID_HOME`, `ANDROID_SDK_ROOT` y la ruta por defecto del SDK.
 *
 * Se usa `AdbFileProvider` y no `AndroidSdkUtils.getAdb(project)`, que está deprecado en Studio 2026.2.
 */
object AdbLocator {

    /** Devuelve la ruta de adb o lanza [AdbNotFoundException] con las rutas probadas. */
    fun locate(project: Project?): Path {
        fromAndroidPlugin(project)?.let { return it }
        val candidates = fallbackCandidates(System.getenv(), Path.of(System.getProperty("user.home")))
        return candidates.firstOrNull(::isExecutable) ?: throw AdbNotFoundException(candidates)
    }

    /**
     * Rutas de respaldo en orden de preferencia. Puras para poder probarlas: el entorno, el home y
     * el sistema operativo se reciben como parámetros.
     */
    fun fallbackCandidates(
        environment: Map<String, String>,
        userHome: Path,
        os: Os = Os.current(),
    ): List<Path> {
        val executable = if (os == Os.WINDOWS) "adb.exe" else "adb"
        val sdkRoots = listOfNotNull(
            environment["ANDROID_HOME"]?.takeIf(String::isNotBlank)?.let(Path::of),
            environment["ANDROID_SDK_ROOT"]?.takeIf(String::isNotBlank)?.let(Path::of),
            defaultSdk(os, environment, userHome),
        )
        return sdkRoots.map { it.resolve("platform-tools").resolve(executable) }.distinct()
    }

    private fun defaultSdk(os: Os, environment: Map<String, String>, userHome: Path): Path? = when (os) {
        Os.MAC -> userHome.resolve("Library/Android/sdk")
        Os.LINUX -> userHome.resolve("Android/Sdk")
        Os.WINDOWS -> environment["LOCALAPPDATA"]?.let { Path.of(it, "Android", "Sdk") }
    }

    /** La API del plugin Android puede cambiar entre versiones de Studio; cualquier fallo cae al respaldo. */
    @Suppress("TooGenericExceptionCaught")
    private fun fromAndroidPlugin(project: Project?): Path? =
        try {
            val provider = project?.let(AdbFileProvider::fromProject) ?: AdbFileProvider.fromApplication()
            provider.get()?.toPath()?.takeIf(::isExecutable)
        } catch (e: Exception) {
            thisLogger().warn("AdbFileProvider failed, using fallback locations", e)
            null
        } catch (e: LinkageError) {
            thisLogger().warn("AdbFileProvider is not available in this Android Studio", e)
            null
        }

    private fun isExecutable(path: Path): Boolean = Files.isRegularFile(path) && Files.isExecutable(path)

    enum class Os {
        MAC, LINUX, WINDOWS;

        companion object {
            fun current(): Os = when {
                SystemInfo.isWindows -> WINDOWS
                SystemInfo.isMac -> MAC
                else -> LINUX
            }
        }
    }
}
