package com.ngoline.easygpg.ui.keys

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
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
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
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
    private lateinit var importButton: Button
    private lateinit var adapter: KeyAdapter
    private lateinit var context: Context
    private lateinit var spinnerMyKeys: Spinner

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
        importButton = root.findViewById(R.id.importButton)
        spinnerMyKeys = root.findViewById(R.id.spinnerMyKeys)

        publicKeyDisplay.text = "Select a key to view its fingerprint"
        copyButton.isEnabled = false

        loadMyKeys()
        loadKeys()

        // Spinner selection logic
        spinnerMyKeys.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedMyKey = myKeys.getOrNull(position)
                if (selectedMyKey != null) {
                    publicKeyDisplay.text = getFingerprint(selectedMyKey!!.publicKey)
                    copyButton.isEnabled = true
                } else {
                    publicKeyDisplay.text = getString(R.string.select_a_key_to_view_its_fingerprint)
                    copyButton.isEnabled = false
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {
                selectedMyKey = null
                publicKeyDisplay.text = getString(R.string.select_a_key_to_view_its_fingerprint)
                copyButton.isEnabled = false
            }
        })

        copyButton.setOnClickListener {
            selectedMyKey?.let {
                copyToClipboard(it.publicKeyRing)
            }
        }

        importButton.setOnClickListener {
            showImportKeyDialog(context)
        }

        listViewKeys.setOnItemClickListener { _, _, position, _ ->
            val keyItem = adapter.getItem(position) as KeyItem
            findNavController().navigate(
                R.id.nav_encrypt,
                bundleOf("selected_key_alias" to keyItem.alias)
            )
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

    private fun copyToClipboard(publicKeyRing: PGPPublicKeyRing) {
        val formattedKey = formatPublicKeyForExport(publicKeyRing)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Public Key", formattedKey)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Public key copied to clipboard", Toast.LENGTH_SHORT).show()
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
                        val fingerprint = "${String(Hex.encode(publicKey.fingerprint)).take(16)}..."
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