package com.newolf.fingerprinthelper_kotlin.services

import android.app.IntentService
import android.content.Intent
import android.os.Handler
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import com.newolf.fingerprinthelper_kotlin.constants.WolfConstants

/**
 * ================================================
 * @author : NeWolf
 * @version : 1.0
 * date :  2018/6/1
 * desc:
 * history:
 * ================================================
 */
class TimeOutService : IntentService(TimeOutService::class.java.simpleName) {
    private val broadcastIntent: Intent by lazy { Intent(WolfConstants.TimeOutService.TIME_OUT_BROADCAST) }
    private val mTimeOutHandler = Handler()

    companion object {
        private var running = false
        private var wasStoppedPreviously = false
        private var timeOut: Long = 0
        private var timeOutLeft: Long = 0
        fun isRunning(): Boolean {
            return running
        }


        fun tryToStopMe(): Boolean {
            if (wasStoppedPreviously) return false
            wasStoppedPreviously = true
            if (timeOut - timeOutLeft >= WolfConstants.DEF_TRY_TIME_OUT) {
                timeOutLeft = 0
                return true
            } else {
                timeOutLeft = WolfConstants.DEF_TRY_TIME_OUT - (timeOut - timeOutLeft)
            }
            return false
        }


    }


    override fun onHandleIntent(intent: Intent?) {
        running = true
        timeOut = intent?.getLongExtra(WolfConstants.TimeOutService.KEY_TRY_TIME_OUT, -1) ?: -1
        Log.d(WolfConstants.TAG, "TimeOutService --> onHandleIntent -->timeOut =  $timeOut")
        timeOutLeft = timeOut
        if (timeOutLeft > 0) {
            timeoutRunnable.run()
        }


    }

    private var timeoutRunnable: Runnable = object : Runnable {
        override fun run() {
            running = true
            timeOutLeft -= 1000
            if (timeOutLeft > 0) {
                mTimeOutHandler.postDelayed(this, 1000)
            } else {
                running = false
                timeOutLeft = 0
            }
            broadcastIntent.putExtra(WolfConstants.Manager.KEY_TIME_OUT_LEFT, timeOutLeft)
//            sendBroadcast(broadcastIntent)
                LocalBroadcastManager.getInstance(this@TimeOutService).sendBroadcast(broadcastIntent)
            if (!running) {
                stopSelf()
            }
        }
    }


}