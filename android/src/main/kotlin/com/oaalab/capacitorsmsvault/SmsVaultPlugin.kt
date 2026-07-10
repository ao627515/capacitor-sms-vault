package com.oaalab.capacitorsmsvault

import android.Manifest
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.provider.Telephony
import com.getcapacitor.JSObject
import com.getcapacitor.Logger
import com.getcapacitor.PermissionState
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import java.lang.ref.WeakReference

/**
 * Plugin Capacitor principal pour capacitor-sms-vault.
 *
 * Ce fichier est le pont entre la couche JavaScript/TypeScript et le système Android.
 * Il ne contient aucune logique métier — toute la logique est dans :
 *   - SmsReader    : lecture de l'historique via ContentResolver
 *   - SmsReceiver  : interception des SMS entrants via BroadcastReceiver
 *
 * Fonctions implémentées (V1) :
 *   F1 - Gestion des permissions (READ_SMS, RECEIVE_SMS)
 *   F2 - Default SMS handler (vérification + demande)
 *   F3 - Lecture historique SMS
 *   F4 - Interception SMS temps réel
 *   F6 - Export JSON (inclus dans F3/F4 via JSObject)
 */
@CapacitorPlugin(
    name = "SmsVault",
    permissions = [
        Permission(
            alias = "readSms",
            strings = [Manifest.permission.READ_SMS]
        ),
        Permission(
            alias = "receiveSms",
            strings = [Manifest.permission.RECEIVE_SMS]
        )
    ]
)
class SmsVaultPlugin : Plugin() {

    companion object {
        private const val TAG = "SmsVaultPlugin"
        // Intent action for changing default SMS app on Android 6-9
        private const val ACTION_CHANGE_DEFAULT_SMS_APP =
            "android.provider.Telephony.ACTION_CHANGE_DEFAULT_SMS_APP"
        private const val EXTRA_PACKAGE_NAME =
            "android.provider.Telephony.EXTRA_PACKAGE_NAME"
    }

    private val smsReceiver = SmsReceiver()
    private var isListening = false
    private lateinit var smsReader: SmsReader
    private var smsRetrieverHelper: SmsRetrieverHelper? = null

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    override fun load() {
        smsReader = SmsReader(context)
        smsReceiver.pluginRef = WeakReference(this)
        Logger.info(TAG, "SmsVaultPlugin loaded")
    }

    /**
     * Méthode publique appelée par SmsReceiver pour notifier JS d'un SMS entrant.
     * notifyListeners() est protected dans Plugin — on l'expose via ce wrapper public.
     */
    fun notifySmsReceived(smsObj: JSObject) {
        notifyListeners("smsReceived", smsObj)
    }

    override fun handleOnDestroy() {
        // Nettoyage : désenregistrer le receiver si l'activité est détruite
        if (isListening) {
            try {
                context.unregisterReceiver(smsReceiver)
                isListening = false
                Logger.info(TAG, "SmsReceiver unregistered on destroy")
            } catch (e: Exception) {
                Logger.warn(TAG, "handleOnDestroy: receiver was not registered")
            }
        }
        smsRetrieverHelper?.stop()
        super.handleOnDestroy()
    }

    // ─── F1 : Gestion des permissions ────────────────────────────────────────

    /**
     * Vérifie l'état des permissions READ_SMS et RECEIVE_SMS sans les demander.
     */
    @PluginMethod
    override fun checkPermissions(call: PluginCall) {
        val readState = getPermissionState("readSms")
        val receiveState = getPermissionState("receiveSms")
        call.resolve(buildPermissionResult(readState, receiveState))
    }

    /**
     * Demande les permissions READ_SMS et RECEIVE_SMS à l'utilisateur.
     */
    @PluginMethod
    override fun requestPermissions(call: PluginCall) {
        // Si les deux permissions sont déjà accordées, retourner directement
        if (getPermissionState("readSms") == PermissionState.GRANTED &&
            getPermissionState("receiveSms") == PermissionState.GRANTED
        ) {
            checkPermissions(call)
            return
        }
        requestPermissionForAliases(arrayOf("readSms", "receiveSms"), call, "permissionsCallback")
    }

    @PermissionCallback
    private fun permissionsCallback(call: PluginCall) {
        val readState = getPermissionState("readSms")
        val receiveState = getPermissionState("receiveSms")
        call.resolve(buildPermissionResult(readState, receiveState))
    }

    private fun buildPermissionResult(
        readState: PermissionState?,
        receiveState: PermissionState?
    ): JSObject {
        return JSObject().apply {
            put("readSms", readState?.toString() ?: "prompt")
            put("receiveSms", receiveState?.toString() ?: "prompt")
        }
    }

    // ─── F2 : Default SMS App ────────────────────────────────────────────────

    /**
     * Vérifie si l'app est l'application SMS par défaut du système.
     */
    @PluginMethod
    fun isDefaultSmsApp(call: PluginCall) {
        val defaultPackage = Telephony.Sms.getDefaultSmsPackage(context)
        val isDefault = defaultPackage == context.packageName
        Logger.info(TAG, "isDefaultSmsApp: $isDefault (default=$defaultPackage, this=${context.packageName})")
        call.resolve(JSObject().put("isDefault", isDefault))
    }

