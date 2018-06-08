package com.newolf.fingerprinthelper_kotlin.constants

import com.newolf.fingerprinthelper_kotlin.FingerprintHelper

/**
 * ================================================
 * @author : NeWolf
 * @version : 1.0
 * date :  2018/5/30
 * desc:
 * history:
 * ================================================
 */

internal object WolfConstants {
    val DEF_TRY_TIME_OUT = 45 * 1000L
    val TAG: String = FingerprintHelper::class.java.simpleName

    object TimeOutService {
        const val TIME_OUT_BROADCAST = "time_out_broadcast"
        const val KEY_TRY_TIME_OUT: String = "key_try_time_out"
    }

    object Manager {
        const val KEY_TIME_OUT_LEFT: String = "key_time_out_left"

    }

}