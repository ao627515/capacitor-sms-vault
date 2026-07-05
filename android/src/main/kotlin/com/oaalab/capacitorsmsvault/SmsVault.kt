package com.oaalab.capacitorsmsvault

import com.getcapacitor.Logger

class SmsVault {

    fun echo(value: String): String {
        Logger.info("Echo", value)

        return value
    }
}
