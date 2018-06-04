package com.newolf.fingerprinthelper_kotlin.interfaces

/**
 * ================================================
 * @author : NeWolf
 * @version : 1.0
 * date :  2018/5/30
 * desc:
 * history:
 * ================================================
 */
interface HelperListener {
    fun onFingerprintStatus(authSuccessful: Boolean, errorType: Int, errorMess: CharSequence?)
    fun onFingerprintListening(listening: Boolean, milliseconds: Long)
}