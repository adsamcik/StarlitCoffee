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
            Tracebox.log.error(error, "Mindlayer package lookup was blocked")
            false
        } catch (error: RuntimeException) {
            Tracebox.log.error(error, "Mindlayer package lookup failed")
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
            Tracebox.log.error(error, "Mindlayer market link resolution was blocked")
        } catch (error: RuntimeException) {
            Tracebox.log.error(error, "Mindlayer market link resolution failed")
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
            Tracebox.log.error(error, "No activity can open the Mindlayer install link")
            false
        } catch (error: SecurityException) {
            Tracebox.log.error(error, "Mindlayer install link was blocked")
            false
        } catch (error: RuntimeException) {
            Tracebox.log.error(error, "Mindlayer install link failed")
            false
        }
    }

}

internal fun shouldOfferMindlayerConnection(
    isInstalled: Boolean,
    connectionAttemptFinished: Boolean,
    isConnected: Boolean,
    offerHandled: Boolean,
): Boolean = isInstalled && connectionAttemptFinished && !isConnected && !offerHandled
