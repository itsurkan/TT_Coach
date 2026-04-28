package com.ttcoachai.managers

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * Handles finding and loading sidecar JSON files for videos.
 */
class DebugJsonMapper(private val context: Context) {
    private val TAG = "DebugJsonMapper"

    fun getJsonFromAssets(videoAssetPath: String): String? {
        val posesPath = videoAssetPath.substringBeforeLast(".") + "_poses.json"
        return try {
            context.assets.open(posesPath).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.d(TAG, "No poses file found at: $posesPath")
            null
        }
    }

    fun getJsonForUri(uri: Uri): String? {
        if (uri.scheme == android.content.ContentResolver.SCHEME_ANDROID_RESOURCE) {
            try {
                val id = uri.lastPathSegment?.toIntOrNull()
                if (id != null) {
                    val videoName = context.resources.getResourceEntryName(id)
                    val posesResId = context.resources.getIdentifier("${videoName}_poses", "raw", context.packageName)
                    if (posesResId != 0) {
                        return context.resources.openRawResource(posesResId).bufferedReader().use { it.readText() }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading raw JSON resource", e)
            }
        }

        getJsonFileForUri(uri)?.let { file ->
            if (file.exists()) {
                return try { file.readText() } catch (e: Exception) { null }
            }
        }
        return null
    }

    private fun getJsonFileForUri(uri: Uri): File? {
        try {
            var filename: String? = null
            if (uri.scheme == android.content.ContentResolver.SCHEME_ANDROID_RESOURCE) {
                uri.lastPathSegment?.toIntOrNull()?.let { filename = context.resources.getResourceEntryName(it) }
            } else if (uri.scheme == android.content.ContentResolver.SCHEME_FILE) {
                val file = File(uri.path ?: return null)
                val sibling = File(file.parent, "${file.nameWithoutExtension}_poses.json")
                if (sibling.exists()) return sibling
                filename = file.nameWithoutExtension
            }
            
            if (filename == null) {
                filename = uri.lastPathSegment?.substringBeforeLast(".")
            }

            if (filename != null) {
                val file = File(context.getExternalFilesDir(null), "${filename}_poses.json")
                if (file.exists()) return file
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving JSON file", e)
        }
        return null
    }
}
