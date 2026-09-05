package com.snaptube.dl

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.snaptube.dl.databinding.ActivityMainBinding
import com.snaptube.dl.engine.DownloadManager
import com.snaptube.dl.ui.browser.BrowserFragment
import com.snaptube.dl.ui.dialogs.FormatBottomSheetDialog
import com.snaptube.dl.ui.downloads.DownloadsFragment
import com.snaptube.dl.ui.home.HomeFragment
import com.snaptube.dl.ui.settings.SettingsFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val homeFragment = HomeFragment()
    private val downloadsFragment = DownloadsFragment()
    private val settingsFragment = SettingsFragment()
    private var browserFragment: BrowserFragment? = null
    private var activeFragment: Fragment = homeFragment

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFragments()
        setupBottomNavigation()
        checkAndRequestPermissions()

        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    private fun setupFragments() {
        supportFragmentManager.beginTransaction().apply {
            add(R.id.fragment_container, settingsFragment, "SETTINGS").hide(settingsFragment)
            add(R.id.fragment_container, downloadsFragment, "PLAY").hide(downloadsFragment)
            add(R.id.fragment_container, homeFragment, "HOME")
        }.commit()
        activeFragment = homeFragment
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_download -> {
                    switchFragment(homeFragment)
                    true
                }
                R.id.nav_play -> {
                    switchFragment(downloadsFragment)
                    true
                }
                R.id.nav_settings -> {
                    switchFragment(settingsFragment)
                    true
                }
                else -> false
            }
        }
    }

    private fun switchFragment(target: Fragment) {
        if (target != activeFragment) {
            val transaction = supportFragmentManager.beginTransaction()
            if (!target.isAdded) {
                transaction.add(R.id.fragment_container, target)
            }
            transaction.hide(activeFragment).show(target).commit()
            activeFragment = target
        }
    }

    fun navigateToHome() {
        binding.bottomNavigation.selectedItemId = R.id.nav_download
    }

    fun navigateToDownloads() {
        binding.bottomNavigation.selectedItemId = R.id.nav_play
    }

    fun navigateToBrowser(url: String) {
        if (browserFragment == null) {
            browserFragment = BrowserFragment.newInstance(url)
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, browserFragment!!, "BROWSER")
                .hide(activeFragment)
                .show(browserFragment!!)
                .commit()
            activeFragment = browserFragment!!
        } else {
            browserFragment?.loadUrl(url)
            switchFragment(browserFragment!!)
        }
    }

    /**
     * Automatic Share-to-Download feature:
     * When any video link is shared from YouTube, Instagram, TikTok, Facebook to SnapBox,
     * immediately extract and popup the format bottom sheet dialogue without requiring manual typing!
     */
    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val url = extractUrl(sharedText)
            if (url != null) {
                triggerAutoShareDownload(url)
            }
        }
    }

    private fun triggerAutoShareDownload(url: String) {
        // Show stylish Snaptube extraction bottom sheet
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_share_extracting, null, false)
        val tvUrl = view.findViewById<TextView>(R.id.tv_share_url)
        tvUrl.text = url
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.show()

        lifecycleScope.launch {
            val result = DownloadManager.extractMetadata(url)
            if (dialog.isShowing) {
                dialog.dismiss()
            }

            result.onSuccess { metadata ->
                val formatSheet = FormatBottomSheetDialog(metadata) {
                    navigateToDownloads()
                }
                formatSheet.show(supportFragmentManager, FormatBottomSheetDialog.TAG)
            }.onFailure { _ ->
                Toast.makeText(
                    this@MainActivity,
                    "Opening video in browser...",
                    Toast.LENGTH_SHORT
                ).show()
                navigateToBrowser(url)
            }
        }
    }

    override fun onBackPressed() {
        if (activeFragment == browserFragment && browserFragment?.canGoBack() == true) {
            browserFragment?.goBack()
        } else if (activeFragment != homeFragment) {
            navigateToHome()
        } else {
            super.onBackPressed()
        }
    }

    private fun extractUrl(text: String): String? {
        val urlRegex = Regex("""https?://[^\s]+""")
        val match = urlRegex.find(text)
        return match?.value?.trim()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
}