package com.newolf.fingerprinthelper_kotlin

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import com.newolf.fingerprinthelper_kotlin.constants.WolfConstants
import com.newolf.fingerprinthelper_kotlin.interfaces.HelperListener

/**
 * ================================================
 * @author : NeWolf
 * @version : 1.0
 * date :  2018/5/30
 * desc:
 * history:
 * ================================================
 */
@Suppress("unused")
@SuppressLint("NewApi")
class FingerprintHelper private constructor(var builder: Builder) {


    var isListening = false


    class Builder(c: Context, internal val listener: HelperListener) {
        internal var context = c as Activity
                ?: throw IllegalArgumentException("Context for FingerprintAuthHelper must be instance of Activity")
        internal var timeOut = WolfConstants.DEF_TRY_TIME_OUT
        internal var keyName = WolfConstants.TAG
        internal var logEnable = false

        fun setKeyName(keyName: String): Builder {
            this.keyName = keyName
            return this
        }

        fun setTryTimeOut(milliseconds: Long): Builder {
            if (milliseconds < WolfConstants.DEF_TRY_TIME_OUT) {
                throw IllegalArgumentException("tryTimeout must be more than ${WolfConstants.DEF_TRY_TIME_OUT} milliseconds!")
            }
            timeOut = milliseconds
            return this
        }

        fun setLogEnable(logEnable: Boolean): Builder {
            this.logEnable = logEnable
            return this
        }


        fun buid() = FingerprintHelper(this)
    }

    val isSdkVersionOk: Boolean by lazy {
        logThis("isSdkVersionOk called")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            logThis("sdkVersionOk")
            return@lazy true
        }
        logThis("fingerprintAuthHelper cant work with sdk version < 23 (Android M)")
        return@lazy false
    }


    private val helperManager = if (isSdkVersionOk) HelperManager(builder.context, builder.listener, builder.keyName, builder.timeOut, builder.logEnable) else null


    var isHardwareEnable = false
        private set
        get() {
            logThis("isHardwareEnable called")
            if (helperManager == null) {
                return false
            }

            field = helperManager.isHardwareEnabled()
            logThis("isHardwareEnable $field")
            return field
        }



    var canListenByUser = true
        @JvmName("canListenByUser")
        get() {
            logThis("getCanListenByUser called")
            logThis("canListenByUser = $field")
            return field
        }
        set(value) {
            logThis("setCanListenByUser called")
            logThis("setCanListenByUser = $value")
            canListenByUser = value
        }

    private fun logThis(s: String) {
        if (builder.logEnable) Log.d(WolfConstants.TAG, s)
    }


    var timeOutLeft = helperManager?.timeOutLeft ?: -1
        private set
        get() {
            logThis("getTimeOutLeft called")
            if (helperManager == null) {
                return -1
            }
            field = helperManager.timeOutLeft
            logThis("timeOutLeft = $field millisecond")
            return field

        }

    fun startListening(): Boolean {
        logThis("startListening called")
        if (helperManager == null) {
            return false
        }
        if (!canListenByUser) {
            return false
        }
        isListening = helperManager.startListening() && timeOutLeft <= 0
        logThis("isListening = $isListening")
        return isListening
    }

}