package com.nowlsantos.mahjongai.util

import android.util.Log

/**
 * A custom logging utility that allows for easy enabling/disabling of log messages.
 * In a real application, `DEBUG_MODE` would typically be set based on `BuildConfig.DEBUG`
 * to automatically control logging based on build type (debug or release).
 */
object Logger {
    // Set to 'true' for development/debug builds to see logs.
    // Set to 'false' for release/production builds to disable most logs.
    // In a full Android project, you'd typically set this via BuildConfig.DEBUG.
    var DEBUG_MODE = true

    // A common tag for all logs from this application.
    private const val TAG = "MahjongAI"

    /**
     * Logs a debug message. Only prints if DEBUG_MODE is true.
     * @param subTag A specific sub-tag for the calling class or component (e.g., "ExpertBig2AI").
     * @param message The message to log.
     * @param throwable An optional Throwable to log its stack trace.
     */
    fun d(subTag: String, message: String, throwable: Throwable? = null) {
        if (DEBUG_MODE) {
            val fullMessage = "$subTag: $message"
            try {
                if (throwable != null) {
                    Log.d(TAG, fullMessage, throwable)
                } else {
                    Log.d(TAG, fullMessage)
                }
            } catch (_: RuntimeException) {
                println("DEBUG: [$TAG] $fullMessage")
                throwable?.printStackTrace()
            }
        }
    }

    /**
     * Logs an info message. Only prints if DEBUG_MODE is true.
     * @param subTag A specific sub-tag for the calling class or component.
     * @param message The message to log.
     * @param throwable An optional Throwable to log its stack trace.
     */
    fun i(subTag: String, message: String, throwable: Throwable? = null) {
        if (DEBUG_MODE) {
            val fullMessage = "$subTag: $message"
            try {
                if (throwable != null) {
                    Log.i(TAG, fullMessage, throwable)
                } else {
                    Log.i(TAG, fullMessage)
                }
            } catch (_: RuntimeException) {
                println("INFO: [$TAG] $fullMessage")
                throwable?.printStackTrace()
            }
        }
    }

    /**
     * Logs an error message. Prints even if DEBUG_MODE is true (consider keeping errors for release).
     * @param subTag A specific sub-tag for the calling class or component.
     * @param message The message to log.
     * @param throwable An optional Throwable to log its stack trace.
     */
    fun e(subTag: String, message: String, throwable: Throwable? = null) {
        if (DEBUG_MODE) {
            val fullMessage = "$subTag: $message"
            try {
                if (throwable != null) {
                    Log.e(TAG, fullMessage, throwable)
                } else {
                    Log.e(TAG, fullMessage)
                }
            } catch (_: RuntimeException) {
                System.err.println("ERROR: [$TAG] $fullMessage")
                throwable?.printStackTrace()
            }
        }
    }

    /**
     * Logs a warning message. Only prints if DEBUG_MODE is true.
     * @param subTag A specific sub-tag for the calling class or component.
     * @param message The message to log.
     * @param throwable An optional Throwable to log its stack trace.
     */
    fun w(subTag: String, message: String, throwable: Throwable? = null) {
        if (DEBUG_MODE) {
            val fullMessage = "$subTag: $message"
            try {
                if (throwable != null) {
                    Log.w(TAG, fullMessage, throwable)
                } else {
                    Log.w(TAG, fullMessage)
                }
            } catch (_: RuntimeException) {
                println("WARN: [$TAG] $fullMessage")
                throwable?.printStackTrace()
            }
        }
    }

    // You can add other logging levels (v, wtf) if needed.
}
