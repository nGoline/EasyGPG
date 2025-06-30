package com.ngoline.easygpg.ui.decrypt

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.text.set
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.ngoline.easygpg.PGPKeyManager
import com.ngoline.easygpg.R
import com.ngoline.easygpg.databinding.FragmentDecryptBinding
import com.ngoline.easygpg.databinding.FragmentEncryptBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DecryptFragment : Fragment() {

    private var _binding: FragmentDecryptBinding? = null

    private lateinit var editTextMessage: EditText
    private lateinit var buttonDecrypt: Button
    private lateinit var keyManager: PGPKeyManager
    private lateinit var textView: TextView

    private val binding get() = _binding!!

    override fun onAttach(context: Context) {
        super.onAttach(context)
        keyManager = PGPKeyManager(context)

        // Only generate keys if they do not exist
        val secretKeyFile = context.filesDir.resolve("secret_keyring.pgp")
        if (!secretKeyFile.exists()) {
            keyManager.generateAndSaveKeys()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val encryptViewModel =
            ViewModelProvider(this)[DecryptViewModel::class.java]

        _binding = FragmentDecryptBinding.inflate(inflater, container, false)
        val root: View = binding.root

        editTextMessage = root.findViewById(R.id.editTextCipher)
        textView = root.findViewById(R.id.textViewDecrypted)
        buttonDecrypt = root.findViewById(R.id.buttonDecrypt)

        buttonDecrypt.setOnClickListener {
            buttonDecrypt.isEnabled = false
            textView.text = getString(R.string.decrypting)
            val message = editTextMessage.text.toString()

            // Start decryption in a coroutine
            lifecycleScope.launch {
                textView.text = withContext(Dispatchers.IO) {
                    decryptText(message)
                }
            }
        }

        editTextMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                buttonDecrypt.isEnabled = true
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val encryptedMessage = arguments?.getString("encrypted_message") ?: ""
        if (encryptedMessage.isEmpty()) {
            textView.text = getString(R.string.waiting_for_encrypted_message)
            return root
        }

        buttonDecrypt.isEnabled = false
        editTextMessage.setText(encryptedMessage)
        textView.text = getString(R.string.decrypting)
        lifecycleScope.launch {
            textView.text = withContext(Dispatchers.IO) {
                decryptText(encryptedMessage)
            }
        }

        return root
    }

    private fun decryptText(encryptedText: String): String {
        return try {
            keyManager.decryptMessage(encryptedText)
        } catch (e: Exception) {
            "Decryption failed: ${e.message}"
        }
    }
}