package com.adsamcik.starlitcoffee.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.adsamcik.starlitcoffee.BuildConfig

object MindlayerAvailability {
    private val packageNames: List<String>
        get() = buildList {
            add(MindlayerInstallLink.PACKAGE_NAME)
            if (BuildConfig.DEBUG) {
                add("com.adsamcik.mindlayer.debug")
                add("com.adsamcik.mindlayer.service.debug")
            }
        }

    fun isInstalled(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && packageNames.any { packageName ->
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (error: SecurityException) {
            Log.w(TAG, "Mindlayer package lookup was blocked", error)
            false
        } catch (error: RuntimeException) {
            Log.w(TAG, "Mindlayer package lookup failed", error)
            false
        }
    }

    private const val TAG = "MindlayerAvailability"
}

object MindlayerInstallLink {
    const val PACKAGE_NAME = "com.adsamcik.mindlayer"
    const val PLAY_STORE_URI = "market://details?id=$PACKAGE_NAME"

    private val playStoreUri: Uri = Uri.parse(PLAY_STORE_URI)
    private val webStoreUri: Uri = Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE_NAME")

    fun open(context: Context): Boolean {
        val marketIntent = Intent(Intent.ACTION_VIEW, playStoreUri)
        try {
            if (marketIntent.resolveActivity(context.packageManager) != null &&
                tryStartActivity(context, marketIntent)
            ) {
                return true
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "Mindlayer market link resolution was blocked", error)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Mindlayer market link resolution failed", error)
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
            Log.w(TAG, "No activity can open the Mindlayer install link", error)
            false
        } catch (error: SecurityException) {
            Log.w(TAG, "Mindlayer install link was blocked", error)
            false
        } catch (error: RuntimeException) {
            Log.w(TAG, "Mindlayer install link failed", error)
            false
        }
    }

    private const val TAG = "MindlayerInstallLink"
}

internal fun shouldOfferMindlayerConnection(
    isInstalled: Boolean,
    connectionAttemptFinished: Boolean,
    isConnected: Boolean,
    offerHandled: Boolean,
): Boolean = isInstalled && connectionAttemptFinished && !isConnected && !offerHandled
