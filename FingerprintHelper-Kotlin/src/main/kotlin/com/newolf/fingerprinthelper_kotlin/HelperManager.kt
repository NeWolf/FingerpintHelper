package com.newolf.fingerprinthelper_kotlin

import android.Manifest
import android.annotation.TargetApi
import android.app.KeyguardManager
import android.content.*
import android.content.pm.PackageManager
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.support.annotation.RequiresApi
import android.support.v4.app.ActivityCompat
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import com.newolf.fingerprinthelper_kotlin.constants.ErrorType
import com.newolf.fingerprinthelper_kotlin.constants.WolfConstants
import com.newolf.fingerprinthelper_kotlin.interfaces.HelperListener
import com.newolf.fingerprinthelper_kotlin.services.TimeOutService
import java.io.IOException
import java.lang.ref.SoftReference
import java.security.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKey
import javax.security.cert.CertificateException

/**
 * ================================================
 * @author : NeWolf
 * @version : 1.0
 * date :  2018/5/31
 * desc:
 * history:
 * ================================================
 */
@RequiresApi(Build.VERSION_CODES.M)
class HelperManager(c: Context, l: HelperListener, val keyName: String, var timeOut: Long, val logEnable: Boolean) : FingerprintManager.AuthenticationCallback() {
    private var context: SoftReference<Context> = SoftReference(c)
    private var listener: SoftReference<HelperListener>? = l.let { SoftReference(l) }
    private var fingerprintManager = c.getSystemService(FingerprintManager::class.java)
    private var cipher: Cipher? = null
    private var keyStore: KeyStore? = null
    private var keyGenerator: KeyGenerator? = null
    private var cancellationSignal: CancellationSignal? = null
    private var cryptoObject: FingerprintManager.CryptoObject? = null
    private var keyguardManager: KeyguardManager = c.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private var shp: SharedPreferences = c.getSharedPreferences(WolfConstants.TAG, Context.MODE_PRIVATE)
    private var timeOutIntent: Intent? = null

    internal var timeOutLeft = 0L
        private set
    internal var triesCountLeft = 0
        private set
    private var isActivityForeground = false
    private var isListening = false
    private var afterStartListenTimeOut = false
    private var selfCancelled = false
    private var secureElementsReady = false
    private var broadcastRegistered = false

    private companion object {
        private const val KEY_TO_MANY_TRIES_ERROR = "KEY_TO_MANY_TRIES_ERROR"
        //        private const val KEY_LOGGING_ENABLE = "KEY_LOGGING_ENABLE"
//        private const val KEY_SECURE_KEY_NAME = "KEY_SECURE_KEY_NAME"
//        private const val KEY_IS_LISTENING = "KEY_IS_LISTENING"
        private const val TRY_LEFT_DEFAULT = 5
    }

    private var tryTimeOutDefault = timeOut

    private val timeOutBroadcast = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            timeOutLeft = intent.getLongExtra(WolfConstants.Manager.KEY_TIME_OUT_LEFT, -1)

            logThis("timeOutLeft = " + (timeOutLeft / 1000).toString() + " sec")

