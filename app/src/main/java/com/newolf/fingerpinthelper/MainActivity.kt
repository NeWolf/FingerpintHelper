package com.newolf.fingerpinthelper

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import com.newolf.fingerprinthelper_kotlin.FingerprintHelper
import com.newolf.fingerprinthelper_kotlin.interfaces.HelperListener

class MainActivity : AppCompatActivity(), HelperListener {
    override fun onFingerprintListening(listening: Boolean, milliseconds: Long) {

    }

    override fun onFingerprintStatus(authSuccessful: Boolean, errorType: Int, errorMess: CharSequence?) {
        Toast.makeText(this, "authSuccessful = $authSuccessful   errorMess = $errorMess ", Toast.LENGTH_SHORT).show()
    }

    lateinit var mHelper: FingerprintHelper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mHelper = FingerprintHelper.Builder(this, this)
                .setKeyName(javaClass.simpleName)
                .setTryTimeOut(60 * 1000L)
                .setLogEnable(true)
                .buid()

        val isHardwareEnable: Boolean = mHelper.isHardwareEnable
        Toast.makeText(this, "isHardwareEnable = $isHardwareEnable", Toast.LENGTH_SHORT).show()
        Toast.makeText(this, "canListenByUser = ${mHelper.canListenByUser}", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        mHelper.startListening()
    }

    override fun onStop() {
        super.onStop()

//        mHelper.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
//        mHelper.destroy()
    }
}
