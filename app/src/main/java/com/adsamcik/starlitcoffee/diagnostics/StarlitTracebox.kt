package com.adsamcik.starlitcoffee.diagnostics

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import dev.tracebox.Tracebox
import dev.tracebox.TraceboxConfiguration
import dev.tracebox.api.CaptureKind
import dev.tracebox.api.TraceboxPolicy
import java.io.File

/** Starlit Coffee's single production diagnostics installation contract. */
object StarlitTracebox {
    @Volatile
    private var nativeCaptureAvailable = true

    val supportedCaptureKinds: Set<CaptureKind>
        get() = captureKinds(nativeCaptureAvailable)

    val defaultPolicy: TraceboxPolicy
        get() = defaultPolicy(nativeCaptureAvailable)

    fun install(context: Context) {
        val enableNativeCapture = isTraceboxNativeCaptureSupported(
            is64BitProcess = Process.is64Bit(),
            supportedAbis = Build.SUPPORTED_ABIS,
        )
        nativeCaptureAvailable = enableNativeCapture
        Tracebox.install(
            context,
            TraceboxConfiguration.Builder()
                .setInitialPolicy(defaultPolicy(enableNativeCapture))
                .setNativeCaptureEnabled(enableNativeCapture)
                .setPersistRequestedProfile(true)
                .build(),
        )
    }

    fun isHandlerProcess(context: Context): Boolean =
        isTraceboxHandlerProcessName(currentProcessName(context))

    private fun currentProcessName(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val activityManagerName = activityManager?.runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }
                ?.processName
            activityManagerName ?: runCatching {
                File("/proc/self/cmdline").inputStream().bufferedReader().use { reader ->
                    reader.readText().substringBefore('\u0000').takeIf(String::isNotBlank)
                }
            }.getOrNull()
    }
}

internal fun isTraceboxNativeCaptureSupported(
    is64BitProcess: Boolean,
    supportedAbis: Array<String>,
): Boolean = is64BitProcess && supportedAbis.any(TRACEBOX_NATIVE_ABIS::contains)

private fun captureKinds(nativeCaptureEnabled: Boolean): Set<CaptureKind> = buildSet {
    addAll(MANAGED_CAPTURE_KINDS)
    if (nativeCaptureEnabled) add(CaptureKind.NATIVE_CRASH)
}

private fun defaultPolicy(nativeCaptureEnabled: Boolean): TraceboxPolicy =
    TraceboxPolicy.standard().copy(captures = captureKinds(nativeCaptureEnabled))

internal fun isTraceboxHandlerProcessName(processName: String?): Boolean =
    processName?.endsWith(TRACEBOX_HANDLER_PROCESS_SUFFIX) == true

private val MANAGED_CAPTURE_KINDS = setOf(
    CaptureKind.JVM_CRASH,
    CaptureKind.HANDLED_EXCEPTION,
    CaptureKind.ANR,
    CaptureKind.OS_EXIT,
)
private val TRACEBOX_NATIVE_ABIS = setOf("arm64-v8a", "x86_64")
private const val TRACEBOX_HANDLER_PROCESS_SUFFIX = ":tracebox_handler"