            if (timeOutLeft > 0) {
                if (isActivityForeground) {
                    listener?.get()?.onFingerprintListening(false, timeOutLeft)
                }
                saveTimeOut(System.currentTimeMillis() + timeOutLeft)
            } else if (timeOutLeft <= 0) {
                registerBroadcast(false)
                saveTimeOut(-1)
                triesCountLeft = TRY_LEFT_DEFAULT
                timeOut = tryTimeOutDefault
                if (isActivityForeground) {
                    startListening()
                }
                logThis("startListening after timeout")
            }
        }
    }

    private fun saveTimeOut(timesLeft: Long) {
        shp.edit().putLong(WolfConstants.Manager.KEY_TIME_OUT_LEFT, timesLeft).apply()
    }


    fun isHardwareEnabled(): Boolean {
        if (isPermissionNeeded()) {
//            throw SecurityException("Missing 'USE_FINGERPRINT' permission!")
            return false
        }
        return fingerprintManager.isHardwareDetected
    }

    private fun isPermissionNeeded(showError: Boolean = true): Boolean {
        if (context.get()?.let { ActivityCompat.checkSelfPermission(it, Manifest.permission.USE_FINGERPRINT) } == PackageManager.PERMISSION_GRANTED) {
            logThis("USE_FINGERPRINT PERMISSION = PERMISSION_GRANTED")
            return false
        }
        logThis("USE_FINGERPRINT PERMISSION = PERMISSION_DENIED")
        if (showError) {
            listener?.get()?.onFingerprintStatus(false, ErrorType.General.PERMISSION_NEEDED,
                    context.get()?.getString(R.string.PERMISSION_NEEDED))

        }
        return true
    }

    private fun logThis(s: String) {
        if (logEnable) Log.d(WolfConstants.TAG, s)
    }

    private fun isFingerprintEnrolled(showError: Boolean): Boolean {
        //Known issue with Samsung firmware: see the link
        //https://stackoverflow.com/questions/39372230/fingerprintmanagercompat-method-had-issues-with-samsung-devices
        //we need to call first Android method isHardwareDetected() to avoid
        //java.lang.SecurityException: Permission Denial: getCurrentUser() from pid=xxxxx, uid=xxxxx
        //requires android.permission.INTERACT_ACROSS_USERS
        if (!(isHardwareEnabled() && fingerprintManager.hasEnrolledFingerprints())) {
            if (showError) {
                listener?.get()?.onFingerprintStatus(false, ErrorType.General.NO_FINGERPRINTS,
                        context.get()?.getString(R.string.NO_FINGERPRINTS))
            }
            logThis("canListen failed. reason: " + context.get()?.getString(R.string.NO_FINGERPRINTS))
            secureElementsReady = false
            return false
        }
        return true
    }

    private fun isSecureComponentsInit(showError: Boolean): Boolean {
        logThis("isSecureComponentsInit start")
        if (isFingerprintEnrolled(showError) && !secureElementsReady) {
            try {
                cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                        + KeyProperties.BLOCK_MODE_CBC + "/"
                        + KeyProperties.ENCRYPTION_PADDING_PKCS7)
            } catch (e: Exception) {
                when (e) {
                    is NoSuchAlgorithmException, is NoSuchPaddingException -> {
                        logThis("Failed to get an instance of Cipher: " + e.message)
                        return false
                    }
                    else -> {
                        logThis("Unexpected exception. Reason: " + e.message)
                        return false
                    }
                }
            }

            val keyStore: KeyStore
            try {
                keyStore = KeyStore.getInstance("AndroidKeyStore")
                this.keyStore = keyStore
            } catch (e: Exception) {
                logThis("create keyStore failed: " + e.message)
                return false
            }

            val keyGenerator: KeyGenerator
            try {
                keyGenerator = KeyGenerator
                        .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                this.keyGenerator = keyGenerator
            } catch (e: Exception) {
                when (e) {
                    is NoSuchAlgorithmException, is NoSuchProviderException -> {
                        logThis("Failed to get an instance of KeyGenerator: " + e.message)
                        return false
                    }
                    else -> {
                        logThis("Unexpected exception. Reason: " + e.message)
                        return false
                    }
                }
            }

            try {
                keyStore.load(null)
                // Set the alias of the entry in Android KeyStore where the key will appear
                // and the constrains (purposes) in the constructor of the Builder

                val builder = KeyGenParameterSpec.Builder(keyName,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        // Require the user to authenticate with a fingerprint to authorize every use
                        // of the key
                        .setUserAuthenticationRequired(true)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)

                // This is a workaround to avoid crashes on devices whose API level is < 24
                // because KeyGenParameterSpec.Builder#setInvalidatedByBiometricEnrollment is only
                // visible on API level +24.
                // Ideally there should be a compat library for KeyGenParameterSpec.Builder but
                // which isn't available yet.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    builder.setInvalidatedByBiometricEnrollment(true)
                }
                try {
                    keyGenerator.init(builder.build())
                    keyGenerator.generateKey()
                } catch (e: Exception) {
                    secureElementsReady = false
                    logThis("isSecureComponentsInit failed. Reason: " + e.message)
                    return false
                }

            } catch (e: Exception) {
                when (e) {
                    is NoSuchAlgorithmException, is InvalidAlgorithmParameterException,
                    is CertificateException, is IOException -> {
                        logThis("isSecureComponentsInit failed. Reason: " + e.message)
                        return false
                    }
                    else -> {
                        logThis("Unexpected exception. Reason: " + e.message)
                        return false
                    }
                }
            }

            secureElementsReady = true
        }
        logThis("secureElementsReady = " + secureElementsReady)
        return secureElementsReady
    }

    internal fun canListen(showError: Boolean = true): Boolean {
        if (!isSecureComponentsInit(showError)) return false

        if (isPermissionNeeded(showError)) return false

        if (!isHardwareEnabled()) {
            if (showError) {
                listener?.get()?.onFingerprintStatus(false, ErrorType.General.HARDWARE_DISABLED,
                        context.get()?.getString(R.string.HARDWARE_DISABLED))
            }
            logThis("canListen failed. reason: " + context.get()?.getString(R.string.HARDWARE_DISABLED))
            return false
        }

        if (keyguardManager.isKeyguardSecure.not()) {
            if (showError) {
                listener?.get()?.onFingerprintStatus(false, ErrorType.General.LOCK_SCREEN_DISABLED,
                        context.get()?.getString(R.string.LOCK_SCREEN_DISABLED))
            }
            logThis("canListen failed. reason: " + context.get()?.getString(R.string.LOCK_SCREEN_DISABLED))
            return false
        }
        return true
    }

    @TargetApi(Build.VERSION_CODES.M)
    fun startListening(): Boolean {
        isActivityForeground = true
        if (timeOutLeft > 0 || !canListen(true) || !initCipher()) {
            isListening = false
            triesCountLeft = 0
        } else {
            afterStartListenTimeOut = true
            Handler().postDelayed({ afterStartListenTimeOut = false }, 200)
            cancellationSignal = CancellationSignal()
            selfCancelled = false
            // The line below prevents the false positive inspection from Android Studio

            fingerprintManager.authenticate(cryptoObject, cancellationSignal, 0, this, null)
            listener?.get()?.onFingerprintListening(true, 0)
            isListening = true
            triesCountLeft = TRY_LEFT_DEFAULT
        }
        registerBroadcast(true)
        return isListening

    }

    private fun registerBroadcast(register: Boolean) {
        logThis("timeOutLeft = $timeOutLeft  register = $register broadcastRegistered = $broadcastRegistered")

        if (timeOutLeft > 0 && register && !broadcastRegistered) {
            logThis("broadcastRegistered = " + true)
            broadcastRegistered = true
//            context.get()?.registerReceiver(timeOutBroadcast, )
            LocalBroadcastManager.getInstance(context.get()!!).registerReceiver(timeOutBroadcast, IntentFilter(WolfConstants.TimeOutService.TIME_OUT_BROADCAST))
        } else if (timeOutLeft > 0 && !register && broadcastRegistered) {
            logThis("broadcastRegistered = " + false)
            broadcastRegistered = false
//            context.get()?.unregisterReceiver(timeOutBroadcast)
            LocalBroadcastManager.getInstance(context.get()!!).unregisterReceiver(timeOutBroadcast)
        }
    }

    private fun initCipher(): Boolean {
        try {
            val keyStore = this.keyStore
            val cipher = this.cipher
            if (keyStore == null || cipher == null) {
                logThis("Couldn't initialize cypher. Keystore was null: ${keyStore == null}. Cipher was null: ${cipher == null}")
                return false
            }

            keyStore.load(null)
            cipher.init(Cipher.ENCRYPT_MODE, keyStore.getKey(keyName, null) as SecretKey)
            cryptoObject = FingerprintManager.CryptoObject(cipher)
            return true
        } catch (ex: Exception) {
            when (ex) {
                is KeyPermanentlyInvalidatedException, is KeyStoreException,
                is CertificateException, is UnrecoverableKeyException,
                is IOException, is NoSuchAlgorithmException,
                is InvalidKeyException -> {
                    logThis("initCipher failed. Reason: " + ex.message)
                    return false
                }
                else -> {
                    logThis("Unexpected exception. Reason: " + ex.message)
                    return false
                }
            }
        }
    }


    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
        super.onAuthenticationError(errorCode, errString)
        if (errorCode == FingerprintManager.FINGERPRINT_ERROR_CANCELED && afterStartListenTimeOut) {
            //this needs because if developer decide to stopListening in onPause method of activity
            //or fragment, than onAuthenticationError() will notify user about sensor is turnedOff
            //in the next onResume() method
            return
        }
        if (!selfCancelled && !afterStartListenTimeOut) {
            logThis("onAuthenticationError called")
            logThis("error: " + ErrorType.getErrorNameByCode(ErrorType.AUTH_ERROR_BASE + errorCode) +
                    " (" + errString + ")")

            listener?.get()?.onFingerprintStatus(false, ErrorType.AUTH_ERROR_BASE + errorCode,
                    errString)

            logThis("stopListening")

            listener?.get()?.onFingerprintListening(false, 0)

            if (errorCode == FingerprintManager.FINGERPRINT_ERROR_LOCKOUT) {
                shp.edit().putString(KEY_TO_MANY_TRIES_ERROR, errString.toString()).apply()
                runTimeOutService()
            }
        }
    }

    private fun runTimeOutService() {
        logThis("runTimeOutService")
        if (timeOutIntent == null) {
            timeOutIntent = Intent(context.get(), TimeOutService::class.java)
        }
        timeOutLeft = tryTimeOutDefault
        registerBroadcast(true)
        timeOutIntent?.putExtra(WolfConstants.TimeOutService.KEY_TRY_TIME_OUT, timeOut)
        context.get()?.startService(timeOutIntent)
        saveTimeOut(System.currentTimeMillis() + timeOut)
    }

    override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult?) {
        super.onAuthenticationSucceeded(result)
        listener?.get()?.onFingerprintStatus(true, ErrorType.Auth.AUTH_SUCCEEDED, context.get()?.getString(R.string.FINGERPRINT_SUCCEEDED))
    }

    override fun onAuthenticationHelp(helpCode: Int, helpString: CharSequence?) {
        super.onAuthenticationHelp(helpCode, helpString)
        triesCountLeft = TRY_LEFT_DEFAULT
        listener?.get()?.onFingerprintStatus(false, helpCode, helpString)
    }

    override fun onAuthenticationFailed() {
        super.onAuthenticationFailed()
        triesCountLeft--
        listener?.get()?.onFingerprintStatus(false, ErrorType.Auth.AUTH_NOT_RECOGNIZED, context.get()?.getString(R.string.FINGERPRINT_NOT_RECOGNIZED))
    }
}