package com.oaalab.capacitorsmsvault

import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.Telephony
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Logger

/**
 * Encapsule la lecture de l'historique SMS via ContentResolver.
 * Prend en charge la projection, le filtrage SQL natif, et le filtrage par expressions régulières en mémoire.
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

        // 1. Déterminer la projection SQL (colonnes à interroger)
        val projectionList = mutableListOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        val hasThreadId = projectionObj?.getBoolean("threadId") == true
        if (hasThreadId) projectionList.add(Telephony.Sms.THREAD_ID)

        val hasSubject = projectionObj?.getBoolean("subject") == true
        if (hasSubject) projectionList.add(Telephony.Sms.SUBJECT)

        val hasDateSent = projectionObj?.getBoolean("dateSent") == true
        if (hasDateSent) projectionList.add(Telephony.Sms.DATE_SENT)

        val hasCreator = projectionObj?.getBoolean("creator") == true
        if (hasCreator && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            projectionList.add(Telephony.Sms.CREATOR)
        }

        val hasPerson = projectionObj?.getBoolean("person") == true
        if (hasPerson) projectionList.add(Telephony.Sms.PERSON)

        // 2. Construire la clause WHERE
        val selection = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()
        buildSelectionAndArgs(filterObj, selection, selectionArgs)

        val finalSelection = if (selection.isEmpty()) null else selection.joinToString(" AND ")
        val finalSelectionArgs = if (selectionArgs.isEmpty()) null else selectionArgs.toTypedArray()

        // 3. Déterminer l'ordre de tri (date ASC ou DESC)
        val orderByStr = filterObj?.getString("orderBy")
        val sortOrder = when (orderByStr) {
            "date_asc"  -> "${Telephony.Sms.DATE} ASC"
            "date_desc" -> "${Telephony.Sms.DATE} DESC"
            else        -> "${Telephony.Sms.DATE} DESC"
        }

        // 4. Préparer les Regex (pour filtrage tardif en Kotlin)
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

                val threadIdIdx = if (hasThreadId) c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID) else -1
                val subjectIdx = if (hasSubject) c.getColumnIndexOrThrow(Telephony.Sms.SUBJECT) else -1
                val dateSentIdx = if (hasDateSent) c.getColumnIndexOrThrow(Telephony.Sms.DATE_SENT) else -1
                val creatorIdx = if (hasCreator && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    c.getColumnIndexOrThrow(Telephony.Sms.CREATOR)
                } else -1
                val personIdx = if (hasPerson) c.getColumnIndexOrThrow(Telephony.Sms.PERSON) else -1

                var matchCount = 0
                var addedCount = 0

                while (c.moveToNext()) {
                    val addressVal = c.getString(addressIndex) ?: ""
                    val bodyVal = c.getString(bodyIndex) ?: ""

                    // Filtrage Regex
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

                                if (hasThreadId && threadIdIdx != -1) put("threadId", c.getString(threadIdIdx) ?: "")
                                if (hasSubject && subjectIdx != -1) put("subject", c.getString(subjectIdx) ?: "")
                                if (hasDateSent && dateSentIdx != -1) put("dateSent", c.getLong(dateSentIdx))
                                if (hasCreator && creatorIdx != -1) put("creator", c.getString(creatorIdx) ?: "")
                                if (hasPerson && personIdx != -1) put("person", c.getString(personIdx) ?: "")
                            }
                            result.put(smsObj)
                            addedCount++
                        } else {
                            break // MaxCount atteint
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
            // Pas de regex : exécution SQL direct COUNT (très performant)
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
            // Avec regex : nous devons tester en mémoire
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
     * Retourne toutes les colonnes brutes sans mapping particulier.
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
            // null en projection = toutes les colonnes
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
     * Construit les filtres SQL basés sur l'objet de filtrage.
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
                // Par défaut : INBOX (type = 1)
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
                // Filtré tardivement en mémoire (Kotlin)
            } else if (body != null) {
                selection.add("${Telephony.Sms.BODY} LIKE ?")
                selectionArgs.add("%$body%")
            }
        } else {
            // Comportement par défaut : boîte INBOX (type = 1)
            selection.add("${Telephony.Sms.TYPE} = ?")
            selectionArgs.add("1")
        }
    }
}
