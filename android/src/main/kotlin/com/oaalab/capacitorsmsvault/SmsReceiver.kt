package com.oaalab.capacitorsmsvault

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.getcapacitor.JSObject
import com.getcapacitor.Logger
import java.lang.ref.WeakReference

/**
 * BroadcastReceiver pour l'interception des SMS entrants en temps réel (F4).
 *
 * Ce receiver est enregistré/désenregistré dynamiquement par SmsVaultPlugin
 * via startListening() / stopListening(). Il notifie le plugin Capacitor
 * à chaque SMS reçu via notifyListeners("smsReceived").
 *
 * WeakReference utilisée pour éviter les fuites mémoire si le plugin
 * est détruit avant le receiver.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    /**
     * Référence faible vers le plugin Capacitor parent.
     * Initialisée par SmsVaultPlugin.load().
     */
    var pluginRef: WeakReference<SmsVaultPlugin>? = null

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Logger.warn(TAG, "Received unexpected intent action: ${intent?.action}")
            return
        }

        val plugin = pluginRef?.get() ?: run {
            Logger.warn(TAG, "Plugin reference is null or garbage collected — ignoring SMS")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            Logger.warn(TAG, "No messages found in SMS_RECEIVED intent")
            return
        }

        for (smsMessage in messages) {
            try {
                val smsObj = JSObject().apply {
                    // L'id n'est pas disponible via PDU — sera peuplé si besoin via ContentResolver
                    put("id", "")
                    put("address", smsMessage.displayOriginatingAddress ?: "")
                    put("body", smsMessage.messageBody ?: "")
                    put("date", smsMessage.timestampMillis)
                    put("type", "inbox")
                }
                plugin.notifySmsReceived(smsObj)
                Logger.info(TAG, "SMS received from: ${smsMessage.displayOriginatingAddress}")
            } catch (e: Exception) {
                Logger.error(TAG, "Failed to parse SMS PDU or notify listener", e)
                // On continue avec les autres messages du même batch
            }
        }
    }
}
