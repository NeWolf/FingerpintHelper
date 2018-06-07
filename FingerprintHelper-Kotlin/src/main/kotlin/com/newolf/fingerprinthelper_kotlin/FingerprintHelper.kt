package com.newolf.fingerprinthelper_kotlin

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.OnLifecycleEvent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.support.v4.app.FragmentActivity
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
class FingerprintHelper private constructor(val builder: Builder) : LifecycleObserver {
    init {
        builder.context.lifecycle.addObserver(this)
    }


    private var isListening = false
    private var canListenBySystem = false
    private val logEnable = builder.logEnable
    private val context = builder.context
    private var dialog: AlertDialog? = null

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


    var triesCountLeft: Int = 0
        private set
        get() {
            logThis("getTriesCountLeft called")
            if (helperManager == null) {
                return 0
            }
            field = helperManager.triesCountLeft
            logThis("triesCountLeft = $field")
            return field
        }


    var isFingerprintEnrolled = false
        private set
        get() {
            if (helperManager == null) {
                return false
            }
            field = helperManager.isFingerprintEnrolled()
            logThis("isFingerprintEnrolled = " + field)
            return field
        }

    fun cleanTimeOut(): Boolean {
        logThis("cleanTimeOut called")
        if (helperManager == null) {
            return false
        }
        val isCleaned = helperManager.cleanTimeOut()
        logThis("timeOutCleaned = $isCleaned")
        return isCleaned
    }

    fun canListen(showError: Boolean): Boolean {
        logThis("canListen called")
        if (helperManager == null) {
            return false
        }
        canListenByUser
        canListenBySystem = helperManager.canListen(showError)
        logThis("canListenBySystem = $canListenBySystem")
        logThis("can listen = ${canListenByUser && canListenBySystem}")
        return canListenByUser && canListenBySystem
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


    //    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun stopListening(): Boolean {
        logThis("stopListening called")
        if (helperManager == null) {
            return false
        }
        if (!canListenByUser) {
            return false
        }
        isListening = helperManager.stopListening()
        logThis("isListening = $isListening")
        return isListening
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy(): Boolean {
        logThis("onDestroy called")

        if (helperManager == null) {
            return false
        }
        helperManager.onDestroy()
        builder.context.lifecycle.removeObserver(this)

        logThis("onDestroy successful")
        return true
    }

    fun openSecuritySettings() {
        logThis("openSecuritySettings called")
        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
    }


    fun showSecuritySettingsDialog() {
        if (dialog == null) {
            dialog = AlertDialog.Builder(builder.context)
                    .setTitle(R.string.fingerprint_dialog_title)
                    .setMessage(R.string.fingerprint_dialog_msg)
                    .setNegativeButton(R.string.fingerprint_dialog_negative, null)
                    .setPositiveButton(R.string.fingerprint_dialog_positive, { dialog, _ ->
                        run {
                            dialog.dismiss()
                            openSecuritySettings()
                        }
                    })
                    .create()
        }

        dialog?.show()

    }


    private fun logThis(s: String) {
        if (logEnable) Log.d(WolfConstants.TAG, s)
    }

    class Builder(c: Context, internal val listener: HelperListener) {
        internal var context = c as FragmentActivity
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
}


