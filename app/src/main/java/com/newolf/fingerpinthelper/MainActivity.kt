package com.newolf.fingerpinthelper

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.support.v7.app.AppCompatActivity
import com.newolf.fingerprinthelper_kotlin.FingerprintHelper
import com.newolf.fingerprinthelper_kotlin.constants.ErrorType
import com.newolf.fingerprinthelper_kotlin.interfaces.HelperListener
import kotlinx.android.synthetic.main.activity_main.*



class MainActivity : AppCompatActivity(), HelperListener {
    val buffer = StringBuffer()
    var index = 1
    override fun onFingerprintListening(listening: Boolean, milliseconds: Long) {
        if (listening){
            tv.setText("请验证已录入的指纹")
        }else{
            val left = milliseconds/1000
            tv.setText("还剩下 $left 秒 \t $listening")
        }

    }

    override fun onFingerprintStatus(authSuccessful: Boolean, errorType: Int, errorMess: CharSequence?) {
//        Toast.makeText(this, "authSuccessful = $authSuccessful   errorMess = $errorMess ", Toast.LENGTH_SHORT).show()


        buffer.append("$index\t"+errorMess + "\t$authSuccessful")
        buffer.append("\r\n")
        tv.setText(buffer.toString())
        index++

        if (authSuccessful) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
//            finish()
        }else {
            when (errorType) {
                ErrorType.General.HARDWARE_DISABLED, ErrorType.General.NO_FINGERPRINTS -> mHelper.showSecuritySettingsDialog()


            }
        }
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
//        Toast.makeText(this, "isHardwareEnable = $isHardwareEnable", Toast.LENGTH_SHORT).show()
//        Toast.makeText(this, "canListenByUser = ${mHelper.canListenByUser}", Toast.LENGTH_SHORT).show()

//        mHelper.showSecuritySettingsDialog()
    }

    override fun onResume() {
        super.onResume()
        mHelper.startListening()
    }

//    override fun onStop() {
//        super.onStop()
//
//        mHelper.stopListening()
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        mHelper.destroy()
//    }
}
