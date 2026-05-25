package com.simple.elderlylauncher

import android.Manifest
import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.simple.elderlylauncher.adapter.FavoriteAppsAdapter
import com.simple.elderlylauncher.adapter.FavoriteContactAdapter
import com.simple.elderlylauncher.adapter.HomePagerAdapter
import com.simple.elderlylauncher.model.AppInfo
import com.simple.elderlylauncher.util.AppLauncher
import com.simple.elderlylauncher.util.AppRepository
import com.simple.elderlylauncher.util.AppUsageTracker
import com.simple.elderlylauncher.util.FavoriteAppsManager
import com.simple.elderlylauncher.util.NewsRepository
import com.simple.elderlylauncher.util.PerformanceMonitor
import com.simple.elderlylauncher.util.QuoteProvider
import com.simple.elderlylauncher.util.LocaleHelper
import com.simple.elderlylauncher.util.SosManager
import com.simple.elderlylauncher.util.OnThisDayLoader
import android.provider.ContactsContract
import android.os.CountDownTimer
import android.widget.FrameLayout
import com.simple.elderlylauncher.service.NotificationService
import android.provider.Settings as AndroidSettings
import android.content.ComponentName
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.button.MaterialButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), NotificationService.NotificationCountListener {

    companion object {
        private const val SWIPE_THRESHOLD = 180
        private const val SWIPE_VELOCITY_THRESHOLD = 200
        private const val TOP_REGION_HEIGHT_DP = 250
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    // Extension function for haptic feedback
    private fun View.vibrate() = performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

    private lateinit var viewPager: ViewPager2
    private val viewModel: LauncherViewModel by viewModels()
    private lateinit var appLauncher: AppLauncher
    private var contactAdapter: FavoriteContactAdapter? = null
    private var favoriteAppsAdapter: FavoriteAppsAdapter? = null
    private lateinit var favoriteAppsManager: FavoriteAppsManager
    private lateinit var usageTracker: AppUsageTracker
    private lateinit var sosManager: SosManager
    private var sosSubtitleView: TextView? = null
    private var entertainmentView: View? = null

    // Essentials page views
    private var batteryPercentText: TextView? = null
    private var batteryStatusText: TextView? = null
    private var batteryIcon: ImageView? = null
    private var lowBatteryWarning: View? = null
    private var isFlashlightOn = false
    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null

    // Home page views
    private var homeView: View? = null
    private var rootLayout: View? = null
    private var notificationIndicator: View? = null
    private var notificationText: TextView? = null

    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private var currentQuoteText: String = ""
    private var isBatteryReceiverRegistered = false

    // Debug overlay views
    private var debugOverlay: View? = null
    private var debugStartupText: TextView? = null
    private var debugFpsText: TextView? = null
    private var debugMemoryText: TextView? = null
    private var debugDrawerText: TextView? = null
    private var debugAppLaunchText: TextView? = null
    private var isFirstCreate = true

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateBatteryStatus(intent)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.loadFavoriteContacts(contentResolver)
        }
    }

    private val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val query = matches[0]
                performWebSearch(query)
            }
        }
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startVoiceSearch()
        } else {
            Toast.makeText(this, R.string.voice_not_available, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Contact picker for the SOS emergency contact. Returns a Phone-row URI which we
     * query for display name + number. On Android 11+ this works without any extra
     * permission because the picker is a system component.
     */
    private val photosPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Re-run the loader now that we can read MediaStore
            entertainmentView?.let { loadOnThisDay(it) }
        }
    }

    private val sosContactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val uri = result.data?.data ?: return@registerForActivityResult
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
            val number = if (numIdx >= 0) cursor.getString(numIdx) else null
            if (number.isNullOrBlank()) {
                Toast.makeText(this, R.string.sos_no_number, Toast.LENGTH_SHORT).show()
                return@use
            }
            sosManager.set(name.orEmpty(), number)
            refreshSosSubtitle()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Mark cold start timing
        if (isFirstCreate) {
            PerformanceMonitor.markColdStart()
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        rootLayout = findViewById(R.id.rootLayout)
        applySystemBarInsets(viewPager)
        appLauncher = AppLauncher(this)
        favoriteAppsManager = FavoriteAppsManager(this)
        // First-run only: pre-add YouTube (or any other default) if installed
        favoriteAppsManager.seedDefaultFavoritesIfNeeded(this)
        usageTracker = AppUsageTracker(this)
        sosManager = SosManager(this)

        // Set time-based background
        updateBackgroundForTimeOfDay()

        // Setup debug overlay
        setupDebugOverlay()

        // Initialize camera manager for flashlight
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            cameraId = cameraManager?.cameraIdList?.firstOrNull()
        } catch (e: Exception) {
            // Camera not available
        }

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.getDefault())
                isTtsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                             result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }

        lifecycleScope.launch {
            AppRepository.preloadApps(applicationContext)
        }

        setupViewPager()
        setupBackButton()
        checkFirstLaunch()
        checkNotificationAccessAfterDelay()
    }

    /**
     * Apply system bar insets (status bar + nav bar + display cutout) as padding.
     * Lets the background draw edge-to-edge while keeping content out from under system UI.
     * Re-applies on configuration changes (rotation, gesture nav toggle, etc.).
     */
    private fun applySystemBarInsets(target: View) {
        ViewCompat.setOnApplyWindowInsetsListener(target) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(target)
    }

    private fun setupViewPager() {
        val adapter = HomePagerAdapter(
            onEssentialsInflated = { view -> setupEssentialsPage(view) },
            onHomeInflated = { view -> setupHomePage(view) },
            onEntertainmentInflated = { view -> setupEntertainmentPage(view) }
        )

        viewPager.adapter = adapter
        // Start on home page (index 1)
        viewPager.setCurrentItem(HomePagerAdapter.PAGE_HOME, false)

        // Disable over-scroll effect for cleaner look
        viewPager.getChildAt(0)?.overScrollMode = RecyclerView.OVER_SCROLL_NEVER
    }

    private fun setupEssentialsPage(view: View) {
        // SOS / Emergency button
        sosSubtitleView = view.findViewById(R.id.sosSubtitle)
        refreshSosSubtitle()
        view.findViewById<View>(R.id.sosCard)?.apply {
            setOnClickListener {
                it.vibrate()
                onSosTapped()
            }
            setOnLongClickListener {
                it.vibrate()
                showSosSettings()
                true
            }
        }

        // Battery views
        batteryPercentText = view.findViewById(R.id.batteryPercent)
        batteryStatusText = view.findViewById(R.id.batteryStatus)
        batteryIcon = view.findViewById(R.id.batteryIcon)
        lowBatteryWarning = view.findViewById(R.id.lowBatteryWarning)

        // Search functionality
        val searchInput = view.findViewById<EditText>(R.id.searchInput)
        searchInput?.setOnEditorActionListener { textView, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = textView.text.toString().trim()
                if (query.isNotEmpty()) {
                    performWebSearch(query)
                    textView.text = ""
                }
                true
            } else {
                false
            }
        }

        view.findViewById<MaterialButton>(R.id.btnVoiceSearch)?.setOnClickListener {
            it.vibrate()
            checkMicPermissionAndSearch()
        }

        // Date click opens calendar
        view.findViewById<View>(R.id.dateText)?.setOnClickListener {
            it.vibrate()
            openCalendar()
        }

        // Quick action buttons
        view.findViewById<MaterialButton>(R.id.btnFlashlight)?.setOnClickListener {
            it.vibrate()
            toggleFlashlight()
        }

        view.findViewById<MaterialButton>(R.id.btnBrightness)?.setOnClickListener {
            it.vibrate()
            openBrightnessSettings()
        }

        view.findViewById<MaterialButton>(R.id.btnVolume)?.setOnClickListener {
            it.vibrate()
            openVolumeSettings()
        }

        view.findViewById<MaterialButton>(R.id.btnSettings)?.setOnClickListener {
            it.vibrate()
            openSettings()
        }

        view.findViewById<MaterialButton>(R.id.btnLanguage)?.setOnClickListener {
            it.vibrate()
            showLanguageDialog()
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }
        isBatteryReceiverRegistered = true
    }

    private fun setupHomePage(view: View) {
        homeView = view

        // Setup notification indicator
        notificationIndicator = view.findViewById(R.id.notificationIndicator)
        notificationText = view.findViewById(R.id.notificationText)
        notificationIndicator?.setOnClickListener {
            it.vibrate()
            expandNotificationPanel()
        }

        // Setup Quick Call help info button
        view.findViewById<View>(R.id.btnQuickCallInfo)?.setOnClickListener {
            it.vibrate()
            showQuickCallHelpDialog()
        }

        // Setup favorite contacts
        val newContactAdapter = FavoriteContactAdapter { contact ->
            appLauncher.dialNumber(contact.phoneNumber)
        }
        contactAdapter = newContactAdapter

        view.findViewById<RecyclerView>(R.id.favoriteContactsRecycler)?.apply {
            layoutManager = LinearLayoutManager(
                this@MainActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = newContactAdapter
        }

        viewModel.favoriteContacts.observe(this) { contacts ->
            contactAdapter?.submitList(contacts)
        }

        // Setup favorite apps
        val newAppsAdapter = FavoriteAppsAdapter(
            onAppClick = { appInfo ->
                launchApp(appInfo.packageName)
            },
            onAppLongClick = { _ ->
                favoriteAppsAdapter?.let { adapter ->
                    val newEditMode = !adapter.isInEditMode()
                    adapter.setEditMode(newEditMode)
                    if (newEditMode) {
                        Toast.makeText(this, R.string.remove_from_home, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onRemoveClick = { appInfo ->
                removeFromHome(appInfo)
            }
        )
        favoriteAppsAdapter = newAppsAdapter

        view.findViewById<RecyclerView>(R.id.favoriteAppsRecycler)?.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 4)
            adapter = newAppsAdapter
        }

        // Setup main buttons
        view.findViewById<MaterialButton>(R.id.btnPhone)?.setOnClickListener {
            it.vibrate()
            appLauncher.openDialer()
        }

        view.findViewById<MaterialButton>(R.id.btnMessages)?.setOnClickListener {
            it.vibrate()
            appLauncher.openMessages()
        }

        view.findViewById<MaterialButton>(R.id.btnWhatsApp)?.setOnClickListener {
            it.vibrate()
            appLauncher.openWhatsApp()
        }

        view.findViewById<MaterialButton>(R.id.btnCamera)?.setOnClickListener {
            it.vibrate()
            appLauncher.openCamera()
        }

        view.findViewById<MaterialButton>(R.id.btnPhotos)?.setOnClickListener {
            it.vibrate()
            appLauncher.openGallery()
        }

        view.findViewById<MaterialButton>(R.id.btnAllApps)?.setOnClickListener {
            it.vibrate()
            startActivity(Intent(this, AppDrawerActivity::class.java))
        }

        view.findViewById<TextView>(R.id.btnSwipeTools)?.setOnClickListener {
            it.vibrate()
            viewPager.setCurrentItem(HomePagerAdapter.PAGE_ESSENTIALS, true)
        }

        view.findViewById<TextView>(R.id.btnSwipeFun)?.setOnClickListener {
            it.vibrate()
            viewPager.setCurrentItem(HomePagerAdapter.PAGE_ENTERTAINMENT, true)
        }

        // Tapping the empty-state hint opens the app drawer so the user can add favorites
        view.findViewById<View>(R.id.emptyAppsHint)?.setOnClickListener {
            it.vibrate()
            startActivity(Intent(this, AppDrawerActivity::class.java))
        }

        // Check permissions
        checkPermissionsAndLoad()

        // Setup swipe down to expand notifications
        setupSwipeDownGesture(view)

        // Show favorites (or empty hint) immediately now that the view is ready,
        // rather than waiting for the next onResume.
        loadFavoriteApps()
    }

    private fun setupEntertainmentPage(view: View) {
        entertainmentView = view

        // Games card → shuffled.online via Chrome Custom Tabs
        view.findViewById<View>(R.id.gamesCard)?.setOnClickListener {
            it.vibrate()
            openGames()
        }

        // On This Day photo memory card
        loadOnThisDay(view)

        // Setup daily quote
        val quoteText = view.findViewById<TextView>(R.id.quoteText)
        val quoteAuthor = view.findViewById<TextView>(R.id.quoteAuthor)
        val btnReadQuote = view.findViewById<MaterialButton>(R.id.btnReadQuote)

        val quote = QuoteProvider.getQuoteOfTheDay()
        quoteText?.text = quote.text
        quoteAuthor?.text = "— ${quote.author}"
        currentQuoteText = "${quote.text} ... by ${quote.author}"

        btnReadQuote?.setOnClickListener {
            it.vibrate()
            readQuoteAloud()
        }

        // Setup news section
        val newsContainer = view.findViewById<LinearLayout>(R.id.newsContainer)
        val newsLoading = view.findViewById<ProgressBar>(R.id.newsLoading)
        val newsError = view.findViewById<TextView>(R.id.newsError)
        val btnRefresh = view.findViewById<MaterialButton>(R.id.btnRefreshNews)

        fun loadNews() {
            newsLoading?.visibility = View.VISIBLE
            newsError?.visibility = View.GONE
            newsContainer?.removeAllViews()

            lifecycleScope.launch {
                when (val result = NewsRepository.fetchHeadlines()) {
                    is NewsRepository.NewsResult.Success -> {
                        newsLoading?.visibility = View.GONE
                        newsContainer?.removeAllViews()

                        for (item in result.items) {
                            val newsItem = layoutInflater.inflate(
                                R.layout.item_news,
                                newsContainer,
                                false
                            )
                            newsItem.findViewById<TextView>(R.id.newsTitle)?.text = item.title
                            newsItem.findViewById<TextView>(R.id.newsSource)?.text = item.source
                            newsItem.setOnClickListener {
                                openNewsLink(item.link)
                            }
                            newsContainer?.addView(newsItem)
                        }
                    }
                    is NewsRepository.NewsResult.Error -> {
                        newsLoading?.visibility = View.GONE
                        newsError?.visibility = View.VISIBLE
                    }
                }
            }
        }

        btnRefresh?.setOnClickListener {
            it.vibrate()
            loadNews()
        }

        // Load news on page setup
        loadNews()
    }

    private fun openNewsLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open link", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- On This Day ----------

    /** Pick the right runtime permission for the device's SDK level. */
    private fun photosPermissionName(): String =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            "android.permission.READ_EXTERNAL_STORAGE"

    private fun hasPhotosPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, photosPermissionName()) ==
                PackageManager.PERMISSION_GRANTED

    private fun loadOnThisDay(view: View) {
        val card = view.findViewById<FrameLayout>(R.id.onThisDayCard) ?: return
        val image = view.findViewById<ImageView>(R.id.onThisDayImage) ?: return
        val yearsAgoText = view.findViewById<TextView>(R.id.onThisDayYearsAgo) ?: return
        val prompt = view.findViewById<View>(R.id.onThisDayPrompt)
        val grantButton = view.findViewById<MaterialButton>(R.id.btnGrantPhotos)

        if (!hasPhotosPermission()) {
            card.visibility = View.GONE
            prompt?.visibility = View.VISIBLE
            grantButton?.setOnClickListener {
                it.vibrate()
                photosPermissionLauncher.launch(photosPermissionName())
            }
            return
        }
        prompt?.visibility = View.GONE

        lifecycleScope.launch {
            val memory = OnThisDayLoader.findToday(applicationContext)
            if (memory == null) {
                card.visibility = View.GONE
                return@launch
            }
            yearsAgoText.text = if (memory.yearsAgo == 1) {
                getString(R.string.on_this_day_year_ago)
            } else {
                getString(R.string.on_this_day_years_ago, memory.yearsAgo)
            }
            com.bumptech.glide.Glide.with(this@MainActivity)
                .load(memory.uri)
                .centerCrop()
                .into(image)
            card.visibility = View.VISIBLE
            card.setOnClickListener {
                it.vibrate()
                try {
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(memory.uri, "image/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(viewIntent)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Cannot open photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ---------- /On This Day ----------

    private fun openGames() {
        val url = "https://shuffled.online"
        // Custom Tabs gives an in-app browser experience: back returns to the launcher
        // cleanly, no app switching confusion. Falls back to default browser if none.
        try {
            val customTabs = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(true)
                .build()
            customTabs.launchUrl(this, Uri.parse(url))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e2: Exception) {
                Toast.makeText(this, R.string.games_cannot_open, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun readQuoteAloud() {
        if (isTtsReady && currentQuoteText.isNotEmpty()) {
            // Stop any ongoing speech first
            textToSpeech?.stop()
            // Speak the quote
            textToSpeech?.speak(currentQuoteText, TextToSpeech.QUEUE_FLUSH, null, "quote")
        } else {
            Toast.makeText(this, "Speech not available", Toast.LENGTH_SHORT).show()
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

        rootLayout?.setBackgroundResource(backgroundRes)
    }

    // NotificationCountListener implementation
    override fun onNotificationCountChanged(count: Int) {
        runOnUiThread {
            if (count > 0) {
                notificationIndicator?.visibility = View.VISIBLE
                notificationText?.text = if (count == 1) {
                    "You have 1 new notification"
                } else {
                    "You have $count new notifications"
                }
            } else {
                notificationIndicator?.visibility = View.GONE
            }
        }
    }

    private fun isNotificationAccessEnabled(): Boolean {
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(this)
        return enabledListeners.contains(packageName)
    }

    private fun checkNotificationAccessAfterDelay() {
        // Skip if already enabled
        if (isNotificationAccessEnabled()) return

        val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)

        // Record install time if not set
        val installTime = prefs.getLong("install_time", 0L)
        if (installTime == 0L) {
            prefs.edit().putLong("install_time", System.currentTimeMillis()).apply()
            return // First install, don't prompt yet
        }

        // Check if already prompted
        val hasPrompted = prefs.getBoolean("notification_access_prompted", false)
        if (hasPrompted) return

        // Check if 2 days have passed
        val twoDaysMs = 2 * 24 * 60 * 60 * 1000L
        val timeSinceInstall = System.currentTimeMillis() - installTime

        if (timeSinceInstall >= twoDaysMs) {
            prefs.edit().putBoolean("notification_access_prompted", true).apply()
            showNotificationAccessDialog()
        }
    }

    private fun showNotificationAccessDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.notification_access_title)
            .setMessage(R.string.notification_access_message)
            .setPositiveButton(R.string.enable) { _, _ ->
                openNotificationAccessSettings()
            }
            .setNegativeButton(R.string.setup_later, null)
            .show()
    }

    private fun openNotificationAccessSettings() {
        try {
            val intent = Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open settings", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeDownGesture(view: View) {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val startY = e1?.y ?: 0f
                val diffY = e2.y - startY
                val diffX = (e2.x) - (e1?.x ?: 0f)

                val topRegionPx = TOP_REGION_HEIGHT_DP * resources.displayMetrics.density

                // Only trigger if:
                // 1. Started from top region of the screen
                // 2. Swipe is mostly vertical (2x more vertical than horizontal)
                // 3. Swipe distance and velocity exceed thresholds
                if (startY < topRegionPx &&
                    abs(diffY) > abs(diffX) * 2 &&
                    diffY > SWIPE_THRESHOLD &&
                    velocityY > SWIPE_VELOCITY_THRESHOLD) {
                    expandNotificationPanel()
                    return true
                }
                return false
            }
        })

        view.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false // Don't consume the event, let it propagate
        }
    }

    // ---------- SOS / Emergency ----------

    private fun refreshSosSubtitle() {
        val contact = sosManager.get()
        sosSubtitleView?.text = if (contact == null) {
            getString(R.string.sos_subtitle_unset)
        } else {
            getString(R.string.sos_subtitle_set, contact.name.ifBlank { contact.number })
        }
    }

    private fun onSosTapped() {
        val contact = sosManager.get()
        if (contact == null) {
            showSosSetup()
        } else {
            startSosCountdown(contact)
        }
    }

    private fun showSosSetup() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sos_setup_title)
            .setMessage(R.string.sos_setup_message)
            .setPositiveButton(R.string.sos_pick_contact) { _, _ -> launchContactPicker() }
            .setNegativeButton(R.string.sos_cancel, null)
            .show()
    }

    private fun launchContactPicker() {
        try {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            sosContactPickerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.sos_no_picker, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 3-second cancellable countdown before dialing. Uses ACTION_DIAL (no CALL_PHONE
     * permission needed) so the dialer opens pre-filled with the number — the user
     * just taps the green call icon. Safer than auto-dialing and avoids permission UX.
     */
    private fun startSosCountdown(contact: SosManager.SosContact) {
        val totalSeconds = 3
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sos_confirm_title)
            .setMessage(getString(R.string.sos_confirm_message, contact.name.ifBlank { contact.number }, totalSeconds))
            .setPositiveButton(R.string.sos_call_now, null) // overridden below to NOT dismiss too early
            .setNegativeButton(R.string.sos_cancel, null)
            .setCancelable(false)
            .create()

        val timer = object : CountDownTimer((totalSeconds * 1000L) + 250, 1000) {
            override fun onTick(msUntilFinished: Long) {
                val remaining = (msUntilFinished / 1000).toInt().coerceAtLeast(1)
                dialog.setMessage(
                    getString(R.string.sos_confirm_message, contact.name.ifBlank { contact.number }, remaining)
                )
            }
            override fun onFinish() {
                dialog.dismiss()
                dialEmergency(contact.number)
            }
        }

        dialog.setOnDismissListener { timer.cancel() }
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                timer.cancel()
                dialog.dismiss()
                dialEmergency(contact.number)
            }
        }
        dialog.show()
        timer.start()
    }

    private fun dialEmergency(number: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open dialer", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSosSettings() {
        val contact = sosManager.get()
        if (contact == null) {
            // Long-pressed before setup → fall through to setup
            showSosSetup()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.sos_change_title, contact.name.ifBlank { contact.number }))
            .setPositiveButton(R.string.sos_change) { _, _ -> launchContactPicker() }
            .setNegativeButton(R.string.sos_remove) { _, _ ->
                sosManager.clear()
                refreshSosSubtitle()
            }
            .setNeutralButton(R.string.sos_cancel, null)
            .show()
    }

    // ---------- /SOS ----------

    @SuppressLint("WrongConstant")
    private fun expandNotificationPanel() {
        try {
            val statusBarService = getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val expand = statusBarManager.getMethod("expandNotificationsPanel")
            expand.invoke(statusBarService)
        } catch (e: Exception) {
            // Fallback: some devices may not support this
        }
    }

    override fun onResume() {
        super.onResume()
        loadFavoriteApps()
        updateBackgroundForTimeOfDay()

        // Register for notification updates
        NotificationService.setListener(this)

        // Performance tracking
        if (isFirstCreate) {
            // Cold start complete - UI is now visible
            viewPager.post {
                PerformanceMonitor.markColdStartComplete()
                PerformanceMonitor.startFrameTracking()
                updateDebugOverlay()
                isFirstCreate = false
            }
        } else {
            PerformanceMonitor.markWarmStart()
            viewPager.post {
                PerformanceMonitor.markWarmStartComplete()
                updateDebugOverlay()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        PerformanceMonitor.stopFrameTracking()
        NotificationService.setListener(null)
    }

    private fun setupDebugOverlay() {
        debugOverlay = findViewById(R.id.debugOverlay)
        debugStartupText = findViewById(R.id.debugStartupTime)
        debugFpsText = findViewById(R.id.debugFps)
        debugMemoryText = findViewById(R.id.debugMemory)
        debugDrawerText = findViewById(R.id.debugDrawer)
        debugAppLaunchText = findViewById(R.id.debugAppLaunch)

        // Show overlay only in debug mode
        debugOverlay?.visibility = if (PerformanceMonitor.DEBUG_MODE) View.VISIBLE else View.GONE

        // Long press on overlay to log all metrics
        debugOverlay?.setOnLongClickListener {
            PerformanceMonitor.logAllMetrics(this)
            Toast.makeText(this, "Metrics logged to Logcat", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun updateDebugOverlay() {
        if (!PerformanceMonitor.DEBUG_MODE) return

        val metrics = PerformanceMonitor.getMetrics()
        PerformanceMonitor.updateMemoryMetrics(this)
        val updatedMetrics = PerformanceMonitor.getMetrics()

        debugStartupText?.text = "Startup: ${metrics.coldStartMs}ms"
        debugFpsText?.text = "FPS: ${String.format("%.1f", updatedMetrics.currentFps)}"
        debugMemoryText?.text = "Mem: ${String.format("%.1f", updatedMetrics.memoryUsageMb)}MB"
        debugDrawerText?.text = "Drawer: ${metrics.drawerLoadMs}ms"
        debugAppLaunchText?.text = "Launch: ${metrics.avgAppLaunchMs}ms avg"
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBatteryReceiverRegistered) {
            unregisterReceiver(batteryReceiver)
            isBatteryReceiverRegistered = false
        }
        // Turn off flashlight if on
        if (isFlashlightOn) {
            toggleFlashlight()
        }
        // Shutdown text-to-speech
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    private fun updateBatteryStatus(intent: Intent?) {
        intent ?: return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

        val batteryPct = if (level >= 0 && scale > 0) {
            (level * 100 / scale)
        } else {
            0
        }

        batteryPercentText?.text = "$batteryPct%"

        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        if (isCharging) {
            batteryStatusText?.visibility = View.VISIBLE
            batteryStatusText?.text = getString(R.string.charging)
        } else {
            batteryStatusText?.visibility = View.GONE
        }

        // Update colors and warning based on battery level
        val isLowBattery = batteryPct <= 40 && !isCharging

        // Show/hide low battery warning
        lowBatteryWarning?.visibility = if (isLowBattery) View.VISIBLE else View.GONE

        // Update icon and text colors
        val iconColor = when {
            batteryPct <= 20 -> getColor(R.color.battery_low) // Red - critical
            batteryPct <= 40 && !isCharging -> getColor(R.color.battery_low) // Red - low
            isCharging -> getColor(R.color.button_phone) // Green - charging
            else -> getColor(R.color.button_phone) // Green - normal
        }
        batteryIcon?.setColorFilter(iconColor)

        // Update percentage text color
        val textColor = if (isLowBattery) {
            getColor(R.color.battery_low)
        } else {
            getColor(R.color.text_primary)
        }
        batteryPercentText?.setTextColor(textColor)

        // Update charging status color
        batteryStatusText?.setTextColor(
            if (isCharging) getColor(R.color.button_phone) else textColor
        )
    }

    private fun toggleFlashlight() {
        try {
            cameraId?.let { id ->
                isFlashlightOn = !isFlashlightOn
                cameraManager?.setTorchMode(id, isFlashlightOn)
                val message = if (isFlashlightOn) R.string.flashlight_on else R.string.flashlight_off
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Flashlight not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openBrightnessSettings() {
        try {
            startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open display settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openVolumeSettings() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_RING,
                AudioManager.ADJUST_SAME,
                AudioManager.FLAG_SHOW_UI
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(this, "Cannot open volume settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLanguageDialog() {
        val languages = LocaleHelper.availableLanguages
        val currentCode = LocaleHelper.getLanguage(this)
        val currentIndex = languages.indexOfFirst { it.code == currentCode }.coerceAtLeast(0)

        // Show language names in their native form
        val languageNames = languages.map { "${it.nativeName} (${it.englishName})" }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.select_language)
            .setSingleChoiceItems(languageNames, currentIndex) { dialog, which ->
                val selectedLanguage = languages[which]
                if (selectedLanguage.code != currentCode) {
                    LocaleHelper.setLanguage(this, selectedLanguage.code)
                    dialog.dismiss()
                    recreate()
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showQuickCallHelpDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.quick_call_help_title)
            .setMessage(R.string.quick_call_help_message)
            .setPositiveButton(R.string.got_it, null)
            .show()
    }

    private fun checkMicPermissionAndSearch() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startVoiceSearch()
            }
            else -> {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_search))
        }

        try {
            voiceSearchLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.voice_not_available, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCalendar() {
        // Try common calendar intents
        val calendarIntents = listOf(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALENDAR)
            },
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("content://com.android.calendar/time")
            },
            Intent().apply {
                setClassName("com.samsung.android.calendar", "com.samsung.android.calendar.MainActivity")
            },
            Intent().apply {
                setClassName("com.google.android.calendar", "com.android.calendar.LaunchActivity")
            }
        )

        for (intent in calendarIntents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return
            } catch (e: Exception) {
                // Try next intent
            }
        }

        Toast.makeText(this, "Cannot open calendar", Toast.LENGTH_SHORT).show()
    }

    private fun performWebSearch(query: String) {
        // Try Google Search app first
        try {
            val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
            }
            startActivity(searchIntent)
            return
        } catch (e: Exception) {
            // Fall through to browser
        }

        // Fallback to browser
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
            }
            startActivity(browserIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open search", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadFavoriteApps() {
        val adapter = favoriteAppsAdapter ?: return

        val favoritePackages = favoriteAppsManager.getFavoriteApps()

        homeView?.let { view ->
            val myAppsSection = view.findViewById<View>(R.id.myAppsSection)
            val emptyAppsHint = view.findViewById<View>(R.id.emptyAppsHint)

            if (favoritePackages.isEmpty()) {
                myAppsSection?.visibility = View.GONE
                emptyAppsHint?.visibility = View.VISIBLE
            } else {
                myAppsSection?.visibility = View.VISIBLE
                emptyAppsHint?.visibility = View.GONE

                val apps = favoritePackages.mapNotNull { packageName ->
                    getAppInfo(packageName)
                }
                adapter.submitList(apps)
            }
        }

        adapter.setEditMode(false)
    }

    private fun getAppInfo(packageName: String): AppInfo? {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val appName = pm.getApplicationLabel(appInfo).toString()
            val icon = pm.getApplicationIcon(appInfo)
            AppInfo(name = appName, packageName = packageName, icon = icon)
        } catch (e: PackageManager.NameNotFoundException) {
            favoriteAppsManager.removeFavoriteApp(packageName)
            null
        }
    }

    private fun removeFromHome(appInfo: AppInfo) {
        favoriteAppsManager.removeFavoriteApp(appInfo.packageName)
        Toast.makeText(this, R.string.app_removed, Toast.LENGTH_SHORT).show()
        loadFavoriteApps()
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            PerformanceMonitor.markAppLaunchStart()
            usageTracker.recordLaunch(packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            PerformanceMonitor.markAppLaunchComplete()
            updateDebugOverlay()
        }
    }

    private fun setupBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    favoriteAppsAdapter?.isInEditMode() == true -> {
                        favoriteAppsAdapter?.setEditMode(false)
                    }
                    viewPager.currentItem != HomePagerAdapter.PAGE_HOME -> {
                        viewPager.setCurrentItem(HomePagerAdapter.PAGE_HOME, true)
                    }
                    // On home page - do nothing
                }
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

    private fun checkFirstLaunch() {
        val prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("first_launch", true)

        if (isFirstLaunch) {
            prefs.edit().putBoolean("first_launch", false).apply()
            if (!isDefaultLauncher()) {
                showSetupDialog()
            }
        }
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private fun showSetupDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.setup_title)
            .setMessage(R.string.setup_message)
            .setPositiveButton(R.string.setup_yes) { _, _ ->
                openDefaultLauncherSettings()
            }
            .setNegativeButton(R.string.setup_later, null)
            .setCancelable(true)
            .show()
    }

    private fun openDefaultLauncherSettings() {
        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                startActivity(intent)
            } catch (e2: Exception) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }
}
