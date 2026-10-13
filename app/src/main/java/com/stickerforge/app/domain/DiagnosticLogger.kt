package com.stickerforge.app.domain

import android.util.Log

object DiagnosticLogger {
    private const val TAG = "StickerForgeDiag"
    private val buffer = StringBuilder()

    fun clear() {
        synchronized(buffer) {
            buffer.setLength(0)
        }
    }

    fun log(stage: String, message: String) {
        Log.i(TAG, "[$stage] $message")
        synchronized(buffer) {
            buffer.append("[$stage] ").append(message).append("\n")
        }
    }

    fun logError(stage: String, message: String, throwable: Throwable? = null) {
        Log.e(TAG, "[$stage] $message", throwable)
        synchronized(buffer) {
            buffer.append("[ERROR:$stage] ").append(message)
            if (throwable != null) {
                buffer.append(" -> ").append(throwable.javaClass.simpleName).append(": ").append(throwable.message ?: "").append("\n")
                val trace = throwable.stackTraceToString().lines().take(6).joinToString("\n")
                buffer.append(trace).append("\n")
            } else {
                buffer.append("\n")
            }
        }
    }

    fun getLog(): String {
        synchronized(buffer) {
            return buffer.toString()
        }
    }
}
