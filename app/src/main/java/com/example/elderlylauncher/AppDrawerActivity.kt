package com.example.elderlylauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.elderlylauncher.adapter.AppListAdapter
import com.example.elderlylauncher.databinding.ActivityAppDrawerBinding
import com.example.elderlylauncher.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppDrawerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppDrawerBinding
    private lateinit var appAdapter: AppListAdapter
    private var allApps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppDrawerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAppList()
        setupBackButton()
        setupSearch()
        loadApps()
    }

    private fun setupAppList() {
        appAdapter = AppListAdapter { appInfo ->
            launchApp(appInfo.packageName)
        }

        binding.appListRecycler.apply {
            layoutManager = GridLayoutManager(this@AppDrawerActivity, 3)
            adapter = appAdapter
        }
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
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
            val apps = withContext(Dispatchers.IO) {
                getInstalledApps()
            }
            allApps = apps
            appAdapter.submitList(apps)
        }
    }

    private fun getInstalledApps(): List<AppInfo> {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = pm.queryIntentActivities(mainIntent, PackageManager.GET_META_DATA)
            .mapNotNull { resolveInfo ->
                try {
                    val appName = resolveInfo.loadLabel(pm).toString()
                    val packageName = resolveInfo.activityInfo.packageName
                    val icon = resolveInfo.loadIcon(pm)

                    // Skip our own app
                    if (packageName == this.packageName) {
                        return@mapNotNull null
                    }

                    AppInfo(
                        name = appName,
                        packageName = packageName,
                        icon = icon
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .sortedBy { it.name.lowercase() }

        return apps
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }
}
