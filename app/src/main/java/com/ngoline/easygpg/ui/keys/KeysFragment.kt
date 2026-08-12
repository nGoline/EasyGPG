package com.ngoline.easygpg.ui.keys

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.PersistableBundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.ngoline.easygpg.data.KeyAdapter
import com.ngoline.easygpg.data.KeyItem
import com.ngoline.easygpg.PGPKeyManager
import com.ngoline.easygpg.R
import com.ngoline.easygpg.databinding.FragmentKeysBinding
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.bouncycastle.util.encoders.Hex
import java.io.ByteArrayOutputStream
import kotlin.jvm.java

class KeysFragment() : Fragment() {

    private lateinit var keyManager: PGPKeyManager
    private lateinit var listViewKeys: ListView
    private lateinit var publicKeyDisplay: TextView
    private lateinit var copyButton: Button
    private lateinit var exportPrivateKeyButton: Button
    private lateinit var importButton: Button
    private lateinit var adapter: KeyAdapter
    private lateinit var context: Context
    private lateinit var spinnerMyKeys: Spinner
    private lateinit var deleteMyKeyButton: Button

    private var _binding: FragmentKeysBinding? = null

    private val binding get() = _binding!!
    private val keyList = mutableListOf<String>()

    private var selectedKeyItem: KeyItem? = null
    private var myKeys: List<KeyItem> = emptyList()
    private var selectedMyKey: KeyItem? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Initialize utilities
        keyManager = PGPKeyManager(requireContext())

        this.context = context
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val keysViewModel =
            ViewModelProvider(this)[KeysViewModel::class.java]

        _binding = FragmentKeysBinding.inflate(inflater, container, false)
        val root: View = binding.root

        listViewKeys = root.findViewById(R.id.listViewKeys)
        publicKeyDisplay = root.findViewById(R.id.publicKeyDisplay)
        copyButton = root.findViewById(R.id.copyButton)
        exportPrivateKeyButton = root.findViewById(R.id.exportPrivateKeyButton)
        importButton = root.findViewById(R.id.importButton)
        spinnerMyKeys = root.findViewById(R.id.spinnerMyKeys)
        deleteMyKeyButton = root.findViewById(R.id.deleteMyKeyButton)

        updateMyKeyDisplay(null)

        loadMyKeys()
        loadKeys()

