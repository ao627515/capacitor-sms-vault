package com.oaalab.capacitorsmsvault

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import com.getcapacitor.JSObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SmsReaderTest {

    private lateinit var context: Context
    private lateinit var smsReader: SmsReader

    // Provider SQLite en mémoire pour simuler la base SMS d'Android
    class MockSmsProvider : ContentProvider() {
        private lateinit var dbHelper: SQLiteOpenHelper

        override fun onCreate(): Boolean {
            dbHelper = object : SQLiteOpenHelper(context, "sms.db", null, 1) {
                override fun onCreate(db: SQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE sms (
                            _id INTEGER PRIMARY KEY AUTOINCREMENT,
                            address TEXT,
                            body TEXT,
                            date INTEGER,
                            type INTEGER,
                            read INTEGER,
                            thread_id INTEGER,
                            subject TEXT,
                            date_sent INTEGER,
                            creator TEXT,
                            person INTEGER
                        )
                        """.trimIndent()
                    )
                }
                override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }
            return true
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? {
            val db = dbHelper.readableDatabase
            return db.query("sms", projection, selection, selectionArgs, null, null, sortOrder)
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            val db = dbHelper.writableDatabase
            val id = db.insert("sms", null, values)
            return Uri.parse("content://sms/$id")
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            val db = dbHelper.writableDatabase
            return db.delete("sms", selection, selectionArgs)
        }

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int {
            val db = dbHelper.writableDatabase
            return db.update("sms", values, selection, selectionArgs)
        }

        override fun getType(uri: Uri): String? = null
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        smsReader = SmsReader(context)
        
        // Enregistrer notre provider SQL simulé pour l'autorité "sms"
        val info = android.content.pm.ProviderInfo().apply {
            authority = "sms"
        }
        org.robolectric.Robolectric.buildContentProvider(MockSmsProvider::class.java).create(info)
        
        // Nettoyer la base SMS avant chaque test
        context.contentResolver.delete(Telephony.Sms.CONTENT_URI, null, null)
    }

    private fun insertSms(
        address: String,
        body: String,
        date: Long,
        type: Int = Telephony.Sms.MESSAGE_TYPE_INBOX,
        read: Int = 1
    ) {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, date)
            put(Telephony.Sms.TYPE, type)
            put(Telephony.Sms.READ, read)
        }
        val uri = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
        assertNotNull("L'insertion du SMS a échoué dans le provider Robolectric", uri)
    }

    @Test
    fun testGetSmsList_NoFilter() {
        insertSms("0601020304", "Message un", 1000L)
        insertSms("0601020305", "Message deux", 2000L)

        // Par défaut sans filtre, getSmsList filtre sur type = 1 (Inbox) et trie par date DESC
        val messages = smsReader.getSmsList(null, null)
        
        assertEquals(2, messages.length())
        
        val first = messages.getJSONObject(0)
        assertEquals("0601020305", first.getString("address"))
        assertEquals("Message deux", first.getString("body"))
        assertEquals(2000L, first.getLong("date"))

        val second = messages.getJSONObject(1)
        assertEquals("0601020304", second.getString("address"))
        assertEquals("Message un", second.getString("body"))
        assertEquals(1000L, second.getLong("date"))
    }

    @Test
    fun testGetSmsList_FilterByType() {
        insertSms("0601020304", "Inbox SMS", 1000L, type = Telephony.Sms.MESSAGE_TYPE_INBOX)
        insertSms("0601020305", "Sent SMS", 2000L, type = Telephony.Sms.MESSAGE_TYPE_SENT)

        // Filtrer sur SENT (type 2)
        val filter = JSObject().apply {
            put("type", 2)
        }
        val messages = smsReader.getSmsList(filter, null)
        assertEquals(1, messages.length())
        assertEquals("Sent SMS", messages.getJSONObject(0).getString("body"))
    }

    @Test
    fun testGetSmsList_FilterByMinMaxDate() {
        insertSms("0601020304", "Ancien", 1000L)
        insertSms("0601020304", "Milieu", 2000L)
        insertSms("0601020304", "Recent", 3000L)

        val filter = JSObject().apply {
            put("minDate", 1500L)
            put("maxDate", 2500L)
        }
        
        val messages = smsReader.getSmsList(filter, null)
        assertEquals(1, messages.length())
        assertEquals("Milieu", messages.getJSONObject(0).getString("body"))
    }

    @Test
    fun testGetSmsList_FilterByAddressExact() {
        insertSms("0601020304", "Message de A", 1000L)
        insertSms("0601020305", "Message de B", 2000L)

        val filter = JSObject().apply {
            put("address", "0601020304")
        }
        
        val messages = smsReader.getSmsList(filter, null)
        assertEquals(1, messages.length())
        assertEquals("Message de A", messages.getJSONObject(0).getString("body"))
    }

    @Test
    fun testGetSmsList_FilterByBodyLike() {
        insertSms("0601020304", "Mon code de verification est 4242", 1000L)
        insertSms("0601020304", "Bonjour comment vas-tu?", 2000L)

        val filter = JSObject().apply {
            put("body", "verification")
        }
        
        val messages = smsReader.getSmsList(filter, null)
        assertEquals(1, messages.length())
        assertEquals("Mon code de verification est 4242", messages.getJSONObject(0).getString("body"))
    }

    @Test
    fun testGetSmsList_FilterByAddressRegex() {
        insertSms("TEST_OTP", "Mon code 123", 1000L)
        insertSms("0611223344", "Salut !", 2000L)
        insertSms("BANK_NOTIF", "Alerte !", 3000L)

        val filter = JSObject().apply {
            put("addressRegex", "^[a-zA-Z_]+$")
        }
        
        val messages = smsReader.getSmsList(filter, null)
        assertEquals(2, messages.length()) // TEST_OTP et BANK_NOTIF correspondent
        assertEquals("BANK_NOTIF", messages.getJSONObject(0).getString("address"))
        assertEquals("TEST_OTP", messages.getJSONObject(1).getString("address"))
    }

    @Test
    fun testGetSmsList_FilterByBodyRegex() {
        insertSms("0601020304", "Votre code est 9987", 1000L)
        insertSms("0601020304", "Votre code de confirmation est 1245", 2000L)
        insertSms("0601020304", "Pas de code ici", 3000L)

        val filter = JSObject().apply {
            put("bodyRegex", "code.*\\d{4}")
        }
        
        val messages = smsReader.getSmsList(filter, null)
        assertEquals(2, messages.length())
        assertEquals("Votre code de confirmation est 1245", messages.getJSONObject(0).getString("body"))
        assertEquals("Votre code est 9987", messages.getJSONObject(1).getString("body"))
    }

    @Test
    fun testGetSmsList_PaginationAndSorting() {
        insertSms("0601020304", "Msg 1", 1000L)
        insertSms("0601020304", "Msg 2", 2000L)
        insertSms("0601020304", "Msg 3", 3000L)
        insertSms("0601020304", "Msg 4", 4000L)

        // 1. Tri date_asc + pagination
        val filterAsc = JSObject().apply {
            put("orderBy", "date_asc")
            put("indexFrom", 1)
            put("maxCount", 2)
        }
        
        val messagesAsc = smsReader.getSmsList(filterAsc, null)
        assertEquals(2, messagesAsc.length())
        assertEquals("Msg 2", messagesAsc.getJSONObject(0).getString("body")) // index 1 (deuxième plus ancien)
        assertEquals("Msg 3", messagesAsc.getJSONObject(1).getString("body")) // index 2

        // 2. Tri date_desc (défaut) + pagination
        val filterDesc = JSObject().apply {
            put("indexFrom", 1)
            put("maxCount", 2)
        }
        
        val messagesDesc = smsReader.getSmsList(filterDesc, null)
        assertEquals(2, messagesDesc.length())
        assertEquals("Msg 3", messagesDesc.getJSONObject(0).getString("body")) // index 1 (deuxième plus récent)
        assertEquals("Msg 2", messagesDesc.getJSONObject(1).getString("body")) // index 2
    }

    @Test
    fun testGetCount() {
        insertSms("0601020304", "Code: 1111", 1000L)
        insertSms("0601020305", "Code: 2222", 2000L)
        insertSms("0601020306", "Pas de code", 3000L)

        // 1. Sans filtre regex (chemin SQL direct)
        val filterSql = JSObject().apply {
            put("address", "0601020304")
        }
        assertEquals(1, smsReader.getCount(filterSql))

        // 2. Avec regex (chemin d'évaluation en mémoire)
        val filterRegex = JSObject().apply {
            put("bodyRegex", "Code: \\d{4}")
        }
        assertEquals(2, smsReader.getCount(filterRegex))
    }
}
