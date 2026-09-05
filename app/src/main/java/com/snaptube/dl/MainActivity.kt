package com.snaptube.dl

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.snaptube.dl.databinding.ActivityMainBinding
import com.snaptube.dl.ui.browser.BrowserFragment
import com.snaptube.dl.ui.downloads.DownloadsFragment
import com.snaptube.dl.ui.home.HomeFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val homeFragment = HomeFragment()
    private val browserFragment = BrowserFragment()
    private val downloadsFragment = DownloadsFragment()
    private var activeFragment: Fragment = homeFragment

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled
    }

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
            add(R.id.fragment_container, downloadsFragment, "DOWNLOADS").hide(downloadsFragment)
            add(R.id.fragment_container, browserFragment, "BROWSER").hide(browserFragment)
            add(R.id.fragment_container, homeFragment, "HOME")
        }.commit()
        activeFragment = homeFragment
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    switchFragment(homeFragment)
                    true
                }
                R.id.nav_browser -> {
                    switchFragment(browserFragment)
                    true
                }
                R.id.nav_downloads -> {
                    switchFragment(downloadsFragment)
                    true
                }
                else -> false
            }
        }
    }

    private fun switchFragment(target: Fragment) {
        if (target != activeFragment) {
            supportFragmentManager.beginTransaction()
                .hide(activeFragment)
                .show(target)
                .commit()
            activeFragment = target
        }
    }

    fun navigateToHome() {
        binding.bottomNavigation.selectedItemId = R.id.nav_home
    }

    fun navigateToBrowser(url: String) {
        browserFragment.loadUrl(url)
        binding.bottomNavigation.selectedItemId = R.id.nav_browser
    }

    fun navigateToDownloads() {
        binding.bottomNavigation.selectedItemId = R.id.nav_downloads
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val url = extractUrl(sharedText)
            if (url != null) {
                navigateToHome()
                homeFragment.setUrlAndAnalyze(url)
            }
        }
    }

    private fun extractUrl(text: String): String? {
        val urlRegex = Regex("""https?://[^\s]+""")
        return urlRegex.find(text)?.value
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
