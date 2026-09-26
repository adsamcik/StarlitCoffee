package com.adsamcik.starlitcoffee.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import com.adsamcik.starlitcoffee.BuildConfig
import dev.tracebox.Tracebox
import dev.tracebox.api.LogTemplate

private object MindlayerStartupTraceboxTemplates {
    val MINDLAYER_PACKAGE_LOOKUP_WAS_BLOCKED = LogTemplate.of("Mindlayer package lookup was blocked")
    val MINDLAYER_PACKAGE_LOOKUP_FAILED = LogTemplate.of("Mindlayer package lookup failed")
    val MINDLAYER_MARKET_LINK_RESOLUTION_WAS_BLOCKED = LogTemplate.of("Mindlayer market link resolution was blocked")
    val MINDLAYER_MARKET_LINK_RESOLUTION_FAILED = LogTemplate.of("Mindlayer market link resolution failed")
    val NO_ACTIVITY_CAN_OPEN_THE_MINDLAYER_INSTALL = LogTemplate.of("No activity can open the Mindlayer install link")
    val MINDLAYER_INSTALL_LINK_WAS_BLOCKED = LogTemplate.of("Mindlayer install link was blocked")
    val MINDLAYER_INSTALL_LINK_FAILED = LogTemplate.of("Mindlayer install link failed")
}

object MindlayerAvailability {
    private val packageNames: List<String>
        get() = buildList {
            add(MindlayerInstallLink.PACKAGE_NAME)
            if (BuildConfig.DEBUG) {
                add("com.adsamcik.mindlayer.debug")
                add("com.adsamcik.mindlayer.service.debug")
            }
        }

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun isInstalled(context: Context): Boolean =
        isSupported() && packageNames.any { packageName ->
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (error: SecurityException) {
            Tracebox.log.error(error, MindlayerStartupTraceboxTemplates.MINDLAYER_PACKAGE_LOOKUP_WAS_BLOCKED)
            false
        } catch (error: RuntimeException) {
            Tracebox.log.error(error, MindlayerStartupTraceboxTemplates.MINDLAYER_PACKAGE_LOOKUP_FAILED)
            false
        }
    }

}

object MindlayerInstallLink {
    const val PACKAGE_NAME = "com.adsamcik.mindlayer"
    const val PLAY_STORE_URI = "market://details?id=$PACKAGE_NAME"

    private val playStoreUri: Uri = PLAY_STORE_URI.toUri()
    private val webStoreUri: Uri = "https://play.google.com/store/apps/details?id=$PACKAGE_NAME".toUri()

    fun open(context: Context): Boolean {
        val marketIntent = Intent(Intent.ACTION_VIEW, playStoreUri)
        try {
            if (marketIntent.resolveActivity(context.packageManager) != null &&
                tryStartActivity(context, marketIntent)
            ) {
                return true
            }
        } catch (error: SecurityException) {
            Tracebox.log.error(error, MindlayerStartupTraceboxTemplates.MINDLAYER_MARKET_LINK_RESOLUTION_WAS_BLOCKED)
        } catch (error: RuntimeException) {
            Tracebox.log.error(error, MindlayerStartupTraceboxTemplates.MINDLAYER_MARKET_LINK_RESOLUTION_FAILED)
        }
        return tryStartActivity(context, Intent(Intent.ACTION_VIEW, webStoreUri))
    }

    private fun tryStartActivity(context: Context, intent: Intent): Boolean {
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (error: ActivityNotFoundException) {
            Tracebox.log.error(error, MindlayerStartupTraceboxTemplates.NO_ACTIVITY_CAN_OPEN_THE_MINDLAYER_INSTALL)
            false
        } catch (error: SecurityException) {
            Tracebox.log.error(error, MindlayerStartupTraceboxTemplates.MINDLAYER_INSTALL_LINK_WAS_BLOCKED)
            false
        } catch (error: RuntimeException) {
            Tracebox.log.error(error, MindlayerStartupTraceboxTemplates.MINDLAYER_INSTALL_LINK_FAILED)
            false
        }
    }

}
