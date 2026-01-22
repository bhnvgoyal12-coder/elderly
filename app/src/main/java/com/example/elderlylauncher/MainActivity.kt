package com.example.elderlylauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.elderlylauncher.adapter.FavoriteContactAdapter
import com.example.elderlylauncher.databinding.ActivityMainBinding
import com.example.elderlylauncher.util.AppLauncher

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: LauncherViewModel by viewModels()
    private lateinit var appLauncher: AppLauncher
    private lateinit var contactAdapter: FavoriteContactAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.loadFavoriteContacts(contentResolver)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appLauncher = AppLauncher(this)

        setupFavoriteContacts()
        setupMainButtons()
        setupBackButton()
        checkPermissionsAndLoad()
    }

    private fun setupFavoriteContacts() {
        contactAdapter = FavoriteContactAdapter { contact ->
            // Direct dial when tapping contact
            appLauncher.dialNumber(contact.phoneNumber)
        }

        binding.favoriteContactsRecycler.apply {
            layoutManager = LinearLayoutManager(
                this@MainActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = contactAdapter
        }

        viewModel.favoriteContacts.observe(this) { contacts ->
            contactAdapter.submitList(contacts)
        }
    }

    private fun setupMainButtons() {
        binding.btnPhone.setOnClickListener {
            appLauncher.openDialer()
        }

        binding.btnMessages.setOnClickListener {
            appLauncher.openMessages()
        }

        binding.btnWhatsApp.setOnClickListener {
            appLauncher.openWhatsApp()
        }

        binding.btnCamera.setOnClickListener {
            appLauncher.openCamera()
        }

        binding.btnPhotos.setOnClickListener {
            appLauncher.openGallery()
        }

        binding.btnAllApps.setOnClickListener {
            startActivity(Intent(this, AppDrawerActivity::class.java))
        }
    }

    private fun setupBackButton() {
        // Do nothing on back press - we're on the home screen
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intentionally empty - prevents accidental app closure
            }
        })
    }

    private fun checkPermissionsAndLoad() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED -> {
                viewModel.loadFavoriteContacts(contentResolver)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }
}
