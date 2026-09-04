package com.ngoline.easygpg

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import com.google.android.material.navigation.NavigationView
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.ngoline.easygpg.databinding.ActivityMainBinding
import kotlin.text.isNotEmpty
import kotlin.text.split
import androidx.biometric.BiometricManager
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.ngoline.easygpg.ui.DeviceAuth
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyPrivacyMode()

        // Register our BouncyCastle version
        installBouncyCastleProvider()

        // Nothing is shown until authentication succeeds.
        authenticateUser()
    }

    override fun onStart() {
        super.onStart()
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(this)
        applyPrivacyMode()
    }

    override fun onStop() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String?,
    ) {
        if (key == getString(R.string.privacy_mode)) {
            applyPrivacyMode()
        }
    }

    private fun applyPrivacyMode() {
        window.applyPrivacyMode(this)
    }

    /**
     * Authenticating here is also what authorises the Keystore key protecting the secret key rings,
     * so nothing is shown — and no key material is readable — until it succeeds.
     */
    private fun authenticateUser() {
        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            ) != BiometricManager.BIOMETRIC_SUCCESS
        ) {
            finish()
            return
        }
        lifecycleScope.launch {
            if (DeviceAuth.authenticate(this@MainActivity)) onAuthenticated() else finish()
        }
    }

    private fun onAuthenticated() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        // Use supportFragmentManager to get the NavController safely
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
        val navController = navHostFragment?.let { androidx.navigation.fragment.NavHostFragment.findNavController(it) }
        if (navController == null) {
            Log.e("MainActivity", "NavController not found on nav_host_fragment_content_main")
            return
        }
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_encrypt, R.id.nav_decrypt, R.id.nav_keys
            ), drawerLayout
        )
        // Composing a message is the app's primary action, and the envelope already says so.
        binding.appBarMain.fab.setOnClickListener {
            navController.navigate(R.id.nav_encrypt)
        }

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }

        if (isNotificationServiceEnabled(this)) {
            // Notification listener service is enabled
            Log.d("MainActivity", "Notification Service is enabled.")
        } else {
            // Notification listener service is not enabled
            Log.d("MainActivity", "Notification Service is not enabled.")
            launchDeviceNotificationOptions()
        }

        // Decrypt notification messages only after authentication.
        val encryptedMessage = intent.getStringExtra("encrypted_message")
        if (!encryptedMessage.isNullOrEmpty()) {
            val bundle = Bundle().apply {
                putString("encrypted_message", encryptedMessage)
            }
            navController.navigate(R.id.nav_decrypt, bundle)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
        val navController = navHostFragment?.let { androidx.navigation.fragment.NavHostFragment.findNavController(it) }
        return navController?.navigateUp(appBarConfiguration) ?: false || super.onSupportNavigateUp()
    }

    private fun launchDeviceNotificationOptions() {
        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        startActivity(intent)
    }

    private fun isNotificationServiceEnabled(context: Context): Boolean {
        val pkgName = context.packageName
        val flat =
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        if (flat != null && flat.isNotEmpty()) {
            val names = flat.split(":").toTypedArray()
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && cn.packageName == pkgName) {
                    return true
                }
            }
        }
        return false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
                val navController = navHostFragment?.let { androidx.navigation.fragment.NavHostFragment.findNavController(it) }
                navController?.navigate(R.id.nav_settings)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
