package com.simple.elderlylauncher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.simple.elderlylauncher.adapter.AppListAdapter
import com.simple.elderlylauncher.databinding.ActivityAppDrawerBinding
import com.simple.elderlylauncher.model.AppInfo
import com.simple.elderlylauncher.util.AppRepository
import com.simple.elderlylauncher.util.AppUsageTracker
import com.simple.elderlylauncher.util.FavoriteAppsManager
import com.simple.elderlylauncher.util.LocaleHelper
import com.simple.elderlylauncher.util.PerformanceMonitor
import kotlinx.coroutines.launch

class AppDrawerActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private lateinit var binding: ActivityAppDrawerBinding
    private lateinit var appAdapter: AppListAdapter
    private lateinit var favoriteAppsManager: FavoriteAppsManager
    private lateinit var usageTracker: AppUsageTracker
    private var allApps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        PerformanceMonitor.markDrawerOpenStart()

        super.onCreate(savedInstanceState)
        binding = ActivityAppDrawerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        favoriteAppsManager = FavoriteAppsManager(this)
        usageTracker = AppUsageTracker(this)

        updateBackgroundForTimeOfDay()
        setupAppList()
        setupBackButton()
        setupSearch()
        loadApps()
    }

    private fun setupAppList() {
        appAdapter = AppListAdapter(
            onAppClick = { appInfo ->
                launchApp(appInfo.packageName)
            },
            onAddToHome = { appInfo ->
                addToHome(appInfo)
            },
            isFavorite = { packageName ->
                favoriteAppsManager.isFavorite(packageName)
            }
        )

        binding.appListRecycler.apply {
            layoutManager = GridLayoutManager(this@AppDrawerActivity, 3)
            adapter = appAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(20)
        }
    }

    private fun addToHome(appInfo: AppInfo) {
        if (favoriteAppsManager.addFavoriteApp(appInfo.packageName)) {
            Toast.makeText(this, R.string.app_added, Toast.LENGTH_SHORT).show()
            appAdapter.notifyDataSetChanged()
        } else if (favoriteAppsManager.isFavorite(appInfo.packageName)) {
            // Already a favorite - do nothing
        } else {
            Toast.makeText(this, R.string.max_apps_reached, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            finish()
        }
    }

    private fun setupSearch() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterApps(s?.toString() ?: "")
            }
        })
    }

    private fun filterApps(query: String) {
        val filtered = if (query.isEmpty()) {
            allApps
        } else {
            allApps.filter { app ->
                app.name.contains(query, ignoreCase = true)
            }
        }
        appAdapter.submitList(filtered)
    }

    private fun loadApps() {
        lifecycleScope.launch {
            val cachedApps = AppRepository.getApps(applicationContext)

            if (cachedApps.isEmpty()) {
                binding.loadingIndicator.visibility = View.VISIBLE
                binding.appListRecycler.visibility = View.GONE
            }

            val apps = AppRepository.getApps(applicationContext)

            // Sort apps: frequently/recently used first, then alphabetically
            allApps = sortAppsByUsage(apps)

            binding.loadingIndicator.visibility = View.GONE
            binding.appListRecycler.visibility = View.VISIBLE
            appAdapter.submitList(allApps)

            // Mark drawer load complete
            PerformanceMonitor.markDrawerOpenComplete()
        }
    }

    /**
     * Sort apps with smart ordering:
     * - Apps with usage data sorted by usage score (descending)
     * - Apps without usage data sorted alphabetically
     */
    private fun sortAppsByUsage(apps: List<AppInfo>): List<AppInfo> {
        return apps.sortedWith { a, b ->
            val scoreA = usageTracker.getUsageScore(a.packageName)
            val scoreB = usageTracker.getUsageScore(b.packageName)

            when {
                // Both have usage - sort by score descending
                scoreA > 0 && scoreB > 0 -> scoreB.compareTo(scoreA)
                // Only A has usage - A comes first
                scoreA > 0 -> -1
                // Only B has usage - B comes first
                scoreB > 0 -> 1
                // Neither has usage - sort alphabetically
                else -> a.name.lowercase().compareTo(b.name.lowercase())
            }
        }
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            PerformanceMonitor.markAppLaunchStart()
            usageTracker.recordLaunch(packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            PerformanceMonitor.markAppLaunchComplete()
        }
    }

    private fun updateBackgroundForTimeOfDay() {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

        val backgroundRes = when (hour) {
            in 6..11 -> R.drawable.bg_morning      // 6 AM - 11:59 AM
            in 12..16 -> R.drawable.bg_afternoon   // 12 PM - 4:59 PM
            in 17..19 -> R.drawable.bg_evening     // 5 PM - 7:59 PM
            else -> R.drawable.bg_night            // 8 PM - 5:59 AM
        }

        binding.root.setBackgroundResource(backgroundRes)
    }
}
