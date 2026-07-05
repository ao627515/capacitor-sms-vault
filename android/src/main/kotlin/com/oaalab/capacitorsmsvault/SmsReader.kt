package com.oaalab.capacitorsmsvault

import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Logger

/**
 * Encapsule la lecture de l'historique SMS via ContentResolver.
 * Requiert la permission READ_SMS et que l'app soit l'app SMS par défaut (Android 10+).
 */
class SmsReader(private val context: Context) {

    companion object {
        private const val TAG = "SmsReader"
        private const val DEFAULT_LIMIT = 100
    }

    /**
     * Lit les SMS de l'inbox avec filtres optionnels.
     *
     * @param address Filtrer par numéro d'expéditeur (LIKE — partiel)
     * @param since   Timestamp ms minimum (date >=)
     * @param limit   Nombre max de résultats (défaut : 100)
     * @return JSArray de JSObject représentant chaque SmsMessage
     */
    fun getSmsList(address: String?, since: Long?, limit: Int?): JSArray {
        val result = JSArray()
        val effectiveLimit = (limit ?: DEFAULT_LIMIT).coerceAtLeast(1)

        val selection = buildSelection(address, since)
        val selectionArgs = buildSelectionArgs(address, since)

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC LIMIT $effectiveLimit"
            )

            cursor?.let { c ->
                val idIndex = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIndex = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIndex = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)

                while (c.moveToNext()) {
                    val smsObj = JSObject().apply {
                        put("id", c.getString(idIndex) ?: "")
                        put("address", c.getString(addressIndex) ?: "")
                        put("body", c.getString(bodyIndex) ?: "")
                        put("date", c.getLong(dateIndex))
                        put("type", mapSmsType(c.getInt(typeIndex)))
                    }
                    result.put(smsObj)
                }
                Logger.info(TAG, "Read ${result.length()} SMS from inbox")
            }
        } catch (e: SecurityException) {
            // Permission manquante — remonter l'exception pour que le plugin la gère
            Logger.error(TAG, "SecurityException reading SMS — permission denied", e)
            throw e
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to read SMS list", e)
        } finally {
            cursor?.close()
        }

        return result
    }

    /**
     * Construit la clause WHERE SQL à partir des filtres fournis.
     */
    private fun buildSelection(address: String?, since: Long?): String? {
        val conditions = mutableListOf<String>()
        if (address != null) conditions.add("${Telephony.Sms.ADDRESS} LIKE ?")
        if (since != null) conditions.add("${Telephony.Sms.DATE} >= ?")
        return if (conditions.isEmpty()) null else conditions.joinToString(" AND ")
    }

    /**
     * Construit les arguments de la clause WHERE.
     */
    private fun buildSelectionArgs(address: String?, since: Long?): Array<String>? {
        val args = mutableListOf<String>()
        if (address != null) args.add("%$address%")
        if (since != null) args.add(since.toString())
        return if (args.isEmpty()) null else args.toTypedArray()
    }

    /**
     * Convertit le type entier Android en string lisible par le plugin TS.
     */
    private fun mapSmsType(type: Int): String {
        return when (type) {
            Telephony.Sms.MESSAGE_TYPE_SENT -> "sent"
            else -> "inbox"
        }
    }
}