        // Spinner selection logic
        spinnerMyKeys.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                updateMyKeyDisplay(myKeys.getOrNull(position))
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {
                updateMyKeyDisplay(null)
            }
        })

        copyButton.setOnClickListener {
            selectedMyKey?.let {
                copyToClipboard(it.publicKeyRing)
            }
        }

        exportPrivateKeyButton.setOnClickListener {
            selectedMyKey?.let(::showExportPrivateKeyWarning)
        }

        importButton.setOnClickListener {
            showImportKeyDialog(context)
        }

        deleteMyKeyButton.setOnClickListener {
            val keyToDelete = selectedMyKey
            if (keyToDelete == null) {
                Toast.makeText(context, R.string.no_key_selected, Toast.LENGTH_SHORT).show()
            } else {
                showDeleteMyKeyConfirmDialog(keyToDelete)
            }
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun formatPublicKeyForExport(publicKeyRing: PGPPublicKeyRing): String {
        val out = ByteArrayOutputStream()
        val armoredStream = ArmoredOutputStream(out)
        publicKeyRing.encode(armoredStream)
        armoredStream.close()
        return out.toString("UTF-8")
    }

    private fun getFingerprint(key: PGPPublicKey): String {
        return String(Hex.encode(key.fingerprint))
    }

    private fun updateMyKeyDisplay(keyItem: KeyItem?) {
        selectedMyKey = keyItem
        if (keyItem != null) {
            publicKeyDisplay.text = getFingerprint(keyItem.publicKey)
            copyButton.isEnabled = true
            exportPrivateKeyButton.isEnabled = true
        } else {
            publicKeyDisplay.text = getString(R.string.select_a_key_to_view_its_fingerprint)
            copyButton.isEnabled = false
            exportPrivateKeyButton.isEnabled = false
        }
    }

    private fun copyToClipboard(publicKeyRing: PGPPublicKeyRing) {
        val formattedKey = formatPublicKeyForExport(publicKeyRing)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Public Key", formattedKey)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Public key copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun showExportPrivateKeyWarning(keyItem: KeyItem) {
        AlertDialog.Builder(context).apply {
            setTitle(R.string.export_private_key_warning_title)
            setMessage(R.string.export_private_key_warning_message)
            setPositiveButton(R.string.continue_export) { _, _ ->
                authenticateForPrivateKeyExport(keyItem)
            }
            setNegativeButton(android.R.string.cancel, null)
            create().show()
        }
    }

    private fun authenticateForPrivateKeyExport(keyItem: KeyItem) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(requireContext()).canAuthenticate(authenticators) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            Toast.makeText(context, R.string.authentication_required, Toast.LENGTH_LONG).show()
            return
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.export_private_key))
            .setSubtitle(getString(R.string.authenticate_to_export_private_key))
            .setAllowedAuthenticators(authenticators)
            .build()
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(requireContext()),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    copyPrivateKeyToClipboard(keyItem.alias)
                }
            }
        )
        prompt.authenticate(promptInfo)
    }

    private fun copyPrivateKeyToClipboard(alias: String) {
        val privateKey = keyManager.exportPrivateKey(alias)
        if (privateKey == null) {
            Toast.makeText(context, R.string.private_key_export_failed, Toast.LENGTH_LONG).show()
            return
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(getString(R.string.export_private_key), privateKey)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, R.string.private_key_copied, Toast.LENGTH_SHORT).show()
    }

    private fun showImportKeyDialog(context: Context) {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_import_key, null)
        val aliasInput = view.findViewById<EditText>(R.id.editTextAlias)
        val publicKeyInput = view.findViewById<EditText>(R.id.editTextPublicKey)

        AlertDialog.Builder(context).apply {
            setView(view)
            setTitle("Import Public Key")
            setPositiveButton("Import") { _, _ ->
                val alias = aliasInput.text.toString().trim()
                val keyData = publicKeyInput.text.toString()
                if (alias.isNotEmpty() && keyData.isNotEmpty()) {
                    val publicKey = keyManager.importPublicKey(alias, keyData)
                    val publicKeyRing = keyManager.loadImportedKeyRing(
                        java.io.File(context.filesDir, "$alias.imported.pgp")
                    )
                    if (publicKey != null && publicKeyRing != null) {
                        val fingerprint = String(Hex.encode(publicKey.fingerprint))
                        val newKey = KeyItem(alias, fingerprint, publicKey, publicKeyRing)
                        adapter.addKey(newKey)
                    } else {
                        Toast.makeText(context, "Failed to import key", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Alias and public key must not be empty", Toast.LENGTH_SHORT).show()
                }
            }
            setNegativeButton("Cancel", null)
            create().show()
        }
    }

    private fun showDeleteMyKeyConfirmDialog(keyItem: KeyItem) {
        AlertDialog.Builder(context).apply {
            setTitle(R.string.delete_key_confirm_title)
            setMessage(getString(R.string.delete_key_confirm_message, keyItem.alias))
            setPositiveButton(R.string.delete_key) { _, _ ->
                keyManager.deleteMyKey(keyItem.alias)
                Toast.makeText(context, R.string.key_deleted, Toast.LENGTH_SHORT).show()
                loadMyKeys()
                updateMyKeyDisplay(myKeys.firstOrNull())
            }
            setNegativeButton(android.R.string.cancel, null)
            create().show()
        }
    }

    private fun loadMyKeys() {
        myKeys = keyManager.getMyPublicKeys()
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, myKeys.map { it.alias })
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMyKeys.adapter = spinnerAdapter
        // Set default selection
        if (myKeys.isNotEmpty()) {
            spinnerMyKeys.setSelection(0)
        }
    }

    private fun loadKeys() {
        val keys = keyManager.getAllPublicKeys()
        adapter = KeyAdapter(keys, context)
        listViewKeys.adapter = adapter
    }
}
