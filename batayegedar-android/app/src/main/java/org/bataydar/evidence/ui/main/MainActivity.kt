package org.bataydar.evidence.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import org.bataydar.evidence.R
import org.bataydar.evidence.databinding.ActivityMainBinding
import org.bataydar.evidence.ui.profile.ProfileFragment
import org.bataydar.evidence.ui.records.AddEditRecordActivity
import org.bataydar.evidence.ui.records.RecordsListFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val homeFragment = HomeFragment()
    private val recordsListFragment = RecordsListFragment()
    private val profileFragment = ProfileFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Strict role validation: Landlord users must not enter Bataydar dashboard
        val tokenManager = org.bataydar.evidence.BataydarApp.instance.tokenManager
        if (tokenManager.isLoggedIn() && tokenManager.isLandlord()) {
            val intent = Intent(this, org.bataydar.evidence.ui.landlord.LandlordDashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            loadFragment(homeFragment)
        }

        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(homeFragment)
                    true
                }
                R.id.nav_records -> {
                    loadFragment(recordsListFragment)
                    true
                }
                R.id.nav_add -> {
                    startActivity(Intent(this, AddEditRecordActivity::class.java))
                    false // Don't highlight 'Add' tab; it's an action button
                }
                R.id.nav_profile -> {
                    loadFragment(profileFragment)
                    true
                }
                else -> false
            }
        }
    }

    fun switchToRecordsTab() {
        binding.bottomNav.selectedItemId = R.id.nav_records
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
