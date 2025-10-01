package com.example.vexodns

import android.os.Bundle
import android.view.Menu
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.vexodns.databinding.ActivityMainBinding
import android.content.Context
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.navigation.ui.NavigationUI

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)
        supportActionBar?.title = ""
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
//navView.setupWithNavController(navController)
        navView.setNavigationItemSelectedListener { menuItem ->
            // Close the navigation drawer
            binding.drawerLayout.closeDrawers()

            when (menuItem.itemId) {
                R.id.nav_sub -> { // <-- Use the ID of your subscription item
                    // Show our custom dialog
                    showAddLinkDialog()
                    true // Mark as handled
                }
                else -> {
                    // Let the Navigation Component handle other items
                    NavigationUI.onNavDestinationSelected(menuItem, navController)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main_toolbar_menu, menu)
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
    private fun showAddLinkDialog() {
        // Inflate the custom layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_link, null)
        val editTextLink = dialogView.findViewById<EditText>(R.id.edit_text_link)

        // Build the dialog
        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        builder.setTitle("افزودن لینک اشتراک")
        builder.setPositiveButton("اوکی") { _, _ ->
            val link = editTextLink.text.toString()
            if (link.isNotBlank()) {
                // Save the link using SharedPreferences
                val sharedPref = getSharedPreferences("VexoDNSPrefs", Context.MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putString("subscription_link", link)
                    apply()
                }
                Toast.makeText(this, "لینک ذخیره شد", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "لینک نمی‌تواند خالی باشد", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("لغو") { dialog, _ ->
            dialog.cancel()
        }

        // Show the dialog
        builder.create().show()
    }
}