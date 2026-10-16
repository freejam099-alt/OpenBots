/*
 * Wiki — crash logger sederhana.
 *
 * Menyimpan stack trace crash ke file di folder eksternal aplikasi supaya penyebabnya bisa
 * diperiksa lewat pengelola file: Android/data/com.google.ai.edge.gallery/files/wiki_crash.txt
 */

package com.google.ai.edge.gallery

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WikiCrashLogger {
  private const val TAG = "AGWikiCrashLogger"
  private const val FILE_NAME = "wiki_crash.txt"
  private const val MAX_READ_CHARS = 60_000

  @Volatile private var installed = false

  /** Pasang handler supaya crash apa pun tercatat dulu sebelum aplikasi ditutup sistem. */
  fun install(context: Context) {
    if (installed) {
      return
    }
    installed = true
    val appContext = context.applicationContext
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      try {
        val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        File(dir, FILE_NAME)
          .appendText("\n=== $timestamp ===\n${Log.getStackTraceString(throwable)}\n")
      } catch (e: Throwable) {
        Log.e(TAG, "Failed to write crash log", e)
      }
      previous?.uncaughtException(thread, throwable)
    }
  }

  /** Baca isi log crash (jika ada) tanpa menghapusnya. */
  fun read(context: Context): String? {
    return try {
      val dir = context.getExternalFilesDir(null) ?: context.filesDir
      val file = File(dir, FILE_NAME)
      if (file.exists()) {
        file.readText().trim().takeLast(MAX_READ_CHARS).takeIf { it.isNotEmpty() }
      } else {
        null
      }
    } catch (e: Throwable) {
      Log.e(TAG, "Failed to read crash log", e)
      null
    }
  }

  /** Hapus log crash setelah ditampilkan/disalin. */
  fun clear(context: Context) {
    try {
      val dir = context.getExternalFilesDir(null) ?: context.filesDir
      File(dir, FILE_NAME).delete()
    } catch (e: Throwable) {
      Log.e(TAG, "Failed to clear crash log", e)
    }
  }
}
