package com.oaalab.capacitorsmsvault

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import androidx.test.core.app.ApplicationProvider
import com.getcapacitor.JSObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.lang.ref.WeakReference

@Implements(SmsMessage::class)
class ShadowSmsMessage {
    companion object {
        var mockAddress: String? = null
        var mockBody: String? = null
        var mockTimestamp: Long = 0L
        var mockServiceCenter: String? = null
        var returnNull = false

        @JvmStatic
        @Implementation
        fun createFromPdu(pdu: ByteArray?): SmsMessage? {
            if (returnNull) return null
            return org.robolectric.util.ReflectionHelpers.callConstructor(SmsMessage::class.java)
        }

        @JvmStatic
        @Implementation
        fun createFromPdu(pdu: ByteArray?, format: String?): SmsMessage? {
            return createFromPdu(pdu)
        }
    }

    @Implementation
    fun getDisplayOriginatingAddress(): String? {
        return mockAddress
    }

    @Implementation
    fun getMessageBody(): String? {
        return mockBody
    }

    @Implementation
    fun getTimestampMillis(): Long {
        return mockTimestamp
    }

    @Implementation
    fun getServiceCenterAddress(): String? {
        return mockServiceCenter
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, shadows = [ShadowSmsMessage::class])
class SmsReceiverTest {

    private lateinit var context: Context
    private lateinit var mockPlugin: SmsVaultPlugin
    private lateinit var smsReceiver: SmsReceiver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockPlugin = mock()
        smsReceiver = SmsReceiver().apply {
            pluginRef = WeakReference(mockPlugin)
        }
        
        // Réinitialisation des paramètres du shadow avant chaque test.
        ShadowSmsMessage.mockAddress = null
        ShadowSmsMessage.mockBody = null
        ShadowSmsMessage.mockTimestamp = 0L
        ShadowSmsMessage.mockServiceCenter = null
        ShadowSmsMessage.returnNull = false
    }

    @Test
    fun testOnReceive_SmsReceivedAction_Success() {
        // Configuration des valeurs retournées par notre shadow.
        ShadowSmsMessage.mockAddress = "0601020304"
        ShadowSmsMessage.mockBody = "Votre code d'activation est 4567."
        ShadowSmsMessage.mockTimestamp = 1234567890L
        ShadowSmsMessage.mockServiceCenter = "+22689001234"

        // Création d'un Intent avec des PDU fictifs.
        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            putExtra("pdus", arrayOf(byteArrayOf(1)))
            putExtra("format", "3gpp")
        }

        smsReceiver.onReceive(context, intent)

        val captor = argumentCaptor<JSObject>()
        verify(mockPlugin).notifySmsReceived(captor.capture())

        val jsObject = captor.firstValue
        assertEquals("0601020304", jsObject.getString("address"))
        assertEquals("Votre code d'activation est 4567.", jsObject.getString("body"))
        assertEquals(1234567890L, jsObject.getLong("date"))
        assertEquals(1, jsObject.getInt("type")) // MessageType.INBOX = 1
        assertEquals(false, jsObject.getBoolean("seen"))
        assertEquals(false, jsObject.getBoolean("locked"))
        assertEquals(-1, jsObject.getInt("status"))
        assertEquals(0, jsObject.getInt("errorCode"))
        assertEquals("+22689001234", jsObject.getString("serviceCenter"))
    }

    @Test
    fun testOnReceive_WrongAction_NoActionTaken() {
        val intent = Intent("android.intent.action.BOOT_COMPLETED")
        smsReceiver.onReceive(context, intent)
        verifyNoInteractions(mockPlugin)
    }

    @Test
    fun testOnReceive_EmptyMessages_NoActionTaken() {
        ShadowSmsMessage.returnNull = true

        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            putExtra("pdus", arrayOf(byteArrayOf(1)))
            putExtra("format", "3gpp")
        }
        smsReceiver.onReceive(context, intent)

        verifyNoInteractions(mockPlugin)
    }
}
