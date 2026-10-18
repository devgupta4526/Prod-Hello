package com.example.romear

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.romear.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom = if (binding.bottomNavigation.isVisible) 0 else bars.bottom
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bottom, ime.bottom))
            insets
        }
        val host = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val controller = host.navController
        binding.bottomNavigation.setupWithNavController(controller)
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val changed = binding.bottomNavigation.selectedItemId != item.itemId
            val handled = androidx.navigation.ui.NavigationUI.onNavDestinationSelected(item, controller)
            if (handled && changed) com.example.romear.core.designsystem.HelloHaptics.selection(binding.bottomNavigation)
            handled
        }
        val back = onBackPressedDispatcher.addCallback(this, enabled = false) {
            if (!controller.popBackStack(R.id.homeFragment, false)) {
                controller.navigate(R.id.discover_graph, null, androidx.navigation.NavOptions.Builder()
                    .setPopUpTo(R.id.main_navigation, false)
                    .setLaunchSingleTop(true)
                    .build())
            }
        }
        controller.addOnDestinationChangedListener { _, destination, _ ->
            val auth = destination.id == R.id.welcomeFragment || destination.id == R.id.authFragment
            val hideBottomNav = auth || destination.id == R.id.chatFragment
            binding.bottomNavigation.isVisible = !hideBottomNav
            ViewCompat.requestApplyInsets(binding.root)
            back.isEnabled = !hideBottomNav && destination.id != R.id.homeFragment
        }
    }
}
