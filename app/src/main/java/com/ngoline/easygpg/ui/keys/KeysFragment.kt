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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.ngoline.easygpg.KeyAdapter
import com.ngoline.easygpg.KeyItem
import com.ngoline.easygpg.PGPKeyManager
import com.ngoline.easygpg.R
import com.ngoline.easygpg.databinding.FragmentKeysBinding
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.util.encoders.Hex
import java.io.ByteArrayOutputStream

class KeysFragment() : Fragment() {

    private lateinit var keyManager: PGPKeyManager
    private lateinit var listViewKeys: ListView
    private lateinit var publicKeyDisplay: TextView
    private lateinit var copyButton: Button
    private lateinit var importButton: Button
    private lateinit var adapter: KeyAdapter
    private lateinit var context: Context

    private var _binding: FragmentKeysBinding? = null

    private val binding get() = _binding!!
    private val keyList = mutableListOf<String>()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Initialize utilities
        keyManager = PGPKeyManager(requireContext())

        // Generate and save keys
        keyManager.generateAndSaveKeys()

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

        val publicKeyRing = keyManager.loadPublicKeyRing()
        publicKeyDisplay.text = getFingerprint(publicKeyRing!!.publicKey)

        loadKeys()

        copyButton.setOnClickListener {
            if (publicKeyRing != null) {
                copyToClipboard(publicKeyRing.publicKey)
            } else {
                Toast.makeText(context, "No public key available", Toast.LENGTH_SHORT).show()
            }
        }

        importButton.setOnClickListener {
            showImportKeyDialog(context)
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun formatPublicKeyForExport(publicKey: PGPPublicKey): String {
        val out = ByteArrayOutputStream()
        val armoredStream = ArmoredOutputStream(out)
        publicKey.encode(armoredStream)
        armoredStream.close()
        return out.toString("UTF-8")
    }

    private fun getFingerprint(key: PGPPublicKey): String {
        return String(Hex.encode(key.fingerprint))
    }

    private fun copyToClipboard(publicKey: PGPPublicKey) {
        val formattedKey = formatPublicKeyForExport(publicKey)
        val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
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
                    val fingerprint = "${String(Hex.encode(publicKey!!.fingerprint)).take(16)}..."
                    val newKey = KeyItem(alias, fingerprint, publicKey)

                    adapter.addKey(newKey)
                } else {
                    Toast.makeText(context, "Alias and public key must not be empty", Toast.LENGTH_SHORT).show()
                }
            }
            setNegativeButton("Cancel", null)
            create().show()
        }
    }

    private fun loadKeys() {
        val keyManager = PGPKeyManager(requireContext())
        val keys = keyManager.getAllPublicKeys()
        adapter = KeyAdapter(keys, context)
        listViewKeys.adapter = adapter
    }
}