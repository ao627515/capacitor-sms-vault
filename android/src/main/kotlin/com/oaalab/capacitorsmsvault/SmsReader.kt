package com.oaalab.capacitorsmsvault

import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.Telephony
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Logger

/**
 * Prend en charge la projection, le filtrage SQL natif et le filtrage par expressions régulières en mémoire.
 */
class SmsReader(private val context: Context) {

    companion object {
        private const val TAG = "SmsReader"
        private const val DEFAULT_LIMIT = 100
    }

    /**
     * Lit la liste des SMS selon le filtre et la projection demandés.
     */
    fun getSmsList(filterObj: JSObject?, projectionObj: JSObject?): JSArray {
        val result = JSArray()

        // 1. Détermine la projection SQL (colonnes à interroger)
        val projectionList = mutableListOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            "sub_id"
        )

        val hasThreadId = projectionObj?.optBoolean("threadId") == true
        if (hasThreadId) projectionList.add(Telephony.Sms.THREAD_ID)

        val hasSubject = projectionObj?.optBoolean("subject") == true
        if (hasSubject) projectionList.add(Telephony.Sms.SUBJECT)

        val hasDateSent = projectionObj?.optBoolean("dateSent") == true
        if (hasDateSent) projectionList.add(Telephony.Sms.DATE_SENT)

        val hasCreator = projectionObj?.optBoolean("creator") == true
        if (hasCreator && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            projectionList.add(Telephony.Sms.CREATOR)
        }

        val hasPerson = projectionObj?.optBoolean("person") == true
        if (hasPerson) projectionList.add(Telephony.Sms.PERSON)

        val hasSeen = projectionObj?.optBoolean("seen") == true
        if (hasSeen) projectionList.add(Telephony.Sms.SEEN)

        val hasStatus = projectionObj?.optBoolean("status") == true
        if (hasStatus) projectionList.add(Telephony.Sms.STATUS)

        val hasErrorCode = projectionObj?.optBoolean("errorCode") == true
        if (hasErrorCode) projectionList.add(Telephony.Sms.ERROR_CODE)

        val hasLocked = projectionObj?.optBoolean("locked") == true
        if (hasLocked) projectionList.add(Telephony.Sms.LOCKED)

        val hasServiceCenter = projectionObj?.optBoolean("serviceCenter") == true
        if (hasServiceCenter) projectionList.add(Telephony.Sms.SERVICE_CENTER)

        // 2. Construit la clause WHERE
        val selection = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()
        buildSelectionAndArgs(filterObj, selection, selectionArgs)

        val finalSelection = if (selection.isEmpty()) null else selection.joinToString(" AND ")
        val finalSelectionArgs = if (selectionArgs.isEmpty()) null else selectionArgs.toTypedArray()

        // 3. Détermine l'ordre de tri (date ASC ou DESC)
        val orderByStr = filterObj?.getString("orderBy")
        val sortOrder = when (orderByStr) {
            "date_asc"  -> "${Telephony.Sms.DATE} ASC"
            "date_desc" -> "${Telephony.Sms.DATE} DESC"
            else        -> "${Telephony.Sms.DATE} DESC"
        }

        // 4. Prépare les expressions régulières (pour le filtrage ultérieur en Kotlin)
        val addressRegexStr = filterObj?.getString("addressRegex")
        val addressRegex = addressRegexStr?.toRegex(RegexOption.IGNORE_CASE)

        val bodyRegexStr = filterObj?.getString("bodyRegex")
        val bodyRegex = bodyRegexStr?.toRegex(RegexOption.IGNORE_CASE)

