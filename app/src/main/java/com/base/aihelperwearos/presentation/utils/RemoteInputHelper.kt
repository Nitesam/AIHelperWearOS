package com.base.aihelperwearos.presentation.utils

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import androidx.wear.input.RemoteInputIntentHelper

import com.base.aihelperwearos.R

...

object RemoteInputHelper {
    const val EXTRA_VOICE_REPLY = "extra_voice_reply"

    fun createRemoteInputIntent(label: String): Intent {
        val remoteInput = RemoteInput.Builder(EXTRA_VOICE_REPLY)
            .setLabel(label)
            .setAllowFreeFormInput(true)
            .build()

        return RemoteInputIntentHelper.createActionRemoteInputIntent().apply {
            RemoteInputIntentHelper.putRemoteInputsExtra(this, listOf(remoteInput))
        }
    }

    fun getTextFromIntent(data: Intent?): String? {
        data ?: return null
        val results: Bundle = RemoteInput.getResultsFromIntent(data) ?: return null
        return results.getCharSequence(EXTRA_VOICE_REPLY)?.toString()
    }
}

