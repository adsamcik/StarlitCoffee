package com.adsamcik.starlitcoffee.scan.observability

import android.content.Context
import android.content.Intent
import android.os.Build

object ScanBugReporter {

    fun buildReportText(context: Context): String {
        val appVersion = try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }

        val sessions = ScanSessionRingBuffer.getForReport(context, count = 5)

        return buildString {
            appendLine("=== StarlitCoffee Scan Report ===")
            appendLine()
            appendLine("App version: $appVersion")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
            appendLine()
            appendLine("--- Recent Scan Sessions ---")
            appendLine(sessions)
        }
    }

    fun shareReport(context: Context) {
        val text = buildReportText(context)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "StarlitCoffee Scan Report")
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(intent, "StarlitCoffee Scan Report")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
