package com.ngoline.easygpg.ui.encrypt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.ngoline.easygpg.KeyItem
import com.ngoline.easygpg.PGPKeyManager
import com.ngoline.easygpg.R
import com.ngoline.easygpg.databinding.FragmentEncryptBinding
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.openpgp.PGPEncryptedData
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPLiteralDataGenerator
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator
import org.bouncycastle.util.encoders.Hex
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Date

class EncryptFragment : Fragment() {

    private var _binding: FragmentEncryptBinding? = null

    private lateinit var editTextMessage: EditText
    private lateinit var spinnerPublicKeys: Spinner
    private lateinit var buttonEncryptShare: Button
    private lateinit var keyManager: PGPKeyManager
    private lateinit var publicKeyList: List<PGPPublicKey>

    private val binding get() = _binding!!

    override fun onAttach(context: Context) {
        super.onAttach(context)
        keyManager = PGPKeyManager(context)

        keyManager.generateAndSaveKeys()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val encryptViewModel =
            ViewModelProvider(this)[EncryptViewModel::class.java]

        _binding = FragmentEncryptBinding.inflate(inflater, container, false)
        val root: View = binding.root

        editTextMessage = root.findViewById(R.id.editTextMessage)
        spinnerPublicKeys = root.findViewById(R.id.spinnerPublicKeys)
        buttonEncryptShare = root.findViewById(R.id.buttonEncryptShare)

        loadPublicKeys()

        buttonEncryptShare.setOnClickListener {
            val message = editTextMessage.text.toString()
            val selectedIndex = spinnerPublicKeys.selectedItemPosition
            if (selectedIndex != -1 && selectedIndex < publicKeyList.size) {
                val selectedKey = publicKeyList[selectedIndex]  // Get the public key using the selected index
                val encryptedMessage = encryptMessage(message, selectedKey)
                shareEncryptedMessage(encryptedMessage)
            } else {
                Toast.makeText(requireContext(), "No public key selected", Toast.LENGTH_SHORT).show()
            }
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadPublicKeys() {
        val keyItems: MutableList<KeyItem> = keyManager.getAllPublicKeys()

        val aliasesAndKeys: List<Pair<String, PGPPublicKey>> = keyItems.map { keyItem ->
            val shortFingerprint = Hex.toHexString(keyItem.publicKey.fingerprint).substring(0, 16)
            "${keyItem.alias} ($shortFingerprint)" to keyItem.publicKey
        }

        publicKeyList = aliasesAndKeys.map { it.second }

        // Create an ArrayAdapter for the spinner. Specify the type explicitly for ArrayAdapter.
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, aliasesAndKeys.map { it.first })
        spinnerPublicKeys.adapter = adapter
    }

    private fun encryptMessage(message: String, publicKey: PGPPublicKey): String {
        try {
            val encryptedData = ByteArrayOutputStream()
            val armorStream = ArmoredOutputStream(encryptedData)

            val encGen = PGPEncryptedDataGenerator(
                JcePGPDataEncryptorBuilder(PGPEncryptedData.CAST5)
                    .setWithIntegrityPacket(true)
                    .setSecureRandom(SecureRandom())
                    .setProvider("BC")
            )

            val keyEncryptionMethodGenerator = JcePublicKeyKeyEncryptionMethodGenerator(publicKey).setProvider("BC")
            encGen.addMethod(keyEncryptionMethodGenerator)

            val encOut = encGen.open(armorStream, ByteArray(0))
            val lData = PGPLiteralDataGenerator()
            val pOut = lData.open(encOut, PGPLiteralData.BINARY, "filename", message.toByteArray().size.toLong(), Date())
            pOut.write(message.toByteArray())
            pOut.close()

            encOut.close()
            armorStream.close()

            return String(encryptedData.toByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
            return "Encryption failed"
        }
    }

    private fun shareEncryptedMessage(encryptedMessage: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, encryptedMessage)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, "Share via"))
    }
}