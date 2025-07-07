package com.ngoline.easygpg.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.ngoline.easygpg.PGPKeyManager
import com.ngoline.easygpg.R
import com.yubico.yubikit.android.YubiKitManager
import com.yubico.yubikit.android.transport.nfc.NfcConfiguration
import com.yubico.yubikit.android.transport.nfc.NfcNotAvailable
import com.yubico.yubikit.android.transport.nfc.NfcYubiKeyDevice
import com.yubico.yubikit.android.transport.usb.UsbConfiguration
import com.yubico.yubikit.core.smartcard.SmartCardConnection
import com.yubico.yubikit.openpgp.KeyRef
import com.yubico.yubikit.openpgp.OpenPgpSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.properties.Delegates

class SettingsFragment : PreferenceFragmentCompat() {

    private val LOG_TAG = "SettingsFragment"
    private val viewModel: SettingsViewModel by viewModels()
    private lateinit var yubiKit: YubiKitManager
    private lateinit var keyManager: PGPKeyManager
    private val nfcConfiguration = NfcConfiguration().timeout(15000)

    private var hasNfc by Delegates.notNull<Boolean>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        yubiKit = YubiKitManager(requireContext())
        keyManager = PGPKeyManager(requireContext())
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == "import_key") {
            importPublicKeyFromYubikey()
            return true
        }

        if (preference.key == "generate_key")
        {
            generateKey()
            return true
        }

        return super.onPreferenceTreeClick(preference)
    }

    override fun onResume() {
        super.onResume()
        //if (viewModel.handleYubiKey.value == true && hasNfc) {
        //    try {
        //        yubiKit.startNfcDiscovery(nfcConfiguration, activity) { device ->
          //          Log.i(LOG_TAG, "NFC device connected $device")
            //        nfcAcquired(device)
              //  }
            //} catch (e: NfcNotAvailable) {
             //   Log.e(LOG_TAG, "NFC is not available", e)
            //}
        //}
    }

    override fun onPause() {
        yubiKit.stopNfcDiscovery(requireActivity())
        super.onPause()
    }

    override fun onDestroy() {
        viewModel.yubiKey.value = null
        yubiKit.stopUsbDiscovery()
        super.onDestroy()
    }

    private fun generateKey() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Enter an alias for this key")
        val input = EditText(requireContext())
        builder.setView(input)
        builder.setPositiveButton("OK") { _, _ ->
            val alias = input.text.toString()
            keyManager.generateAndSaveKeys(alias)
            Toast.makeText(requireContext(), "Public keyring imported and trusted!", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun importPublicKeyFromYubikey() {
        Toast.makeText(requireContext(), "Waiting for Yubikey... (insert via USB or tap via NFC)", Toast.LENGTH_LONG).show()
        viewModel.handleYubiKey.observe(this) {
            if (it) {
                Log.i(LOG_TAG, "Enable listening")
                yubiKit.startUsbDiscovery(UsbConfiguration()) { device ->
                    Log.i(LOG_TAG, "USB device attached $device, current: ${viewModel.yubiKey.value}")
                    viewModel.yubiKey.postValue(device)
                    device.setOnClosed {
                        Log.i(LOG_TAG, "Device removed $device")
                        viewModel.yubiKey.postValue(null)
                    }
                }
                try {
                    yubiKit.startNfcDiscovery(nfcConfiguration, requireActivity()) { device ->
                        Log.i(LOG_TAG,"NFC Session started $device")
                        nfcAcquired(device)
                    }
                    hasNfc = true
                } catch (e: NfcNotAvailable) {
                    hasNfc = false
                    Log.e(LOG_TAG, "Error starting NFC listening", e)
                }
            } else {
                Log.i(LOG_TAG,"Disable listening")
                yubiKit.stopNfcDiscovery(requireActivity())
                yubiKit.stopUsbDiscovery()
            }
        }
    }

    private fun nfcAcquired(device: NfcYubiKeyDevice){
        /*lifecycleScope.launch {
            try {
                val keyRefs = listOf(KeyRef.SIG, KeyRef.DEC)
                val connection = withContext(Dispatchers.IO) {
                    device.openConnection(SmartCardConnection::class.java)
                }
                val openPgpSession = OpenPgpSession(connection)
                val pubKeys = mutableListOf<org.bouncycastle.openpgp.PGPPublicKey>()
                for (keyRef in keyRefs) {
                    try {
                        val pubKeyData = withContext(Dispatchers.IO) {
                            openPgpSession.getPublicKey(keyRef)
                        }
                        if (pubKeyData != null) {
                            // Try to parse as a keyring, otherwise wrap as a single-key keyring
                            try {
                                val keyRing = org.bouncycastle.openpgp.PGPPublicKeyRing(
                                    pubKeyData.encoded.inputStream(),
                                    org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator()
                                )
                                val keyIter = keyRing.publicKeys
                                while (keyIter.hasNext()) {
                                    val pubKey = keyIter.next() as org.bouncycastle.openpgp.PGPPublicKey
                                    pubKeys.add(pubKey)
                                }
                            } catch (_: Exception) {
                                // If not a keyring, treat as a single key and wrap in a keyring
                                try {
                                    val singleKey = org.bouncycastle.openpgp.PGPPublicKey(
                                        pubKeyData.encoded.inputStream(),
                                        org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator()
                                    )
                                    val keyRing = org.bouncycastle.openpgp.PGPPublicKeyRing(listOf(singleKey))
                                    val keyIter = keyRing.publicKeys
                                    while (keyIter.hasNext()) {
                                        val pubKey = keyIter.next() as org.bouncycastle.openpgp.PGPPublicKey
                                        pubKeys.add(pubKey)
                                    }
                                } catch (e: Exception) {
                                    Log.e(LOG_TAG, "Error parsing public key for $keyRef: ${e.localizedMessage}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(LOG_TAG, "Error reading public key for $keyRef: ${e.localizedMessage}")
                        // Ignore missing slots
                    }
                }
                if (pubKeys.isNotEmpty()) {
                    val mergedRing = org.bouncycastle.openpgp.PGPPublicKeyRing(pubKeys)
                    val baos = java.io.ByteArrayOutputStream()
                    val aos = org.bouncycastle.bcpg.ArmoredOutputStream(baos)
                    mergedRing.encode(aos)
                    aos.close()
                    val armored = baos.toByteArray()
                    val fingerprint = PublicKeyStore.fingerprintOf(armored)
                    promptForAliasAndStore(armored, fingerprint)
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to import key: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }*/
    }
}