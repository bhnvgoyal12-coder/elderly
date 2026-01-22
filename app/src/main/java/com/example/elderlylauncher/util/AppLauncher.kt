package com.example.elderlylauncher.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import com.example.elderlylauncher.R

class AppLauncher(private val context: Context) {

    companion object {
        // Messages apps
        private val MESSAGES_PACKAGES = listOf(
            "com.samsung.android.messaging",     // Samsung Messages
            "com.google.android.apps.messaging"  // Google Messages
        )

        // Dialer apps
        private val DIALER_PACKAGES = listOf(
            "com.samsung.android.dialer",        // Samsung Phone
            "com.google.android.dialer",         // Google Phone
            "com.android.dialer"                 // AOSP Dialer
        )

        // Camera apps
        private val CAMERA_PACKAGES = listOf(
            "com.sec.android.app.camera",        // Samsung Camera
            "com.google.android.GoogleCamera"    // Google Camera
        )

        // Gallery apps
        private val GALLERY_PACKAGES = listOf(
            "com.sec.android.gallery3d",         // Samsung Gallery
            "com.google.android.apps.photos"     // Google Photos
        )

        // WhatsApp
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
    }

    /**
     * Opens the phone dialer app
     */
    fun openDialer() {
        if (!launchAppByPackages(DIALER_PACKAGES)) {
            // Fallback: use implicit intent
            val intent = Intent(Intent.ACTION_DIAL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            safeStartActivity(intent, R.string.error_no_dialer)
        }
    }

    /**
     * Opens the dialer with a specific number
     */
    fun dialNumber(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        safeStartActivity(intent, R.string.error_cannot_dial)
    }

    /**
     * Opens the SMS/Messages app
     */
    fun openMessages() {
        if (!launchAppByPackages(MESSAGES_PACKAGES)) {
            // Fallback: implicit intent
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_MESSAGING)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            safeStartActivity(intent, R.string.error_no_messages)
        }
    }

    /**
     * Opens WhatsApp
     */
    fun openWhatsApp() {
        var intent = context.packageManager.getLaunchIntentForPackage(WHATSAPP_PACKAGE)

        if (intent == null) {
            intent = context.packageManager.getLaunchIntentForPackage(WHATSAPP_BUSINESS_PACKAGE)
        }

        if (intent != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                intent.addCategory(Intent.CATEGORY_LAUNCHER)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            Toast.makeText(
                context,
                R.string.error_whatsapp_not_installed,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Opens the camera app
     */
    fun openCamera() {
        if (!launchAppByPackages(CAMERA_PACKAGES)) {
            // Fallback: implicit camera intent
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            safeStartActivity(intent, R.string.error_no_camera)
        }
    }

    /**
     * Opens the gallery/photos app
     */
    fun openGallery() {
        if (!launchAppByPackages(GALLERY_PACKAGES)) {
            // Fallback: view images intent
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = "image/*"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            safeStartActivity(intent, R.string.error_no_gallery)
        }
    }

    /**
     * Attempts to launch an app from a list of package names
     * Returns true if successful, false otherwise
     */
    private fun launchAppByPackages(packages: List<String>): Boolean {
        for (packageName in packages) {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    intent.addCategory(Intent.CATEGORY_LAUNCHER)
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            }
        }
        return false
    }

    /**
     * Safely starts an activity with error handling
     */
    private fun safeStartActivity(intent: Intent, errorMessageRes: Int) {
        try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, errorMessageRes, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, errorMessageRes, Toast.LENGTH_LONG).show()
        }
    }
}
