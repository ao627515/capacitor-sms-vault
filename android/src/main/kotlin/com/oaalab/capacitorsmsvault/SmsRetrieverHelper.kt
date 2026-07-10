package com.oaalab.capacitorsmsvault

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.getcapacitor.Logger
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status

/**
 * Classe d'aide pour encapsuler l'utilisation de la SMS Retriever API.
 * Permet de capter un message OTP sans aucune permission SMS (READ_SMS/RECEIVE_SMS).
 * Le cycle de vie est à usage unique (s'autodétruit après succès ou timeout de 5 minutes).
 */
class SmsRetrieverHelper(
    private val context: Context,
    private val onOtpReceived: (String) -> Unit
) {
    companion object {
        private const val TAG = "SmsRetrieverHelper"
    }

    private var receiver: BroadcastReceiver? = null
    private var isRegistered = false

    /**
     * Démarre le client SMS Retriever de Google Play Services et enregistre
     * le receiver d'écoute temporaire.
     */
    fun start() {
        if (isRegistered) {
            Logger.info(TAG, "SmsRetriever déjà démarré et à l'écoute")
            return
        }

        val client = SmsRetriever.getClient(context)
        val task = client.startSmsRetriever()

        task.addOnSuccessListener {
            Logger.info(TAG, "Client SMS Retriever démarré avec succès")
            registerReceiver()
        }
        task.addOnFailureListener { e ->
            Logger.error(TAG, "Échec du démarrage du client SMS Retriever", e)
        }
    }

    /**
     * Enregistre dynamiquement le BroadcastReceiver pour intercepter le signal de succès ou de timeout.
     */
    private fun registerReceiver() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == SmsRetriever.SMS_RETRIEVED_ACTION) {
                    val extras = intent.extras
                    val status = extras?.let {
                        androidx.core.os.BundleCompat.getParcelable(it, SmsRetriever.EXTRA_STATUS, Status::class.java)
                    }

                    when (status?.statusCode) {
                        CommonStatusCodes.SUCCESS -> {
                            val message = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE)
                            if (message != null) {
                                Logger.info(TAG, "SMS d'OTP récupéré avec succès")
                                onOtpReceived(message)
                            }
                            stop() // Arrête le receiver après réception unique
                        }
                        CommonStatusCodes.TIMEOUT -> {
                            Logger.info(TAG, "SMS Retriever expiré après 5 minutes")
                            stop() // Arrête le receiver en cas de timeout
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        
        // Sur Android 13+ (API 33+), le flag RECEIVER_NOT_EXPORTED est requis pour des raisons de sécurité
        // puisque le broadcast est directement destiné à notre package par le service Google Play.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        isRegistered = true
    }

    /**
     * Libère proprement le BroadcastReceiver de l'activité.
     */
    fun stop() {
        if (isRegistered && receiver != null) {
            try {
                context.unregisterReceiver(receiver)
                Logger.info(TAG, "BroadcastReceiver SMS Retriever désenregistré")
            } catch (e: Exception) {
                Logger.warn(TAG, "Erreur lors du désenregistrement du receiver SMS Retriever : ${e.message}")
            } finally {
                receiver = null
                isRegistered = false
            }
        }
    }
}
