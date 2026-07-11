package com.oaalab.capacitorsmsvault

import android.Manifest
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.SubscriptionManager
import com.getcapacitor.JSArray
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
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import java.lang.ref.WeakReference

/**
 * Plugin Capacitor principal pour capacitor-sms-vault.
 *
 * Ce fichier constitue le pont entre la couche JavaScript/TypeScript et le système Android.
 * Il ne contient aucune logique métier — celle-ci est déléguée à :
 *   - SmsReader            : lecture de l'historique via ContentResolver
 *   - SmsReceiver          : interception des SMS entrants via BroadcastReceiver
 *   - SmsRetrieverHelper   : interception des codes OTP via SMS Retriever API
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
        ),
        Permission(
            alias = "phone",
            strings = [
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS
            ]
        ),
        Permission(
            alias = "sendSms",
            strings = [Manifest.permission.SEND_SMS]
        )
    ]
)
class SmsVaultPlugin : Plugin() {

    companion object {
        private const val TAG = "SmsVaultPlugin"

        // Action d'Intent pour changer l'application SMS par défaut sur Android 6-9
        private const val ACTION_CHANGE_DEFAULT_SMS_APP =
            "android.provider.Telephony.ACTION_CHANGE_DEFAULT_SMS_APP"
        // Nom du paramètre extra pour le package de l'application
        private const val EXTRA_PACKAGE_NAME =
            "android.provider.Telephony.EXTRA_PACKAGE_NAME"
    }

    private val smsReceiver = SmsReceiver()
    private var isListening = false
    private lateinit var smsReader: SmsReader
    private var smsRetrieverHelper: SmsRetrieverHelper? = null

    // Cache pour la déduplication des événements SMS
    private val recentSmsIds = BoundedSet<String>(100)
    private val recentSmsFingerprints = BoundedSet<String>(100)

    private var smsContentObserver: SmsContentObserver? = null
    private var simSubscriptionsListener: SubscriptionManager.OnSubscriptionsChangedListener? = null
    private var userConsentCall: PluginCall? = null
    private var userConsentReceiver: BroadcastReceiver? = null

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    override fun load() {
        smsReader = SmsReader(context)
        smsReceiver.pluginRef = WeakReference(this)
        Logger.info(TAG, "SmsVaultPlugin loaded")
    }

    /**
     * Méthode publique appelée par SmsReceiver ou SmsContentObserver pour notifier JS d'un SMS entrant.
     */
    fun notifySmsReceived(smsObj: JSObject) {
        val addressVal = smsObj.getString("address") ?: ""
        val bodyVal = smsObj.getString("body") ?: ""
        val idVal = smsObj.getString("id") ?: ""

        val fingerprint = "$addressVal|$bodyVal"
        if (idVal.isNotEmpty()) {
            recentSmsIds.add(idVal)
        }
        recentSmsFingerprints.add(fingerprint)

        notifyListeners("smsReceived", smsObj)
    }

    override fun handleOnDestroy() {
        // Nettoyage : désenregistrement du receiver et de l'observer si l'activité est détruite
        if (isListening) {
            try {
                context.unregisterReceiver(smsReceiver)
            } catch (e: Exception) {
                // Ignore
            }

            smsContentObserver?.let { observer ->
                try {
                    context.contentResolver.unregisterContentObserver(observer)
                } catch (e: Exception) {
                    // Ignore
                }
                smsContentObserver = null
            }
            isListening = false
            Logger.info(TAG, "SmsReceiver and SmsContentObserver unregistered on destroy")
        }
        smsRetrieverHelper?.stop()
        simSubscriptionsListener?.let { listener ->
            try {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                subscriptionManager.removeOnSubscriptionsChangedListener(listener)
            } catch (e: Exception) {
                // Ignore
            }
            simSubscriptionsListener = null
        }
        cleanupUserConsent()
        super.handleOnDestroy()
    }

    private fun cleanupUserConsent() {
        userConsentReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Ignore
            }
            userConsentReceiver = null
        }
        userConsentCall = null
    }

    // ─── Gestion des permissions ─────────────────────────────────────────────

    /**
     * Vérifie l'état des permissions READ_SMS, RECEIVE_SMS et SEND_SMS sans les demander.
     */
    @PluginMethod
    override fun checkPermissions(call: PluginCall) {
        val readState = getPermissionState("readSms")
        val receiveState = getPermissionState("receiveSms")
        val phoneState = getPermissionState("phone")
        val sendState = getPermissionState("sendSms")
        
        val result = JSObject().apply {
            put("readSms", readState?.toString() ?: "prompt")
            put("receiveSms", receiveState?.toString() ?: "prompt")
            put("phone", phoneState?.toString() ?: "prompt")
            put("sendSms", sendState?.toString() ?: "prompt")
        }
        call.resolve(result)
    }

    /**
     * Demande les permissions à l'utilisateur.
     */
    @PluginMethod
    override fun requestPermissions(call: PluginCall) {
        // Si les permissions sont déjà accordées, la promesse est résolue immédiatement
        val readState = getPermissionState("readSms")
        val receiveState = getPermissionState("receiveSms")
        val phoneState = getPermissionState("phone")
        val sendState = getPermissionState("sendSms")

        if (readState == PermissionState.GRANTED && 
            receiveState == PermissionState.GRANTED && 
            phoneState == PermissionState.GRANTED &&
            sendState == PermissionState.GRANTED) {
            
            val result = JSObject().apply {
                put("readSms", "granted")
                put("receiveSms", "granted")
                put("phone", "granted")
                put("sendSms", "granted")
            }
            call.resolve(result)
            return
        }

        // Sinon, demande des permissions via le framework Capacitor
        requestPermissionForAliases(arrayOf("readSms", "receiveSms", "phone", "sendSms"), call, "permissionsCallback")
    }

    @PermissionCallback
    private fun permissionsCallback(call: PluginCall) {
        val readState = getPermissionState("readSms")
        val receiveState = getPermissionState("receiveSms")
        val phoneState = getPermissionState("phone")
        val sendState = getPermissionState("sendSms")
        
        val result = JSObject().apply {
            put("readSms", readState?.toString() ?: "prompt")
            put("receiveSms", receiveState?.toString() ?: "prompt")
            put("phone", phoneState?.toString() ?: "prompt")
            put("sendSms", sendState?.toString() ?: "prompt")
        }
        call.resolve(result)
    }

    // ─── Application SMS par défaut ──────────────────────────────────────────

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
     * Demande à définir cette app comme l'app SMS par défaut.
     */
    @PluginMethod
    fun requestDefaultSmsApp(call: PluginCall) {
        val isDefault = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        if (isDefault) {
            Logger.info(TAG, "requestDefaultSmsApp: already default app, resolve")
            call.resolve(JSObject().put("isDefault", true))
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ : utilisation de RoleManager (recommandé et requis)
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as android.app.role.RoleManager
            if (roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_SMS)) {
                val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS)
                startActivityForResult(call, intent, "defaultSmsAppCallback")
            } else {
                call.reject("ROLE_UNAVAILABLE", "SMS role is not available on this device")
            }
        } else {
            // Android 6-9 : utilisation de l'ancien Intent Action
            val intent = android.content.Intent(ACTION_CHANGE_DEFAULT_SMS_APP).apply {
                putExtra(EXTRA_PACKAGE_NAME, context.packageName)
            }
            startActivityForResult(call, intent, "defaultSmsAppCallback")
        }
    }

    @ActivityCallback
    private fun defaultSmsAppCallback(call: PluginCall, result: androidx.activity.result.ActivityResult) {
        val isDefault = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        Logger.info(TAG, "defaultSmsAppCallback: isDefault=$isDefault")
        call.resolve(JSObject().put("isDefault", isDefault))
    }

    // ─── Lecture de l'historique SMS ─────────────────────────────────────────

    /**
     * Récupère la liste des SMS selon les filtres et la projection fournis.
     */
    @PluginMethod
    fun getSmsList(call: PluginCall) {
        if (getPermissionState("readSms") != PermissionState.GRANTED) {
            call.reject("PERMISSION_DENIED", "READ_SMS permission is required to read SMS history")
            return
        }

        try {
            val filter = call.getObject("filter")
            val projection = call.getObject("projection")

            val messages = smsReader.getSmsList(filter, projection)
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

    /**
     * Retourne uniquement le nombre de SMS correspondant aux filtres.
     */
    @PluginMethod
    fun getCount(call: PluginCall) {
        if (getPermissionState("readSms") != PermissionState.GRANTED) {
            call.reject("PERMISSION_DENIED", "READ_SMS permission is required to count SMS")
            return
        }

        try {
            val filter = call.getObject("filter")
            val count = smsReader.getCount(filter)
            call.resolve(JSObject().put("count", count))
        } catch (e: Exception) {
            Logger.error(TAG, "getCount failed", e)
            call.reject("COUNT_FAILED", "Failed to count SMS: ${e.message}", e)
        }
    }

    /**
     * Retourne toutes les colonnes brutes de la table SMS Android sous forme de chaîne.
     */
    @PluginMethod
    fun getRawSmsList(call: PluginCall) {
        if (getPermissionState("readSms") != PermissionState.GRANTED) {
            call.reject("PERMISSION_DENIED", "READ_SMS permission is required to read raw SMS history")
            return
        }

        try {
            val filter = call.getObject("filter")
            val messages = smsReader.getRawSmsList(filter)
            call.resolve(JSObject().put("messages", messages))
        } catch (e: Exception) {
            Logger.error(TAG, "getRawSmsList failed", e)
            call.reject("READ_RAW_FAILED", "Failed to read raw SMS: ${e.message}", e)
        }
    }

    // ─── Interception des SMS en temps réel ──────────────────────────────────

    /**
     * Démarre l'écoute des SMS entrants (BroadcastReceiver, ContentObserver ou les deux).
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

        val strategy = call.getString("strategy") ?: "broadcast"
        Logger.info(TAG, "startListening invoked with strategy: $strategy")

        try {
            recentSmsIds.clear()
            recentSmsFingerprints.clear()

            // 1. Enregistrement du BroadcastReceiver
            if (strategy == "broadcast" || strategy == "both") {
                val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
                    priority = IntentFilter.SYSTEM_HIGH_PRIORITY
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(smsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    context.registerReceiver(smsReceiver, filter)
                }
            }

            // 2. Enregistrement du ContentObserver
            if (strategy == "observer" || strategy == "both") {
                val observer = SmsContentObserver(Handler(Looper.getMainLooper()), context) {
                    handleSmsDbChange()
                }
                context.contentResolver.registerContentObserver(
                    Telephony.Sms.CONTENT_URI,
                    true,
                    observer
                )
                smsContentObserver = observer
            }

            isListening = true
            Logger.info(TAG, "SMS listening started with strategy: $strategy")
            call.resolve()
        } catch (e: Exception) {
            Logger.error(TAG, "startListening failed", e)
            call.reject("START_FAILED", "Failed to start SMS listener: ${e.message}", e)
        }
    }

    /**
     * Arrête l'écoute des SMS entrants.
     */
    @PluginMethod
    fun stopListening(call: PluginCall) {
        if (!isListening) {
            Logger.info(TAG, "stopListening: not currently listening, no-op")
            call.resolve()
            return
        }

        try {
            // Désenregistrement du BroadcastReceiver
            try {
                context.unregisterReceiver(smsReceiver)
            } catch (e: Exception) {
                // Ignore
            }

            // Désenregistrement du ContentObserver
            smsContentObserver?.let { observer ->
                try {
                    context.contentResolver.unregisterContentObserver(observer)
                } catch (e: Exception) {
                    // Ignore
                }
                smsContentObserver = null
            }

            isListening = false
            recentSmsIds.clear()
            recentSmsFingerprints.clear()
            Logger.info(TAG, "SMS listening stopped")
        } catch (e: Exception) {
            Logger.warn(TAG, "stopListening failed — ${e.message}")
            isListening = false
        }
        call.resolve()
    }

    // ─── SMS Retriever (OTP) ─────────────────────────────────────────────────

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

    // ─── Détection SIM / Numéro de téléphone ─────────────────────────────────

    /**
     * Récupère la liste des cartes SIM actives et de leurs numéros associés.
     */
    @Suppress("DEPRECATION")
    @PluginMethod
    fun getSimInfo(call: PluginCall) {
        if (getPermissionState("phone") != PermissionState.GRANTED) {
            call.reject("PERMISSION_DENIED", "READ_PHONE_STATE / READ_PHONE_NUMBERS permissions are required to access SIM info")
            return
        }

        try {
            val simCardsList = JSArray()
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

            val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList
            if (activeSubscriptions != null) {
                for (subInfo in activeSubscriptions) {
                    val simInfo = JSObject()
                    simInfo.put("carrierName", subInfo.carrierName?.toString() ?: "")
                    simInfo.put("countryIso", subInfo.countryIso?.toString() ?: "")
                    simInfo.put("simSlotIndex", subInfo.simSlotIndex)
                    simInfo.put("subscriptionId", subInfo.subscriptionId)

                    val mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        subInfo.mccString ?: ""
                    } else {
                        @Suppress("DEPRECATION")
                        if (subInfo.mcc != 0) subInfo.mcc.toString() else ""
                    }
                    simInfo.put("mcc", mcc)

                    val mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        subInfo.mncString ?: ""
                    } else {
                        @Suppress("DEPRECATION")
                        if (subInfo.mnc != 0) subInfo.mnc.toString() else ""
                    }
                    simInfo.put("mnc", mnc)

                    val number = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        try {
                            subscriptionManager.getPhoneNumber(subInfo.subscriptionId) ?: ""
                        } catch (e: SecurityException) {
                            subInfo.number ?: ""
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        subInfo.number ?: ""
                    }
                    simInfo.put("phoneNumber", number)

                    simCardsList.put(simInfo)
                }
            }

            val response = JSObject()
            response.put("simCards", simCardsList)
            call.resolve(response)
        } catch (e: Exception) {
            Logger.error(TAG, "getSimInfo failed", e)
            call.reject("SIM_INFO_FAILED", "Failed to get SIM info: ${e.message}", e)
        }
    }

    /**
     * Récupère le profil SIM détaillé contenant le statut dualSim et la liste des cartes actives.
     */
    @PluginMethod
    fun getDeviceSimProfile(call: PluginCall) {
        if (getPermissionState("phone") != PermissionState.GRANTED) {
            call.reject("PERMISSION_DENIED", "READ_PHONE_STATE / READ_PHONE_NUMBERS permissions are required to access SIM profile")
            return
        }

        try {
            val profile = buildSimProfile()
            call.resolve(profile)
        } catch (e: Exception) {
            Logger.error(TAG, "getDeviceSimProfile failed", e)
            call.reject("SIM_PROFILE_FAILED", "Failed to get SIM profile: ${e.message}", e)
        }
    }

    /**
     * Démarre la surveillance des changements de cartes SIM sur le périphérique.
     */
    @PluginMethod
    fun startMonitoringSimChanges(call: PluginCall) {
        if (getPermissionState("phone") != PermissionState.GRANTED) {
            call.reject("PERMISSION_DENIED", "READ_PHONE_STATE / READ_PHONE_NUMBERS permissions are required to monitor SIM changes")
            return
        }

        if (simSubscriptionsListener != null) {
            Logger.info(TAG, "SIM monitoring already started")
            call.resolve()
            return
        }

        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
                override fun onSubscriptionsChanged() {
                    Logger.info(TAG, "SIM subscriptions changed detected")
                    val profile = buildSimProfile()
                    notifyListeners("simsChanged", profile)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                subscriptionManager.addOnSubscriptionsChangedListener(context.mainExecutor, listener)
            } else {
                @Suppress("DEPRECATION")
                subscriptionManager.addOnSubscriptionsChangedListener(listener)
            }

            simSubscriptionsListener = listener
            Logger.info(TAG, "SIM monitoring started")
            call.resolve()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to start monitoring SIM changes", e)
            call.reject("MONITOR_START_FAILED", "Failed to start SIM monitoring: ${e.message}", e)
        }
    }

    /**
     * Arrête la surveillance des changements de cartes SIM sur le périphérique.
     */
    @PluginMethod
    fun stopMonitoringSimChanges(call: PluginCall) {
        val listener = simSubscriptionsListener
        if (listener == null) {
            Logger.info(TAG, "SIM monitoring was not active")
            call.resolve()
            return
        }

        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            subscriptionManager.removeOnSubscriptionsChangedListener(listener)
            simSubscriptionsListener = null
            Logger.info(TAG, "SIM monitoring stopped")
            call.resolve()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to stop monitoring SIM changes", e)
            call.reject("MONITOR_STOP_FAILED", "Failed to stop SIM monitoring: ${e.message}", e)
        }
    }

    /**
     * Helper pour construire le profil SIM complet.
     */
    @Suppress("DEPRECATION")
    private fun buildSimProfile(): JSObject {
        val simProfile = JSObject()
        val simsList = JSArray()
        var dualSim = false

        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList
            if (activeSubscriptions != null) {
                dualSim = activeSubscriptions.size > 1
                for (subInfo in activeSubscriptions) {
                    val simInfo = JSObject().apply {
                        put("carrierName", subInfo.carrierName?.toString() ?: "")
                        put("countryIso", subInfo.countryIso?.toString() ?: "")
                        put("simSlotIndex", subInfo.simSlotIndex)
                        put("subscriptionId", subInfo.subscriptionId)

                        val mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            subInfo.mccString ?: ""
                        } else {
                            @Suppress("DEPRECATION")
                            if (subInfo.mcc != 0) subInfo.mcc.toString() else ""
                        }
                        put("mcc", mcc)

                        val mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            subInfo.mncString ?: ""
                        } else {
                            @Suppress("DEPRECATION")
                            if (subInfo.mnc != 0) subInfo.mnc.toString() else ""
                        }
                        put("mnc", mnc)

                        val number = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            try {
                                subscriptionManager.getPhoneNumber(subInfo.subscriptionId) ?: ""
                            } catch (e: SecurityException) {
                                subInfo.number ?: ""
                            }
                        } else {
                            subInfo.number ?: ""
                        }
                        put("phoneNumber", number)
                    }
                    simsList.put(simInfo)
                }
            }
        } catch (e: SecurityException) {
            Logger.error(TAG, "SecurityException building SIM profile", e)
        } catch (e: Exception) {
            Logger.error(TAG, "Error building SIM profile", e)
        }

        simProfile.put("dualSim", dualSim)
        simProfile.put("sims", simsList)
        return simProfile
    }

    /**
     * Démarre l'écoute d'un message OTP via l'API SMS User Consent de Google.
     */
    @PluginMethod
    fun startSmsUserConsent(call: PluginCall) {
        cleanupUserConsent()
        userConsentCall = call

        val senderAddress = call.getString("senderAddress")

        try {
            val client = SmsRetriever.getClient(context)
            val task = client.startSmsUserConsent(senderAddress)

            task.addOnSuccessListener {
                Logger.info(TAG, "SMS User Consent client started successfully")
                registerUserConsentReceiver()
            }
            task.addOnFailureListener { e ->
                Logger.error(TAG, "Failed to start SMS User Consent client", e)
                call.reject("USER_CONSENT_START_FAILED", "Failed to start SMS User Consent client: ${e.message}", e)
                userConsentCall = null
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to initialize SMS User Consent", e)
            call.reject("USER_CONSENT_INIT_FAILED", "Failed to initialize SMS User Consent: ${e.message}", e)
            userConsentCall = null
        }
    }

    /**
     * Arrête l'écoute du consentement utilisateur.
     */
    @PluginMethod
    fun stopSmsUserConsent(call: PluginCall) {
        cleanupUserConsent()
        Logger.info(TAG, "SMS User Consent stopped manually")
        call.resolve()
    }

    /**
     * Enregistre le BroadcastReceiver pour intercepter le signal de consentement.
     */
    private fun registerUserConsentReceiver() {
        val filter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == SmsRetriever.SMS_RETRIEVED_ACTION) {
                    val extras = intent.extras
                    val status = extras?.let {
                        androidx.core.os.BundleCompat.getParcelable(it, SmsRetriever.EXTRA_STATUS, Status::class.java)
                    }

                    when (status?.statusCode) {
                        CommonStatusCodes.SUCCESS -> {
                            val consentIntent = extras?.let {
                                androidx.core.os.BundleCompat.getParcelable(it, SmsRetriever.EXTRA_CONSENT_INTENT, Intent::class.java)
                            }
                            val call = userConsentCall
                            if (consentIntent != null && call != null) {
                                startActivityForResult(call, consentIntent, "userConsentCallback")
                            } else {
                                userConsentCall?.reject("CONSENT_INTENT_NULL", "Consent intent is null or plugin call was lost")
                                cleanupUserConsent()
                            }
                        }
                        CommonStatusCodes.TIMEOUT -> {
                            Logger.info(TAG, "SMS User Consent timed out")
                            userConsentCall?.reject("TIMEOUT", "SMS User Consent timed out after 5 minutes")
                            cleanupUserConsent()
                        }
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        userConsentReceiver = receiver
    }

    /**
     * Callback déclenché après la décision de l'utilisateur sur la boîte de dialogue de consentement.
     */
    @ActivityCallback
    private fun userConsentCallback(call: PluginCall, result: androidx.activity.result.ActivityResult) {
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val message = result.data?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)
            if (message != null) {
                val data = JSObject().apply {
                    put("message", message)
                }
                notifyListeners("smsUserConsentReceived", data)
                call.resolve()
            } else {
                call.reject("NO_MESSAGE", "No SMS message received in user consent result")
            }
        } else {
            call.reject("CONSENT_DENIED", "User denied consent to read SMS")
        }
        cleanupUserConsent()
    }

    // ─── Classes internes utilitaires ────────────────────────────────────────

    /**
     * Implémentation du ContentObserver pour content://sms.
     */
    private inner class SmsContentObserver(
        handler: Handler,
        private val context: Context,
        private val onChangeCallback: () -> Unit
    ) : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            onChangeCallback()
        }
    }

    /**
     * Structure de cache ordonnée avec une taille limite.
     */
    private class BoundedSet<T>(private val maxSize: Int) {
        private val set = LinkedHashSet<T>()

        @Synchronized
        fun add(element: T): Boolean {
            if (set.contains(element)) return false
            if (set.size >= maxSize) {
                val iterator = set.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
            return set.add(element)
        }

        @Synchronized
        fun contains(element: T): Boolean = set.contains(element)

        @Synchronized
        fun clear() = set.clear()
    }

    @PluginMethod
    fun markAsRead(call: PluginCall) {
        val id = call.getString("id")
        if (id == null) {
            call.reject("MISSING_PARAMETER", "Parameter 'id' is required")
            return
        }

        try {
            val uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id)
            val values = android.content.ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
            val rowsUpdated = context.contentResolver.update(uri, values, null, null)
            if (rowsUpdated > 0) {
                call.resolve()
            } else {
                call.reject("UPDATE_FAILED", "No SMS found with ID $id or permission denied (must be default SMS app on Android 10+)")
            }
        } catch (e: Exception) {
            Logger.error(TAG, "markAsRead failed", e)
            call.reject("WRITE_FAILED", "Failed to mark SMS as read: ${e.message}", e)
        }
    }

    @PluginMethod
    fun markAsSeen(call: PluginCall) {
        val id = call.getString("id")
        if (id == null) {
            call.reject("MISSING_PARAMETER", "Parameter 'id' is required")
            return
        }

        try {
            val uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id)
            val values = android.content.ContentValues().apply {
                put(Telephony.Sms.SEEN, 1)
            }
            val rowsUpdated = context.contentResolver.update(uri, values, null, null)
            if (rowsUpdated > 0) {
                call.resolve()
            } else {
                call.reject("UPDATE_FAILED", "No SMS found with ID $id or permission denied (must be default SMS app on Android 10+)")
            }
        } catch (e: Exception) {
            Logger.error(TAG, "markAsSeen failed", e)
            call.reject("WRITE_FAILED", "Failed to mark SMS as seen: ${e.message}", e)
        }
    }

    @PluginMethod
    fun setSmsLocked(call: PluginCall) {
        val id = call.getString("id")
        val locked = call.getBoolean("locked")
        if (id == null || locked == null) {
            call.reject("MISSING_PARAMETER", "Parameters 'id' and 'locked' are required")
            return
        }

        try {
            val uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id)
            val values = android.content.ContentValues().apply {
                put(Telephony.Sms.LOCKED, if (locked) 1 else 0)
            }
            val rowsUpdated = context.contentResolver.update(uri, values, null, null)
            if (rowsUpdated > 0) {
                call.resolve()
            } else {
                call.reject("UPDATE_FAILED", "No SMS found with ID $id or permission denied (must be default SMS app on Android 10+)")
            }
        } catch (e: Exception) {
            Logger.error(TAG, "setSmsLocked failed", e)
            call.reject("WRITE_FAILED", "Failed to update SMS locked state: ${e.message}", e)
        }
    }

    @PluginMethod
    fun deleteSms(call: PluginCall) {
        val id = call.getString("id")
        if (id == null) {
            call.reject("MISSING_PARAMETER", "Parameter 'id' is required")
            return
        }

        val uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id)
        var isLocked = false
        var exists = false
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, arrayOf(Telephony.Sms.LOCKED), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                exists = true
                isLocked = cursor.getInt(0) == 1
            }
        } catch (e: Exception) {
            // En cas d'erreur de lecture, on considère que le SMS n'est pas verrouillé
            // et on tente la suppression directe (au mieux / best-effort).
        } finally {
            cursor?.close()
        }

        if (!exists) {
            val result = JSObject().apply {
                put("deleted", false)
                put("reason", "not_found")
            }
            call.resolve(result)
            return
        }

        val force = call.getBoolean("force") ?: false
        if (isLocked && !force) {
            val result = JSObject().apply {
                put("deleted", false)
                put("reason", "locked")
            }
            call.resolve(result)
            return
        }

        if (isLocked && force) {
            try {
                val values = android.content.ContentValues().apply {
                    put(Telephony.Sms.LOCKED, 0)
                }
                context.contentResolver.update(uri, values, null, null)
            } catch (e: Exception) {
                call.reject("DELETE_FAILED", "Failed to unlock SMS before deletion: ${e.message}", e)
                return
            }
        }

        try {
            val rowsDeleted = context.contentResolver.delete(uri, null, null)
            if (rowsDeleted > 0) {
                val result = JSObject().apply {
                    put("deleted", true)
                }
                call.resolve(result)
            } else {
                call.reject("DELETE_FAILED", "No SMS found with ID $id or permission denied (must be default SMS app on Android 10+)")
            }
        } catch (e: Exception) {
            Logger.error(TAG, "deleteSms failed", e)
            call.reject("DELETE_FAILED", "Failed to delete SMS: ${e.message}", e)
        }
    }

    @PluginMethod
    fun sendSms(call: PluginCall) {
        val phoneNumber = call.getString("phoneNumber")
        val message = call.getString("message")
        val mode = call.getString("mode") ?: "indirect"

        if (phoneNumber.isNullOrEmpty() || message.isNullOrEmpty()) {
            call.reject("MISSING_PARAMETER", "Parameters 'phoneNumber' and 'message' are required")
            return
        }

        if (mode == "direct") {
            if (getPermissionState("sendSms") != PermissionState.GRANTED) {
                call.reject("PERMISSION_DENIED", "SEND_SMS permission is required to send SMS directly")
                return
            }

            try {
                val smsManager: android.telephony.SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    context.getSystemService(android.telephony.SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    android.telephony.SmsManager.getDefault()
                }

                val parts = smsManager.divideMessage(message)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                }
                call.resolve()
            } catch (e: Exception) {
                Logger.error(TAG, "sendSms direct failed", e)
                call.reject("SEND_FAILED", "Failed to send SMS directly: ${e.message}", e)
            }
        } else {
            // Mode indirect : lancement de l'Intent système.
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$phoneNumber")
                    putExtra("sms_body", message)
                }
                activity?.startActivity(intent) ?: context.startActivity(intent.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                call.resolve()
            } catch (e: Exception) {
                Logger.error(TAG, "sendSms indirect failed", e)
                call.reject("SEND_FAILED", "Failed to open default SMS app: ${e.message}", e)
            }
        }
    }

    private fun handleSmsDbChange() {
        val now = System.currentTimeMillis()
        val tenSecondsAgo = now - 10000

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.SEEN,
                    Telephony.Sms.STATUS,
                    Telephony.Sms.ERROR_CODE,
                    Telephony.Sms.LOCKED,
                    Telephony.Sms.SERVICE_CENTER,
                    "sub_id"
                ),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(tenSecondsAgo.toString()),
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.let { c ->
                val idIndex = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIndex = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIndex = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)

                val seenIdx = c.getColumnIndex(Telephony.Sms.SEEN)
                val statusIdx = c.getColumnIndex(Telephony.Sms.STATUS)
                val errorCodeIdx = c.getColumnIndex(Telephony.Sms.ERROR_CODE)
                val lockedIdx = c.getColumnIndex(Telephony.Sms.LOCKED)
                val serviceCenterIdx = c.getColumnIndex(Telephony.Sms.SERVICE_CENTER)
                val subIdIndex = c.getColumnIndex("sub_id")

                while (c.moveToNext()) {
                    val idVal = c.getString(idIndex) ?: ""
                    val addressVal = c.getString(addressIndex) ?: ""
                    val bodyVal = c.getString(bodyIndex) ?: ""
                    val dateVal = c.getLong(dateIndex)
                    val typeVal = c.getInt(typeIndex)

                    val fingerprint = "$addressVal|$bodyVal"

                    // Déduplication des messages
                    if (recentSmsIds.contains(idVal)) continue
                    if (recentSmsFingerprints.contains(fingerprint)) {
                        // Déjà traité par le BroadcastReceiver, on retient l'identifiant.
                        recentSmsIds.add(idVal)
                        continue
                    }

                    recentSmsIds.add(idVal)
                    recentSmsFingerprints.add(fingerprint)

                    val smsObj = JSObject().apply {
                        put("id", idVal)
                        put("address", addressVal)
                        put("body", bodyVal)
                        put("date", dateVal)
                        put("type", typeVal)

                        if (seenIdx != -1 && !c.isNull(seenIdx)) {
                            put("seen", c.getInt(seenIdx) == 1)
                        } else {
                            put("seen", false)
                        }

                        if (statusIdx != -1 && !c.isNull(statusIdx)) {
                            put("status", c.getInt(statusIdx))
                        } else {
                            put("status", -1) // SmsDeliveryStatus.NONE
                        }

                        if (errorCodeIdx != -1 && !c.isNull(errorCodeIdx)) {
                            put("errorCode", c.getInt(errorCodeIdx))
                        } else {
                            put("errorCode", 0)
                        }

                        if (lockedIdx != -1 && !c.isNull(lockedIdx)) {
                            put("locked", c.getInt(lockedIdx) == 1)
                        } else {
                            put("locked", false)
                        }

                        if (serviceCenterIdx != -1 && !c.isNull(serviceCenterIdx)) {
                            put("serviceCenter", c.getString(serviceCenterIdx) ?: "")
                        } else {
                            put("serviceCenter", "")
                        }

                        if (subIdIndex != -1 && !c.isNull(subIdIndex)) {
                            put("subscriptionId", c.getInt(subIdIndex))
                        }
                    }

                    notifySmsReceived(smsObj)
                    Logger.info(TAG, "New SMS detected via ContentObserver, ID: $idVal")
                }
            }
        } catch (e: Exception) {
            Logger.error(TAG, "handleSmsDbChange failed", e)
        } finally {
            cursor?.close()
        }
    }
}