        // Pagination
        val indexFrom = filterObj?.getInteger("indexFrom") ?: 0
        val maxCount = filterObj?.getInteger("maxCount") ?: DEFAULT_LIMIT

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projectionList.toTypedArray(),
                finalSelection,
                finalSelectionArgs,
                sortOrder
            )

            cursor?.let { c ->
                val idIndex = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIndex = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIndex = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val subIdIndex = c.getColumnIndex("sub_id")

                val threadIdIdx = if (hasThreadId) c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID) else -1
                val subjectIdx = if (hasSubject) c.getColumnIndexOrThrow(Telephony.Sms.SUBJECT) else -1
                val dateSentIdx = if (hasDateSent) c.getColumnIndexOrThrow(Telephony.Sms.DATE_SENT) else -1
                val creatorIdx = if (hasCreator && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    c.getColumnIndexOrThrow(Telephony.Sms.CREATOR)
                } else -1
                val personIdx = if (hasPerson) c.getColumnIndexOrThrow(Telephony.Sms.PERSON) else -1
                val seenIdx = if (hasSeen) c.getColumnIndexOrThrow(Telephony.Sms.SEEN) else -1
                val statusIdx = if (hasStatus) c.getColumnIndexOrThrow(Telephony.Sms.STATUS) else -1
                val errorCodeIdx = if (hasErrorCode) c.getColumnIndexOrThrow(Telephony.Sms.ERROR_CODE) else -1
                val lockedIdx = if (hasLocked) c.getColumnIndexOrThrow(Telephony.Sms.LOCKED) else -1
                val serviceCenterIdx = if (hasServiceCenter) c.getColumnIndexOrThrow(Telephony.Sms.SERVICE_CENTER) else -1

                var matchCount = 0
                var addedCount = 0

                while (c.moveToNext()) {
                    val addressVal = c.getString(addressIndex) ?: ""
                    val bodyVal = c.getString(bodyIndex) ?: ""

                    // Filtrage par expressions régulières
                    if (addressRegex != null && !addressRegex.containsMatchIn(addressVal)) {
                        continue
                    }
                    if (bodyRegex != null && !bodyRegex.containsMatchIn(bodyVal)) {
                        continue
                    }

                    // Pagination
                    if (matchCount >= indexFrom) {
                        if (addedCount < maxCount) {
                            val smsObj = JSObject().apply {
                                put("id", c.getString(idIndex) ?: "")
                                put("address", addressVal)
                                put("body", bodyVal)
                                put("date", c.getLong(dateIndex))
                                put("type", c.getInt(typeIndex))

                                if (subIdIndex != -1 && !c.isNull(subIdIndex)) {
                                    put("subscriptionId", c.getInt(subIdIndex))
                                }

                                if (hasThreadId && threadIdIdx != -1) put("threadId", c.getString(threadIdIdx) ?: "")
                                if (hasSubject && subjectIdx != -1) put("subject", c.getString(subjectIdx) ?: "")
                                if (hasDateSent && dateSentIdx != -1) put("dateSent", c.getLong(dateSentIdx))
                                if (hasCreator && creatorIdx != -1) put("creator", c.getString(creatorIdx) ?: "")
                                if (hasPerson && personIdx != -1) put("person", c.getString(personIdx) ?: "")
                                if (hasSeen && seenIdx != -1) put("seen", c.getInt(seenIdx) == 1)
                                if (hasStatus && statusIdx != -1) put("status", c.getInt(statusIdx))
                                if (hasErrorCode && errorCodeIdx != -1) put("errorCode", c.getInt(errorCodeIdx))
                                if (hasLocked && lockedIdx != -1) put("locked", c.getInt(lockedIdx) == 1)
                                if (hasServiceCenter && serviceCenterIdx != -1) put("serviceCenter", c.getString(serviceCenterIdx) ?: "")
                            }
                            result.put(smsObj)
                            addedCount++
                        } else {
                            break // Nombre maximum de messages (maxCount) atteint
                        }
                    }
                    matchCount++
                }
                Logger.info(TAG, "Read ${result.length()} SMS (total match count: $matchCount)")
            }
        } catch (e: SecurityException) {
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
     * Retourne uniquement le nombre de SMS correspondant aux filtres,
     * optimisé pour éviter le chargement de données inutiles.
     */
    fun getCount(filterObj: JSObject?): Int {
        val selection = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()
        buildSelectionAndArgs(filterObj, selection, selectionArgs)

        val finalSelection = if (selection.isEmpty()) null else selection.joinToString(" AND ")
        val finalSelectionArgs = if (selectionArgs.isEmpty()) null else selectionArgs.toTypedArray()

        val addressRegexStr = filterObj?.getString("addressRegex")
        val bodyRegexStr = filterObj?.getString("bodyRegex")

        if (addressRegexStr == null && bodyRegexStr == null) {
            // Pas d'expression régulière : exécution d'un COUNT en SQL direct (très performant)
            var count = 0
            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf("COUNT(*)"),
                    finalSelection,
                    finalSelectionArgs,
                    null
                )
                if (cursor != null && cursor.moveToFirst()) {
                    count = cursor.getInt(0)
                }
            } catch (e: Exception) {
                Logger.error(TAG, "getCount SQL failed", e)
            } finally {
                cursor?.close()
            }
            return count
        } else {
            // Avec expression régulière : test et filtrage effectués en mémoire
            val addressRegex = addressRegexStr?.toRegex(RegexOption.IGNORE_CASE)
            val bodyRegex = bodyRegexStr?.toRegex(RegexOption.IGNORE_CASE)

            var count = 0
            val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY)
            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    projection,
                    finalSelection,
                    finalSelectionArgs,
                    null
                )
                cursor?.let { c ->
                    val addressIndex = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                    val bodyIndex = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    while (c.moveToNext()) {
                        val addressVal = c.getString(addressIndex) ?: ""
                        val bodyVal = c.getString(bodyIndex) ?: ""
                        if (addressRegex != null && !addressRegex.containsMatchIn(addressVal)) continue
                        if (bodyRegex != null && !bodyRegex.containsMatchIn(bodyVal)) continue
                        count++
                    }
                }
            } catch (e: Exception) {
                Logger.error(TAG, "getCount Regex failed", e)
            } finally {
                cursor?.close()
            }
            return count
        }
    }

    /**
     * Retourne toutes les colonnes brutes de la table des SMS, sans aucun mapping.
     */
    fun getRawSmsList(filterObj: JSObject?): JSArray {
        val result = JSArray()

        val selection = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()
        buildSelectionAndArgs(filterObj, selection, selectionArgs)

        val finalSelection = if (selection.isEmpty()) null else selection.joinToString(" AND ")
        val finalSelectionArgs = if (selectionArgs.isEmpty()) null else selectionArgs.toTypedArray()

        val addressRegexStr = filterObj?.getString("addressRegex")
        val addressRegex = addressRegexStr?.toRegex(RegexOption.IGNORE_CASE)
        val bodyRegexStr = filterObj?.getString("bodyRegex")
        val bodyRegex = bodyRegexStr?.toRegex(RegexOption.IGNORE_CASE)

        val indexFrom = filterObj?.getInteger("indexFrom") ?: 0
        val maxCount = filterObj?.getInteger("maxCount") ?: DEFAULT_LIMIT

        val orderByStr = filterObj?.getString("orderBy")
        val sortOrder = when (orderByStr) {
            "date_asc"  -> "${Telephony.Sms.DATE} ASC"
            "date_desc" -> "${Telephony.Sms.DATE} DESC"
            else        -> "${Telephony.Sms.DATE} DESC"
        }

        var cursor: Cursor? = null
        try {
            // Passer null pour la projection retourne toutes les colonnes
            cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                null,
                finalSelection,
                finalSelectionArgs,
                sortOrder
            )

            cursor?.let { c ->
                val columnNames = c.columnNames
                val addressIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)

                var matchCount = 0
                var addedCount = 0

                while (c.moveToNext()) {
                    if (addressRegex != null && addressIdx != -1) {
                        val addressVal = c.getString(addressIdx) ?: ""
                        if (!addressRegex.containsMatchIn(addressVal)) continue
                    }
                    if (bodyRegex != null && bodyIdx != -1) {
                        val bodyVal = c.getString(bodyIdx) ?: ""
                        if (!bodyRegex.containsMatchIn(bodyVal)) continue
                    }

                    if (matchCount >= indexFrom) {
                        if (addedCount < maxCount) {
                            val smsObj = JSObject()
                            for (colName in columnNames) {
                                val colIdx = c.getColumnIndex(colName)
                                if (colIdx != -1) {
                                    val value = try {
                                        c.getString(colIdx) ?: ""
                                    } catch (e: Exception) {
                                        ""
                                    }
                                    smsObj.put(colName, value)
                                }
                            }
                            result.put(smsObj)
                            addedCount++
                        } else {
                            break
                        }
                    }
                    matchCount++
                }
            }
        } catch (e: Exception) {
            Logger.error(TAG, "getRawSmsList failed", e)
            throw e
        } finally {
            cursor?.close()
        }

        return result
    }

    /**
     * Construit les clauses de sélection SQL basées sur l'objet de filtrage.
     */
    private fun buildSelectionAndArgs(
        filterObj: JSObject?,
        selection: MutableList<String>,
        selectionArgs: MutableList<String>
    ) {
        if (filterObj != null) {
            // ─── Groupe A : Filtres cumulatifs (AND) ───
            val type = filterObj.getInteger("type")
            if (type != null && type != 0) {
                selection.add("${Telephony.Sms.TYPE} = ?")
                selectionArgs.add(type.toString())
            } else if (type == null) {
                // Par défaut : boîte de réception (INBOX, type = 1)
                selection.add("${Telephony.Sms.TYPE} = ?")
                selectionArgs.add("1")
            }

            if (filterObj.has("minDate")) {
                val minDate = filterObj.getLong("minDate")
                selection.add("${Telephony.Sms.DATE} >= ?")
                selectionArgs.add(minDate.toString())
            }

            if (filterObj.has("maxDate")) {
                val maxDate = filterObj.getLong("maxDate")
                selection.add("${Telephony.Sms.DATE} <= ?")
                selectionArgs.add(maxDate.toString())
            }

            if (filterObj.has("read")) {
                val readVal = filterObj.getInteger("read")
                selection.add("${Telephony.Sms.READ} = ?")
                selectionArgs.add(readVal.toString())
            }

            if (filterObj.has("seen")) {
                val seenVal = filterObj.getInteger("seen")
                selection.add("${Telephony.Sms.SEEN} = ?")
                selectionArgs.add(seenVal.toString())
            }

            // ─── Groupe B : Filtres exclusifs (Priorité : id > threadId > address > addressRegex > body) ───
            val id = filterObj.getString("id")
            val threadId = filterObj.getString("threadId")
            val address = filterObj.getString("address")
            val addressRegex = filterObj.getString("addressRegex")
            val body = filterObj.getString("body")

            if (id != null) {
                selection.add("${Telephony.Sms._ID} = ?")
                selectionArgs.add(id)
            } else if (threadId != null) {
                selection.add("${Telephony.Sms.THREAD_ID} = ?")
                selectionArgs.add(threadId)
            } else if (address != null) {
                selection.add("${Telephony.Sms.ADDRESS} = ?")
                selectionArgs.add(address)
            } else if (addressRegex != null) {
                // Filtré ultérieurement en mémoire (Kotlin)
            } else if (body != null) {
                selection.add("${Telephony.Sms.BODY} LIKE ?")
                selectionArgs.add("%$body%")
            }
        } else {
            // Comportement par défaut : boîte de réception (INBOX, type = 1)
            selection.add("${Telephony.Sms.TYPE} = ?")
            selectionArgs.add("1")
        }
    }
}
