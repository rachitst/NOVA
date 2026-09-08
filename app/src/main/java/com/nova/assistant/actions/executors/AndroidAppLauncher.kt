package com.nova.assistant.actions.executors

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.nova.assistant.actions.model.ActionErrorType
import com.nova.assistant.actions.model.ActionResult
import com.nova.assistant.actions.model.AppTarget
import com.nova.assistant.core.logging.NovaLogger

/**
 * Android implementation of AppLauncher using PackageManager and explicit launch intents.
 */
class AndroidAppLauncher(
    private val context: Context
) : AppLauncher {

    override fun isAppInstalled(target: AppTarget): Boolean {
        val packageManager = context.packageManager
        return when (target) {
            is AppTarget.KnownApp -> {
                val packages = listOf(target.primaryPackage) + target.fallbackPackages
                packages.any { pkg -> isPackageAvailable(packageManager, pkg) }
            }
            is AppTarget.NamedApp -> {
                resolveNamedAppPackage(packageManager, target.rawName) != null
            }
        }
    }

    override fun launchApp(target: AppTarget): ActionResult {
        val packageManager = context.packageManager
        val packageName = when (target) {
            is AppTarget.KnownApp -> {
                val candidatePackages = listOf(target.primaryPackage) + target.fallbackPackages
                candidatePackages.firstOrNull { pkg -> isPackageAvailable(packageManager, pkg) }
            }
            is AppTarget.NamedApp -> {
                resolveNamedAppPackage(packageManager, target.rawName)
            }
        }

        if (packageName == null) {
            NovaLogger.w("AppLauncher", "Target application not installed: ${target.displayName}")
            return ActionResult.Failure(
                spokenResponse = "${target.displayName} is not installed on this device.",
                displayFeedback = "Could not find application: ${target.displayName}",
                errorType = ActionErrorType.APP_NOT_FOUND
            )
        }

        return try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                ActionResult.Failure(
                    spokenResponse = "Cannot launch ${target.displayName}.",
                    displayFeedback = "No launcher intent available for $packageName",
                    errorType = ActionErrorType.EXECUTION_FAILED
                )
            } else {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                NovaLogger.i("AppLauncher", "Successfully launched ${target.displayName} ($packageName)")
                ActionResult.Success(
                    spokenResponse = "Opening ${target.displayName}.",
                    displayFeedback = "Launched ${target.displayName}"
                )
            }
        } catch (e: ActivityNotFoundException) {
            NovaLogger.e("AppLauncher", "Activity not found when launching $packageName", e)
            ActionResult.Failure(
                spokenResponse = "Failed to open ${target.displayName}.",
                displayFeedback = "Activity not found for $packageName",
                errorType = ActionErrorType.APP_NOT_FOUND
            )
        } catch (e: SecurityException) {
            NovaLogger.e("AppLauncher", "Security exception launching $packageName", e)
            ActionResult.Failure(
                spokenResponse = "Permission denied to open ${target.displayName}.",
                displayFeedback = "Security exception launching $packageName",
                errorType = ActionErrorType.PERMISSION_REQUIRED
            )
        } catch (e: Exception) {
            NovaLogger.e("AppLauncher", "Unexpected error launching $packageName", e)
            ActionResult.Failure(
                spokenResponse = "An error occurred while opening ${target.displayName}.",
                displayFeedback = "Error: ${e.localizedMessage ?: "Unknown error"}",
                errorType = ActionErrorType.EXECUTION_FAILED
            )
        }
    }

    private fun isPackageAvailable(packageManager: PackageManager, packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            // Also check via launch intent in case of query limitations
            packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (e: Exception) {
            false
        }
    }

    private fun resolveNamedAppPackage(packageManager: PackageManager, rawName: String): String? {
        val query = rawName.trim().lowercase()
        // Check known apps first
        val known = AppTarget.KnownApp.findByIdentifier(query)
        if (known != null && isPackageAvailable(packageManager, known.primaryPackage)) {
            return known.primaryPackage
        }

        // Query launcher activities
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = try {
            packageManager.queryIntentActivities(intent, 0)
        } catch (e: Exception) {
            emptyList()
        }

        for (resolveInfo in resolveInfos) {
            val label = resolveInfo.loadLabel(packageManager)?.toString()?.lowercase() ?: ""
            if (label == query || label.contains(query) || query.contains(label)) {
                return resolveInfo.activityInfo.packageName
            }
        }
        return null
    }
}
