package com.example.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.core.AppResult
import com.example.database.MekkaPhoneDatabase
import com.example.repository.MekkaPhoneRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Real Android BroadcastReceiver that captures incoming SMS messages from electronic wallet providers
 * (Vodafone Cash, Orange Cash, e& money, InstaPay, Axis Pay) and routes them into the
 * SMS Intelligence Review Queue (`AWAITING_CONFIRMATION`) without ever auto-posting to the Financial Ledger.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages.firstOrNull()?.displayOriginatingAddress?.trim().orEmpty()
        val fullBody = messages.joinToString(separator = "") { it.displayMessageBody.orEmpty() }.trim()
        if (fullBody.isBlank()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = MekkaPhoneDatabase.getInstance(context.applicationContext)
                val repository = MekkaPhoneRepository(database)
                repository.ensureFoundationInitialized()

                val activeShopId = repository.activeShopId
                // Respect the shop's "sms_auto_capture_enabled" setting (defaults to enabled)
                val autoCaptureSetting = database.coreShopAuthDao()
                    .getSetting(activeShopId, "sms_auto_capture_enabled")
                    ?.settingValue ?: "true"

                if (autoCaptureSetting.equals("true", ignoreCase = true)) {
                    repository.ingestSmsMessage(
                        senderAddress = sender.ifBlank { "SMS-PROVIDER" },
                        rawMessageBody = fullBody,
                        actorUserId = repository.activeUserId
                    )
                }
            } catch (_: Exception) {
                // Safely ignore malformed intents without crashing background receiver
            } finally {
                pendingResult.finish()
            }
        }
    }
}
