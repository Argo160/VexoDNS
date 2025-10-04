package com.example.vexodns

import android.content.Context
import android.os.Bundle
import android.view.Menu
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.vexodns.databinding.ActivityMainBinding
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.navigation.ui.NavigationUI
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPref = getSharedPreferences("VexoDNSPrefs", MODE_PRIVATE)

        // *** کد ریست کردن حذف شد! ***
        // دیگر is_connected_state را به false تنظیم نمی‌کنیم

        // زبان ذخیره شده را می‌خوانیم و تنظیم می‌کنیم
        val langCode = sharedPref.getString("app_language", null)
        if (langCode != null) {
            val localeList = LocaleListCompat.forLanguageTags(langCode)
            AppCompatDelegate.setApplicationLocales(localeList)
        }

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)
        supportActionBar?.title = ""

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.nav_home), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setNavigationItemSelectedListener { menuItem ->
            binding.drawerLayout.closeDrawers()
            when (menuItem.itemId) {
                R.id.nav_sub -> {
                    showAddLinkDialog()
                    true
                }
                R.id.nav_language -> {
                    showLanguageSelectionDialog()
                    true
                }
                else -> NavigationUI.onNavDestinationSelected(menuItem, navController)
            }
        }

        // درخواست دسترسی نوتیفیکیشن
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(permission), 0)
            }
        }
    }

    fun setDrawerEnabled(enabled: Boolean) {
        val lockMode = if (enabled) {
            DrawerLayout.LOCK_MODE_UNLOCKED
        } else {
            DrawerLayout.LOCK_MODE_LOCKED_CLOSED
        }
        binding.drawerLayout.setDrawerLockMode(lockMode)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_toolbar_menu, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun showLanguageSelectionDialog() {
        val languages = arrayOf("English", "فارسی", "Русский", "中文")
        val languageCodes = arrayOf("en", "fa", "ru", "zh")

        val sharedPref = getSharedPreferences("VexoDNSPrefs", MODE_PRIVATE)
        val currentLangCode = sharedPref.getString("app_language", "en")
        var checkedItem = languageCodes.indexOf(currentLangCode)
        if (checkedItem == -1) {
            checkedItem = 0
        }

        var selectedLangCode = languageCodes[checkedItem]

        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.select_language_title))

        builder.setSingleChoiceItems(languages, checkedItem) { dialog, which ->
            selectedLangCode = languageCodes[which]
        }

        builder.setPositiveButton(android.R.string.ok) { dialog, which ->
            if (selectedLangCode != currentLangCode) {
                setAppLocale(selectedLangCode)
            }
            dialog.dismiss()
        }
        builder.setNegativeButton(android.R.string.cancel) { dialog, which ->
            dialog.dismiss()
        }

        builder.create().show()
    }

    private fun setAppLocale(languageCode: String) {
        val sharedPref = getSharedPreferences("VexoDNSPrefs", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("app_language", languageCode)
            apply()
        }

        val localeList = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    private fun showAddLinkDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_link, null)
        val editTextLink = dialogView.findViewById<EditText>(R.id.edit_text_link)

        val sharedPref = getSharedPreferences("VexoDNSPrefs", MODE_PRIVATE)
        val savedLink = sharedPref.getString("subscription_link", null)

        if (!savedLink.isNullOrBlank()) {
            editTextLink.setText(savedLink)
        }

        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        builder.setTitle(R.string.url_label)
        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            val link = editTextLink.text.toString()
            if (link.isNotBlank()) {
                val sharedPref = getSharedPreferences("VexoDNSPrefs", MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putString("subscription_link", link)
                    apply()
                }
                Toast.makeText(this, getString(R.string.link_saved_success), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.link_cannot_be_empty), Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton(android.R.string.cancel) { dialog, _ ->
            dialog.cancel()
        }

        builder.create().show()
    }
}