    /**
     * Ouvre le dialogue système pour changer l'application SMS par défaut.
     * Sur Android 10+ (API 29+) : utilise RoleManager.
     * Sur Android 6-9 : utilise l'intent ACTION_CHANGE_DEFAULT_SMS_APP.
     */
    @PluginMethod
    fun requestDefaultSmsApp(call: PluginCall) {
        // Vérifier d'abord si on est déjà l'app par défaut
        if (Telephony.Sms.getDefaultSmsPackage(context) == context.packageName) {
            call.resolve(JSObject().put("isDefault", true))
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ : RoleManager
            val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager == null) {
                call.reject("ROLE_MANAGER_UNAVAILABLE", "RoleManager not available on this device")
                return
            }
            val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS)
            startActivityForResult(call, intent, "defaultSmsAppCallback")
        } else {
            // Android 6-9 : intent classique
            val intent = android.content.Intent(ACTION_CHANGE_DEFAULT_SMS_APP).apply {
                putExtra(EXTRA_PACKAGE_NAME, context.packageName)
            }
            startActivityForResult(call, intent, "defaultSmsAppCallback")
        }
    }

    @ActivityCallback
    private fun defaultSmsAppCallback(call: PluginCall?, result: androidx.activity.result.ActivityResult) {
        val resolveCall = call ?: return
        val isDefault = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        Logger.info(TAG, "defaultSmsAppCallback: isDefault=$isDefault")
        resolveCall.resolve(JSObject().put("isDefault", isDefault))
    }

    // ─── F3 : Lecture historique SMS ─────────────────────────────────────────

    /**
     * Récupère la liste des SMS avec filtres optionnels.
     * Requiert READ_SMS. Sur Android 10+, l'app doit aussi être l'app SMS par défaut.
     */
    @PluginMethod
    fun getSmsList(call: PluginCall) {
        if (getPermissionState("readSms") != PermissionState.GRANTED) {
            call.reject("PERMISSION_DENIED", "READ_SMS permission is required to read SMS history")
            return
        }

        try {
            val address = call.getString("address")
            val since: Long? = call.getLong("since")
            val limit: Int? = call.getInt("limit")

            val messages = smsReader.getSmsList(address, since, limit)
            call.resolve(JSObject().put("messages", messages))
        } catch (e: SecurityException) {
            call.reject(
                "PERMISSION_DENIED",
                "Access to SMS was denied by the system. Make sure the app is set as the default SMS app on Android 10+.",
                e
            )
        } catch (e: Exception) {
            Logger.error(TAG, "getSmsList failed", e)
            call.reject("READ_FAILED", "Failed to read SMS list: ${e.message}", e)
        }
    }

    // ─── F4 : Interception SMS temps réel ────────────────────────────────────

    /**
     * Démarre l'écoute des SMS entrants via BroadcastReceiver.
     * Requiert RECEIVE_SMS.
     */
    @PluginMethod
    fun startListening(call: PluginCall) {
        if (getPermissionState("receiveSms") != PermissionState.GRANTED) {
            call.reject("PERMISSION_DENIED", "RECEIVE_SMS permission is required to listen for incoming SMS")
            return
        }

        if (isListening) {
            Logger.info(TAG, "startListening: already listening, no-op")
            call.resolve()
            return
        }

        try {
            val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
                priority = IntentFilter.SYSTEM_HIGH_PRIORITY
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ : le flag RECEIVER_NOT_EXPORTED est requis pour les receivers non-exportés
                context.registerReceiver(smsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(smsReceiver, filter)
            }

            isListening = true
            Logger.info(TAG, "SMS listening started")
            call.resolve()
        } catch (e: Exception) {
            Logger.error(TAG, "startListening failed", e)
            call.reject("START_FAILED", "Failed to start SMS listener: ${e.message}", e)
        }
    }

    /**
     * Arrête l'écoute des SMS entrants et libère le BroadcastReceiver.
     */
    @PluginMethod
    fun stopListening(call: PluginCall) {
        if (!isListening) {
            Logger.info(TAG, "stopListening: not currently listening, no-op")
            call.resolve()
            return
        }

        try {
            context.unregisterReceiver(smsReceiver)
            isListening = false
            Logger.info(TAG, "SMS listening stopped")
        } catch (e: Exception) {
            // Ne pas rejeter l'appel si le receiver n'était pas enregistré
            Logger.warn(TAG, "stopListening: receiver was not registered — ${e.message}")
            isListening = false
        }
        call.resolve()
    }

    // ─── F5 : SMS Retriever (OTP) ──────────────────────────────────────────

    /**
     * Démarre la détection d'OTP via la SMS Retriever API.
     */
    @PluginMethod
    fun startSmsRetriever(call: PluginCall) {
        try {
            smsRetrieverHelper?.stop()

            val helper = SmsRetrieverHelper(context) { message ->
                val data = JSObject().apply {
                    put("message", message)
                }
                notifyListeners("otpReceived", data)
            }
            smsRetrieverHelper = helper
            helper.start()
            call.resolve()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to start SMS Retriever", e)
            call.reject("RETRIEVER_START_FAILED", "Failed to start SMS Retriever: ${e.message}", e)
        }
    }
}
