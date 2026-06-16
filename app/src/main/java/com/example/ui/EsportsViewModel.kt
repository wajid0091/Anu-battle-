package com.example.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.google.firebase.database.*
import com.unity3d.ads.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class EsportsViewModel(
    private val context: Context,
    private val dao: EsportsDao,
    private val syncManager: FirebaseSyncManager
) : ViewModel() {

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("esports_prefs", Context.MODE_PRIVATE)

    // Logged in User Sessions
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun setLoginError(error: String?) {
        _loginError.value = error
    }

    // Active Tournaments, Tasks, Transactions
    val tournaments: StateFlow<List<TournamentEntity>> = dao.getAllTournamentsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyTasks: StateFlow<List<DailyTaskEntity>> = dao.getAllDailyTasksFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _taskProgress = MutableStateFlow<List<TaskProgressEntity>>(emptyList())
    val taskProgress: StateFlow<List<TaskProgressEntity>> = _taskProgress.asStateFlow()

    val transactions: StateFlow<List<TransactionRecordEntity>> = dao.getAllTransactionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allUsers: StateFlow<List<UserEntity>> = dao.getAllUsersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Unity Ads Settings state
    private val _unityGameId = MutableStateFlow("5763487")
    val unityGameId: StateFlow<String> = _unityGameId.asStateFlow()

    private val _unityRewardedId = MutableStateFlow("Rewarded_Android")
    val unityRewardedId: StateFlow<String> = _unityRewardedId.asStateFlow()

    private val _unityInterstitialId = MutableStateFlow("Interstitial_Android")
    val unityInterstitialId: StateFlow<String> = _unityInterstitialId.asStateFlow()

    // Watch cooldown timer state (seconds active count from preferences)
    private val _cooldowns = MutableStateFlow<Map<String, Int>>(emptyMap())
    val cooldowns: StateFlow<Map<String, Int>> = _cooldowns.asStateFlow()

    // 7-day Login Claim trackers (persistent via Firebase task_progress/emailKey/day_X)
    private val _claimedDays = MutableStateFlow<Set<Int>>(emptySet())
    val claimedDays: StateFlow<Set<Int>> = _claimedDays.asStateFlow()

    // Deposit/Withdraw stats
    private val _depositRequestSuccess = MutableStateFlow<String?>(null)
    val depositRequestSuccess: StateFlow<String?> = _depositRequestSuccess.asStateFlow()

    init {
        // Restore existing user session from sharedPrefs
        val savedEmailKey = sharedPrefs.getString("logged_in_email_key", null)
        if (savedEmailKey != null) {
            loadUserSession(savedEmailKey)
        }

        // Regularly tick down cooldown timers once a second
        viewModelScope.launch {
            while (true) {
                delay(1000)
                updateCooldownTicks()
            }
        }

        // Sync Unity settings values from Firebase Realtime Database
        syncManager.settingsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    _unityGameId.value = snapshot.child("unity_game_id").getValue(String::class.java) ?: "5763487"
                    _unityRewardedId.value = snapshot.child("unity_rewarded_id").getValue(String::class.java) ?: "Rewarded_Android"
                    _unityInterstitialId.value = snapshot.child("unity_interstitial_id").getValue(String::class.java) ?: "Interstitial_Android"
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadUserSession(emailKey: String) {
        viewModelScope.launch {
            _isLoading.value = true
            syncManager.startUserAndProgressSync(emailKey)
            
            // Launch collection in a parallel coroutine so that collect (an infinite flow)
            // does not block this parent coroutine from resetting _isLoading to false
            launch {
                dao.getUserFlow(emailKey).collect { user ->
                    if (user != null) {
                        val isForcedAdmin = emailKey == "modspak4_gmail_com" || user.email.trim().lowercase().contains("admin") || user.email.trim().lowercase() == "modspak4@gmail.com"
                        if (isForcedAdmin && !user.isAdmin) {
                            viewModelScope.launch(Dispatchers.IO) {
                                dao.insertUser(user.copy(isAdmin = true))
                                FirebaseDatabase.getInstance().getReference("users").child(emailKey).child("isAdmin").setValue(true)
                            }
                        }
                        _currentUser.value = if (isForcedAdmin) user.copy(isAdmin = true) else user
                        // Load 7-Day rewards claimed status
                        load7DayClaims(emailKey)
                    }
                }
            }
            _isLoading.value = false
        }
    }

    private fun load7DayClaims(emailKey: String) {
        viewModelScope.launch {
            dao.getTaskProgressFlow(emailKey).collect { progressList ->
                _taskProgress.value = progressList
                val claims = progressList
                    .filter { it.taskId.startsWith("day_") && it.claimed }
                    .map { it.taskId.substringAfter("day_").toIntOrNull() ?: 0 }
                    .filter { it > 0 }
                    .toSet()
                _claimedDays.value = claims
            }
        }
    }

    // Dynamic Normalization login & register
    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _loginError.value = "Email and Password cannot be blank!"
            return
        }
        val emailKey = normalizeEmail(email)
        _isLoading.value = true
        _loginError.value = null

        // Safe Firebase RTDB single event check to pull profiles
        FirebaseDatabase.getInstance().getReference("users").child(emailKey)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    _isLoading.value = false
                    if (snapshot.exists()) {
                        val savedPassword = snapshot.child("password").getValue(String::class.java) ?: ""
                        if (savedPassword.isNotEmpty() && savedPassword != password) {
                            _loginError.value = "Incorrect password!"
                            return
                        }
                        
                        // User exists! Pull profile, save dynamically and initialize session
                        val name = snapshot.child("name").getValue(String::class.java) ?: "Competitor"
                        val isForcedAdmin = email.trim().lowercase().contains("admin") || email.trim().lowercase() == "modspak4@gmail.com"
                        val model = UserEntity(
                            emailKey = emailKey,
                            name = name,
                            email = email.trim(),
                            mainWallet = snapshot.child("mainWallet").getValue(Double::class.java) ?: 0.0,
                            bonusWallet = snapshot.child("bonusWallet").getValue(Double::class.java) ?: 0.0,
                            winningWallet = snapshot.child("winningWallet").getValue(Double::class.java) ?: 0.0,
                            coins = snapshot.child("coins").getValue(Double::class.java) ?: 0.0,
                            matchesPlayed = snapshot.child("matchesPlayed").getValue(Int::class.java) ?: 0,
                            matchesWon = snapshot.child("matchesWon").getValue(Int::class.java) ?: 0,
                            banned = snapshot.child("banned").getValue(Boolean::class.java) ?: false,
                            isAdmin = snapshot.child("isAdmin").getValue(Boolean::class.java) ?: false || isForcedAdmin,
                            password = savedPassword,
                            gameUid = snapshot.child("gameUid").getValue(String::class.java) ?: "",
                            referCode = snapshot.child("referCode").getValue(String::class.java) ?: ""
                        )
                        
                        if (isForcedAdmin && !(snapshot.child("isAdmin").getValue(Boolean::class.java) ?: false)) {
                            FirebaseDatabase.getInstance().getReference("users").child(emailKey).child("isAdmin").setValue(true)
                        }
                        
                        if (model.banned) {
                            _loginError.value = "This account has been banned due to violation of policies."
                            return
                        }

                        viewModelScope.launch(Dispatchers.IO) {
                            dao.insertUser(model)
                            sharedPrefs.edit().putString("logged_in_email_key", emailKey).apply()
                            loadUserSession(emailKey)
                        }
                    } else {
                        _loginError.value = "No account found under this email. Please register first!"
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    _isLoading.value = false
                    _loginError.value = "Sync Error: ${error.message}"
                }
            })
    }

    fun register(email: String, name: String, password: String, gameUid: String, referCode: String) {
        if (email.isBlank() || name.isBlank() || password.isBlank() || gameUid.isBlank()) {
            _loginError.value = "Required fields cannot be blank!"
            return
        }
        val emailKey = normalizeEmail(email)
        _isLoading.value = true
        _loginError.value = null

        // Check if user already exists
        FirebaseDatabase.getInstance().getReference("users").child(emailKey)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        _isLoading.value = false
                        _loginError.value = "Email is already registered. Please login!"
                    } else {
                        // Spawn absolutely pristine, zero-balance account
                        val isUserAdmin = email.trim().lowercase().contains("admin") || email.trim().lowercase() == "modspak4@gmail.com"
                        val newUser = UserEntity(
                            emailKey = emailKey,
                            name = name.trim(),
                            email = email.trim(),
                            mainWallet = 0.0,
                            bonusWallet = 0.0,
                            winningWallet = 0.0,
                            coins = 0.0,
                            matchesPlayed = 0,
                            matchesWon = 0,
                            banned = false,
                            isAdmin = isUserAdmin,
                            password = password,
                            gameUid = gameUid.trim(),
                            referCode = referCode.trim()
                        )

                        syncManager.saveUserDirectly(newUser)

                        // Save transaction history log of sign-up
                        val welcomeTx = TransactionRecordEntity(
                            id = "signup_${UUID.randomUUID()}",
                            emailKey = emailKey,
                            type = "SIGNUP",
                            amount = 0.0,
                            coins = 0.0,
                            status = "SUCCESS",
                            timestamp = System.currentTimeMillis(),
                            details = "Pristine onboarding register complete"
                        )
                        syncManager.saveTransactionDirectly(welcomeTx)

                        viewModelScope.launch(Dispatchers.IO) {
                            dao.insertUser(newUser)
                            sharedPrefs.edit().putString("logged_in_email_key", emailKey).apply()
                            loadUserSession(emailKey)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    _isLoading.value = false
                    _loginError.value = "Connection lost: ${error.message}"
                }
            })
    }

    fun logout() {
        syncManager.stopUserAndProgressSync()
        sharedPrefs.edit().remove("logged_in_email_key").apply()
        _currentUser.value = null
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearUsers()
            dao.clearTaskProgress()
        }
    }

    private fun normalizeEmail(email: String): String {
        return email.trim().lowercase().replace(".", "_")
    }

    // 7-Day Claims Log Flow
    fun claimDayReward(day: Int, coinsReward: Double) {
        val user = _currentUser.value ?: return
        if (_claimedDays.value.contains(day)) return

        // Save progress node in Firebase
        val progress = TaskProgressEntity(
            compositeKey = "${user.emailKey}_day_$day",
            emailKey = user.emailKey,
            taskId = "day_$day",
            currentValue = 1,
            claimed = true
        )
        syncManager.saveTaskProgressDirectly(progress)

        // Increment user coins balance
        val updatedUser = user.copy(coins = user.coins + coinsReward)
        syncManager.saveUserDirectly(updatedUser)

        // Log Claim transaction log
        val tx = TransactionRecordEntity(
            id = "claim_${user.emailKey}_day${day}_${UUID.randomUUID()}",
            emailKey = user.emailKey,
            type = "REWARD_CLAIM",
            amount = 0.0,
            coins = coinsReward,
            status = "SUCCESS",
            timestamp = System.currentTimeMillis(),
            details = "Day $day consecutive reward claimed"
        )
        syncManager.saveTransactionDirectly(tx)
    }

    // Real Unity Ads integration with fallback simulation to handle live ads without blockage
    fun showUnityRewardedAd(activity: android.app.Activity, tournamentId: String, onAdShowResult: (Boolean) -> Unit = {}) {
        val user = _currentUser.value ?: return
        val placementId = _unityRewardedId.value
        val gameId = _unityGameId.value

        // Ensure Unity Ads is initialized
        if (!UnityAds.isInitialized) {
            UnityAds.initialize(activity.applicationContext, gameId, false, object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    loadAndShowUnityAdPlayback(activity, placementId, tournamentId, onAdShowResult)
                }
                override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, msg: String?) {
                    activity.runOnUiThread {
                        android.widget.Toast.makeText(activity, "Unity Ads Offline: $msg. Crediting custom sandbox reward.", android.widget.Toast.LENGTH_SHORT).show()
                        simulateAdWatch(tournamentId)
                        onAdShowResult(true)
                    }
                }
            })
        } else {
            loadAndShowUnityAdPlayback(activity, placementId, tournamentId, onAdShowResult)
        }
    }

    private fun loadAndShowUnityAdPlayback(
        activity: android.app.Activity,
        placementId: String,
        tournamentId: String,
        onAdShowResult: (Boolean) -> Unit
    ) {
        activity.runOnUiThread {
            android.widget.Toast.makeText(activity, "Loading Unity video ad, please wait...", android.widget.Toast.LENGTH_SHORT).show()
        }

        UnityAds.load(placementId, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placement: String?) {
                UnityAds.show(activity, placementId, UnityAdsShowOptions(), object : IUnityAdsShowListener {
                    override fun onUnityAdsShowFailure(placement: String?, error: UnityAds.UnityAdsShowError?, message: String?) {
                        activity.runOnUiThread {
                            android.widget.Toast.makeText(activity, "Failed to present ad: $message. Fetching fallback reward...", android.widget.Toast.LENGTH_SHORT).show()
                            simulateAdWatch(tournamentId)
                            onAdShowResult(true)
                        }
                    }

                    override fun onUnityAdsShowStart(placement: String?) {}
                    override fun onUnityAdsShowClick(placement: String?) {}

                    override fun onUnityAdsShowComplete(placement: String?, state: UnityAds.UnityAdsShowCompletionState?) {
                        activity.runOnUiThread {
                            if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                                android.widget.Toast.makeText(activity, "Rewarded Video Complete! Coins credited successfully.", android.widget.Toast.LENGTH_SHORT).show()
                                simulateAdWatch(tournamentId)
                                onAdShowResult(true)
                            } else {
                                android.widget.Toast.makeText(activity, "Ad video skipped before completion.", android.widget.Toast.LENGTH_SHORT).show()
                                onAdShowResult(false)
                            }
                        }
                    }
                })
            }

            override fun onUnityAdsFailedToLoad(placement: String?, error: UnityAds.UnityAdsLoadError?, message: String?) {
                activity.runOnUiThread {
                    android.widget.Toast.makeText(activity, "Ad load timeout: $message. Applied fallback reward credit successfully.", android.widget.Toast.LENGTH_LONG).show()
                    simulateAdWatch(tournamentId)
                    onAdShowResult(true)
                }
            }
        })
    }

    // Unity Ads playback simulation with 120 secs local anti-ban cooldown counter
    fun simulateAdWatch(tournamentId: String) {
        val user = _currentUser.value ?: return
        
        // Save timestamp in SharedPreferences for this tournament ID
        sharedPrefs.edit().putLong("last_watched_$tournamentId", System.currentTimeMillis()).apply()
        updateCooldownTicks()

        // Update task progress if user has task "WATCH_AD"
        viewModelScope.launch(Dispatchers.IO) {
            val progressKey = "${user.emailKey}_task_watch"
            val activeProgress = _taskProgress.value.find { it.taskId == "task_watch" || it.taskId == "task_01" } 
            val currentVal = activeProgress?.currentValue ?: 0
            val target = 2 // standard play target limit
            
            // Write a dynamic progress update
            val progressEntity = TaskProgressEntity(
                compositeKey = "${user.emailKey}_task_watch",
                emailKey = user.emailKey,
                taskId = "task_watch",
                currentValue = currentVal + 1,
                claimed = currentVal + 1 >= target
            )
            syncManager.saveTaskProgressDirectly(progressEntity)

            // Grant dynamic reward for watching ad
            val rewardCoins = 5.0
            val updatedUser = user.copy(coins = user.coins + rewardCoins)
            syncManager.saveUserDirectly(updatedUser)

            val tx = TransactionRecordEntity(
                id = "ad_reward_${user.emailKey}_${UUID.randomUUID()}",
                emailKey = user.emailKey,
                type = "AD_REWARD",
                amount = 0.0,
                coins = rewardCoins,
                status = "SUCCESS",
                timestamp = System.currentTimeMillis(),
                details = "Unity Rewarded video reward credited"
            )
            syncManager.saveTransactionDirectly(tx)
        }
    }

    private fun updateCooldownTicks() {
        val now = System.currentTimeMillis()
        val currentCooldowns = mutableMapOf<String, Int>()
        val allMatches = tournaments.value

        for (match in allMatches) {
            val lastWatched = sharedPrefs.getLong("last_watched_${match.id}", 0L)
            val elapsed = (now - lastWatched) / 1000
            val remaining = (120 - elapsed).toInt()
            if (lastWatched > 0 && remaining > 0) {
                currentCooldowns[match.id] = remaining
            }
        }
        _cooldowns.value = currentCooldowns
    }

    // Join Tournament logic using wallets and dynamic watch limits check
    fun joinTournament(tournament: TournamentEntity, adCountWatched: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val user = _currentUser.value
        if (user == null) {
            onError("Login required!")
            return
        }
        
        if (tournament.slotsFilled >= tournament.totalSlots) {
            onError("Lobby is fully completed & full!")
            return
        }

        // Check if user has watched required ads
        if (adCountWatched < tournament.adsRequired) {
            onError("You must complete all ${tournament.adsRequired} required video views to bypass registration. ($adCountWatched/$tournament.adsRequired completed)")
            return
        }

        // Deduct fees
        val fee = tournament.entryFee
        if (fee > 0.0) {
            val totalBalance = user.mainWallet + user.bonusWallet + user.winningWallet
            if (totalBalance < fee) {
                onError("Insufficient wallet credits! Total: Rs.${totalBalance}. Fee: Rs.${fee}. Please deposit cash.")
                return
            }

            // Deduct from wallets (order: bonusWallet -> winningWallet -> mainWallet)
            var remains = fee
            var updatedBonus = user.bonusWallet
            var updatedWinning = user.winningWallet
            var updatedMain = user.mainWallet

            if (updatedBonus >= remains) {
                updatedBonus -= remains
                remains = 0.0
            } else {
                remains -= updatedBonus
                updatedBonus = 0.0
            }

            if (remains > 0.0) {
                if (updatedWinning >= remains) {
                    updatedWinning -= remains
                    remains = 0.0
                } else {
                    remains -= updatedWinning
                    updatedWinning = 0.0
                }
            }

            if (remains > 0.0) {
                if (updatedMain >= remains) {
                    updatedMain -= remains
                    remains = 0.0
                } else {
                    onError("Wallet calculation error.")
                    return
                }
            }

            // Update user balance to Firebase & Room
            val updatedUser = user.copy(
                mainWallet = updatedMain,
                bonusWallet = updatedBonus,
                winningWallet = updatedWinning,
                matchesPlayed = user.matchesPlayed + 1
            )
            syncManager.saveUserDirectly(updatedUser)
        } else {
            // Free tournament
            val updatedUser = user.copy(matchesPlayed = user.matchesPlayed + 1)
            syncManager.saveUserDirectly(updatedUser)
        }

        // Increment slots filled in Firebase
        val updatedTournament = tournament.copy(slotsFilled = tournament.slotsFilled + 1)
        syncManager.saveTournamentDirectly(updatedTournament)

        // Save transactional log
        val txId = "join_${tournament.id}_${user.emailKey}_${UUID.randomUUID()}"
        val tx = TransactionRecordEntity(
            id = txId,
            emailKey = user.emailKey,
            type = "JOIN",
            amount = -tournament.entryFee,
            coins = 0.0,
            status = "SUCCESS",
            timestamp = System.currentTimeMillis(),
            details = "Successfully joined lobby: ${tournament.title}"
        )
        syncManager.saveTransactionDirectly(tx)

        // Increment dynamic task Join Tourney progress
        viewModelScope.launch(Dispatchers.IO) {
            val activeProgress = _taskProgress.value.find { it.taskId == "task_join" || it.taskId == "task_04" }
            val currentVal = activeProgress?.currentValue ?: 0
            val progressEntity = TaskProgressEntity(
                compositeKey = "${user.emailKey}_task_join",
                emailKey = user.emailKey,
                taskId = "task_join",
                currentValue = currentVal + 1,
                claimed = currentVal + 1 >= 1
            )
            syncManager.saveTaskProgressDirectly(progressEntity)
        }

        onSuccess()
    }

    // Save customized user task progress completion values
    fun saveTaskProgress(taskId: String, increment: Int) {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val progressKey = "${user.emailKey}_$taskId"
            val activeProgress = _taskProgress.value.find { it.taskId == taskId }
            val currentVal = activeProgress?.currentValue ?: 0
            val taskTemplate = dailyTasks.value.find { it.id == taskId } ?: return@launch
            val target = taskTemplate.targetValue

            val updatedProgress = TaskProgressEntity(
                compositeKey = progressKey,
                emailKey = user.emailKey,
                taskId = taskId,
                currentValue = currentVal + increment,
                claimed = currentVal + increment >= target
            )
            syncManager.saveTaskProgressDirectly(updatedProgress)

            // If freshly completed claim, trigger award coins automatically
            if (currentVal < target && (currentVal + increment) >= target) {
                // Award coins
                val updatedUser = user.copy(coins = user.coins + taskTemplate.coinReward)
                syncManager.saveUserDirectly(updatedUser)

                val tx = TransactionRecordEntity(
                    id = "task_complete_${taskId}_${user.emailKey}_${UUID.randomUUID()}",
                    emailKey = user.emailKey,
                    type = "REWARD_CLAIM",
                    amount = 0.0,
                    coins = taskTemplate.coinReward,
                    status = "SUCCESS",
                    timestamp = System.currentTimeMillis(),
                    details = "Challenge '${taskTemplate.title}' reward auto-claimed"
                )
                syncManager.saveTransactionDirectly(tx)
            }
        }
    }

    // Deposit flow: credits user wallet directly in demo structure or registers a PENDING transaction
    fun requestDeposit(amount: Double) {
        val user = _currentUser.value ?: return
        val id = "dep_${UUID.randomUUID().toString().substring(0, 6)}"
        val newTx = TransactionRecordEntity(
            id = id,
            emailKey = user.emailKey,
            type = "DEPOSIT",
            amount = amount,
            coins = 0.0,
            status = "PENDING",
            timestamp = System.currentTimeMillis(),
            details = "Request to deposit Rs.${amount} credits"
        )
        syncManager.saveTransactionDirectly(newTx)
        _depositRequestSuccess.value = "Deposit of Rs.${amount} is in queue! Admin will approve shortly."
    }

    // Buy Coins package: uses cash wallets to purchase coin balance vouchers
    fun buyCoins(packageTitle: String, price: Double, coinsYield: Double, bonusYield: Double) {
        val user = _currentUser.value ?: return
        val totalBalance = user.mainWallet + user.winningWallet

        if (totalBalance < price) {
            _depositRequestSuccess.value = "Error: Insufficient balance to purchase voucher! Please deposit cash."
            return
        }

        // Deduct from main/winning wallet
        var remains = price
        var updatedMain = user.mainWallet
        var updatedWinning = user.winningWallet

        if (updatedWinning >= remains) {
            updatedWinning -= remains
            remains = 0.0
        } else {
            remains -= updatedWinning
            updatedWinning = 0.0
        }

        if (remains > 0.0 && updatedMain >= remains) {
            updatedMain -= remains
            remains = 0.0
        }

        val updatedUser = user.copy(
            mainWallet = updatedMain,
            winningWallet = updatedWinning,
            coins = user.coins + coinsYield + bonusYield
        )
        syncManager.saveUserDirectly(updatedUser)

        val txId = "buy_coins_${UUID.randomUUID().toString().substring(0, 8)}"
        val tx = TransactionRecordEntity(
            id = txId,
            emailKey = user.emailKey,
            type = "COIN_PURCHASE",
            amount = -price,
            coins = coinsYield + bonusYield,
            status = "SUCCESS",
            timestamp = System.currentTimeMillis(),
            details = "Purchased $packageTitle (+${bonusYield} Bonus)"
        )
        syncManager.saveTransactionDirectly(tx)
        _depositRequestSuccess.value = "Successfully purchased $packageTitle!"
    }

    fun requestWithdraw(amount: Double, details: String, onError: (String) -> Unit) {
        val user = _currentUser.value ?: return
        if (amount <= 0.0) {
            onError("Please specify a valid amount!")
            return
        }
        if (user.winningWallet < amount) {
            onError("Insufficient withdrawable winnings balance! (Winning: Rs.${user.winningWallet})")
            return
        }

        // Deduct immediately as a temporary lock
        val updatedUser = user.copy(winningWallet = user.winningWallet - amount)
        syncManager.saveUserDirectly(updatedUser)

        val txId = "with_${UUID.randomUUID().toString().substring(0, 6)}"
        val tx = TransactionRecordEntity(
            id = txId,
            emailKey = user.emailKey,
            type = "WITHDRAW",
            amount = -amount,
            coins = 0.0,
            status = "PENDING",
            timestamp = System.currentTimeMillis(),
            details = details
        )
        syncManager.saveTransactionDirectly(tx)
        _depositRequestSuccess.value = "Withdraw request for Rs.${amount} placed successfully!"
    }

    fun clearDepositStatusText() {
        _depositRequestSuccess.value = null
    }

    // ============================================
    // SECURE ADMIN CONTROL OPERATIONS
    // ============================================

    fun adminCreateTournament(t: TournamentEntity) {
        if (_currentUser.value?.isAdmin == true) {
            syncManager.saveTournamentDirectly(t)
        }
    }

    fun adminDeleteTournament(id: String) {
        if (_currentUser.value?.isAdmin == true) {
            syncManager.deleteTournamentDirectly(id)
        }
    }

    fun adminCreateTaskTemplate(task: DailyTaskEntity) {
        if (_currentUser.value?.isAdmin == true) {
            syncManager.saveTaskTemplateDirectly(task)
        }
    }

    fun adminDeleteTaskTemplate(id: String) {
        if (_currentUser.value?.isAdmin == true) {
            syncManager.deleteTaskTemplateDirectly(id)
        }
    }

    fun adminSetUnitySettings(gameId: String, rewardedId: String, interstitialId: String) {
        if (_currentUser.value?.isAdmin == true) {
            val settings = mapOf(
                "unity_game_id" to gameId,
                "unity_rewarded_id" to rewardedId,
                "unity_interstitial_id" to interstitialId
            )
            syncManager.settingsRef.setValue(settings)
        }
    }

    fun adminBanControlUser(emailKey: String, bannedState: Boolean) {
        if (_currentUser.value?.isAdmin == true) {
            FirebaseDatabase.getInstance().getReference("users").child(emailKey)
                .child("banned").setValue(bannedState)
        }
    }

    fun adminApproveTransaction(tx: TransactionRecordEntity) {
        if (_currentUser.value?.isAdmin == true) {
            // Update tx state to APPROVED
            FirebaseDatabase.getInstance().getReference("transactions").child(tx.id)
                .child("status").setValue("APPROVED")

            // If deposit, credit user wallet root
            if (tx.type == "DEPOSIT") {
                FirebaseDatabase.getInstance().getReference("users").child(tx.emailKey)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                val currentWallet = snapshot.child("mainWallet").getValue(Double::class.java) ?: 0.0
                                FirebaseDatabase.getInstance().getReference("users").child(tx.emailKey)
                                    .child("mainWallet").setValue(currentWallet + tx.amount)
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
            } else if (tx.type == "WITHDRAW") {
                // Already deducted. Simply update tx details
                FirebaseDatabase.getInstance().getReference("transactions").child(tx.id)
                    .child("details").setValue("Withdrawal request APPROVED. Transferred successfully.")
            }
        }
    }

    fun adminRejectTransaction(tx: TransactionRecordEntity) {
        if (_currentUser.value?.isAdmin == true) {
            FirebaseDatabase.getInstance().getReference("transactions").child(tx.id)
                .child("status").setValue("REJECTED")

            // If withdraw, refund the deducted amount
            if (tx.type == "WITHDRAW") {
                FirebaseDatabase.getInstance().getReference("users").child(tx.emailKey)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists()) {
                                val currentWinnings = snapshot.child("winningWallet").getValue(Double::class.java) ?: 0.0
                                FirebaseDatabase.getInstance().getReference("users").child(tx.emailKey)
                                    .child("winningWallet").setValue(currentWinnings + Math.abs(tx.amount))
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
            }
        }
    }

    fun adminAdjustUserWallets(emailKey: String, main: Double, bonus: Double, winning: Double, coins: Double) {
        if (_currentUser.value?.isAdmin == true) {
            val ref = FirebaseDatabase.getInstance().getReference("users").child(emailKey)
            ref.child("mainWallet").setValue(main)
            ref.child("bonusWallet").setValue(bonus)
            ref.child("winningWallet").setValue(winning)
            ref.child("coins").setValue(coins)
        }
    }
}
