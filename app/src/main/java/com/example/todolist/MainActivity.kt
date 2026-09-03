package com.example.todolist

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.example.todolist.data.model.Role
import com.example.todolist.databinding.ActivityMainBinding
import com.example.todolist.ui.common.NotificationsDialog
import com.example.todolist.ui.common.navAnimOptions
import com.example.todolist.util.ThemeManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var themeManager: ThemeManager

    /** Fix #7: tracks keyboard visibility so the tab bar can hide/reappear. */
    private var isKeyboardVisible = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* handled on next launch */ }

    private val adminTopLevel = setOf(
        R.id.adminTasksFragment, R.id.adminBatchesFragment, R.id.adminProgressFragment
    )
    private val userTopLevel = setOf(
        R.id.userBoardFragment, R.id.userPersonalFragment, R.id.userProgressFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fix #1: fade instead of a hard black flash when the activity is recreated
        // for the dark/light switch (windowBackground matches the target theme too).
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        themeManager = (application as TaskApp).container.themeManager

        val navHost = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
        navController = navHost.navController

        setSupportActionBar(binding.toolbar)
        setupDrawer()

        // Fix #7: hide the tab bar whenever the keyboard opens.
        setupKeyboardListener()

        binding.bottomNav.setOnItemSelectedListener { item ->
            // Navigation 2.10: use the explicit ID-based overload. The generic
            // navigate(route) builder APIs treat the argument as a route string,
            // which would crash with "Destination with route Int cannot be found".
            navController.navigate(
                item.itemId,
                null,
                navAnimOptions {
                    setLaunchSingleTop(true)
                    setRestoreState(true)
                    setPopUpTo(navController.graph.startDestinationId, false, true)
                },
                null
            )
            true
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            onDestinationChanged(destination)
        }

        requestNotificationPermissionIfNeeded()

        // Fix #12: returning users skip the Welcome screen and go straight home.
        val session = (application as TaskApp).container.sessionManager
        if (session.isLoggedIn) {
            when (session.role) {
                Role.ADMIN -> navigateTo(R.id.adminTasksFragment, clearBackStack = true)
                Role.USER -> navigateTo(R.id.userBoardFragment, clearBackStack = true)
                null -> Unit
            }
        }
    }
    /** Fix #8: burger menu (top-left) opens a drawer with theme / notifications / logout. */
    private fun setupDrawer() {
        binding.toolbar.navigationIcon = ContextCompat.getDrawable(this, R.drawable.ic_menu)
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_theme -> {
                    themeManager.setDarkMode(!themeManager.isDarkMode())
                    updateDrawerState()
                }
                R.id.nav_notifications -> {
                    val userId = (application as TaskApp).container.sessionManager.userId
                    if (userId > 0) {
                        NotificationsDialog().show(supportFragmentManager, "notifications")
                    }
                }
                R.id.nav_logout -> {
                    (application as TaskApp).container.sessionManager.clear()
                    navController.navigate(
                        R.id.welcomeFragment,
                        null,
                        navAnimOptions { setPopUpTo(navController.graph.startDestinationId, true) },
                        null
                    )
                }
            }
            binding.drawerLayout.closeDrawer(Gravity.START)
            true
        }

        // Keep drawer item visibility/icons fresh whenever it opens.
        binding.drawerLayout.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: android.view.View) {
                updateDrawerState()
            }
        })
        updateDrawerState()
    }

    private fun updateDrawerState() {
        val session = (application as TaskApp).container.sessionManager
        val dest = navController.currentDestination
        val loggedIn = session.isLoggedIn

        val notificationsItem = binding.navigationView.menu.findItem(R.id.nav_notifications)
        notificationsItem.isVisible = loggedIn && session.role == Role.USER && dest?.id in userTopLevel
        binding.navigationView.menu.findItem(R.id.nav_logout).isVisible = loggedIn

        val themeItem = binding.navigationView.menu.findItem(R.id.nav_theme)
        val dark = themeManager.isDarkMode()
        themeItem.setIcon(if (dark) R.drawable.ic_light_mode else R.drawable.ic_dark_mode)
        themeItem.title = if (dark) "Light mode" else "Dark mode"
    }

    /** Fix #7: hide the bottom tab bar while the soft keyboard is up. */
    private fun setupKeyboardListener() {
        val root = binding.root
        root.viewTreeObserver.addOnGlobalLayoutListener {
            val visibleFrame = Rect()
            root.getWindowVisibleDisplayFrame(visibleFrame)
            val screenHeight = root.rootView.height
            val covered = screenHeight - visibleFrame.bottom
            isKeyboardVisible = covered > screenHeight / 4
            updateBottomNavVisibility()
        }
    }

    private fun updateBottomNavVisibility() {
        val dest = navController.currentDestination
        val onTopLevel = dest?.id in adminTopLevel || dest?.id in userTopLevel
        binding.bottomNav.isVisible = onTopLevel && !isKeyboardVisible
    }

    private fun onDestinationChanged(destination: NavDestination) {
        // Full-screen views (Welcome + Sign in): no toolbar/title bar, no
        // hamburger, and the drawer is locked closed. These screens carry their
        // own actions (light/dark toggle top-right) instead of the drawer.
        val isFullscreen = destination.id == R.id.welcomeFragment || destination.id == R.id.loginFragment

        binding.toolbar.isVisible = !isFullscreen
        binding.toolbar.title = if (isFullscreen) "" else destination.label
        binding.toolbar.navigationIcon =
            if (isFullscreen) null else ContextCompat.getDrawable(this, R.drawable.ic_menu)
        binding.drawerLayout.setDrawerLockMode(
            if (isFullscreen) {
                androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED
            } else {
                androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED
            }
        )

        // Bottom nav: visible only on top-level home screens (hidden while keyboard is up).
        val showBottomNav = destination.id in adminTopLevel || destination.id in userTopLevel
        binding.bottomNav.isVisible = showBottomNav && !isKeyboardVisible

        if (showBottomNav) {
            val isAdmin = (application as TaskApp).container.sessionManager.role == Role.ADMIN
            val res = if (isAdmin) R.menu.menu_bottom_nav_admin else R.menu.menu_bottom_nav_user
            if (binding.bottomNav.tag != res) {
                binding.bottomNav.menu.clear()
                binding.bottomNav.inflateMenu(res)
                binding.bottomNav.tag = res
            }
            binding.bottomNav.menu.findItem(destination.id)?.isChecked = true
        }

        updateDrawerState()
    }

    fun navigateTo(destinationId: Int, clearBackStack: Boolean = false) {
        navController.navigate(
            destinationId,
            null,
            navAnimOptions {
                setLaunchSingleTop(true)
                if (clearBackStack) {
                    setPopUpTo(navController.graph.startDestinationId, true)
                }
            },
            null
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
