package org.bataydar.evidence.ui.landlord

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import org.bataydar.evidence.BataydarApp
import org.bataydar.evidence.R
import org.bataydar.evidence.databinding.ActivityLandlordDashboardBinding
import org.bataydar.evidence.ui.auth.LoginActivity

class LandlordDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLandlordDashboardBinding
    private val tokenManager by lazy { BataydarApp.instance.tokenManager }

    private val homeFragment = LandlordHomeFragment()
    private val requestsFragment = LandlordRequestsFragment()
    private val historyFragment = LandlordHistoryFragment()
    private val profileFragment = LandlordProfileFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Strict role validation: Ensure user is logged in as LANDLORD
        if (!tokenManager.isLoggedIn() || !tokenManager.isLandlord()) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        binding = ActivityLandlordDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupHeader()
        setupBottomNavigation()

        if (savedInstanceState == null) {
            loadFragment(homeFragment)
        }
    }

    private fun setupHeader() {
        val landlordName = tokenManager.getUserName() ?: "Landowner"
        binding.tvLandlordIdentity.text = landlordName
    }

    private fun setupBottomNavigation() {
        binding.bottomNavLandlord.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_landlord_dashboard -> {
                    loadFragment(homeFragment)
                    true
                }
                R.id.nav_landlord_requests -> {
                    loadFragment(requestsFragment)
                    true
                }
                R.id.nav_landlord_history -> {
                    loadFragment(historyFragment)
                    true
                }
                R.id.nav_landlord_profile -> {
                    loadFragment(profileFragment)
                    true
                }
                else -> false
            }
        }
    }

    fun switchToRequestsTab() {
        binding.bottomNavLandlord.selectedItemId = R.id.nav_landlord_requests
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.landlordFragmentContainer, fragment)
            .commit()
    }
}

