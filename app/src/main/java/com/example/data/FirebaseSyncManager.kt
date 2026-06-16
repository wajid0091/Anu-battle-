package com.example.data

import android.content.Context
import android.util.Log
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FirebaseSyncManager(
    private val context: Context,
    private val dao: EsportsDao,
    private val scope: CoroutineScope
) {
    private val database = FirebaseDatabase.getInstance()
    private val usersRef = database.getReference("users")
    private val tournamentsRef = database.getReference("tournaments")
    private val tasksRef = database.getReference("daily_task_templates")
    private val transactionsRef = database.getReference("transactions")
    private val promosRef = database.getReference("promo_sliders")
    val settingsRef = database.getReference("settings")
    
    private var activeUsersListener: ValueEventListener? = null
    private var tournamentsListener: ValueEventListener? = null
    private var tasksListener: ValueEventListener? = null
    private var transactionsListener: ValueEventListener? = null
    private var promosListener: ValueEventListener? = null
    private var progressListener: ValueEventListener? = null

    init {
        startMetadataSync()
        seedDefaultsIfEmpty()
    }

    fun startUserAndProgressSync(emailKey: String) {
        stopUserAndProgressSync()

        Log.d("FirebaseSync", "Starting real-time sync for $emailKey")
        
        val userRef = usersRef.child(emailKey)
        activeUsersListener = userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val user = snapshot.toUserEntity()
                    user?.let {
                        scope.launch(Dispatchers.IO) {
                            dao.insertUser(it)
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseSync", "User sync cancelled: ${error.message}")
            }
        })

        // Listen to task progress for this user
        val progressRef = database.getReference("task_progress").child(emailKey)
        progressListener = progressRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val progressList = mutableListOf<TaskProgressEntity>()
                for (child in snapshot.children) {
                    val taskId = child.key ?: continue
                    val currentValue = child.child("currentValue").getValue(Int::class.java) ?: 0
                    val claimed = child.child("claimed").getValue(Boolean::class.java) ?: false
                    progressList.add(
                        TaskProgressEntity(
                            compositeKey = "${emailKey}_$taskId",
                            emailKey = emailKey,
                            taskId = taskId,
                            currentValue = currentValue,
                            claimed = claimed
                        )
                    )
                }
                scope.launch(Dispatchers.IO) {
                    dao.insertTaskProgresses(progressList)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun stopUserAndProgressSync() {
        activeUsersListener?.let { usersRef.removeEventListener(it) }
        progressListener?.let { database.getReference("task_progress").removeEventListener(it) }
    }

    private fun startMetadataSync() {
        tournamentsListener = tournamentsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<TournamentEntity>()
                for (child in snapshot.children) {
                    val t = child.toTournamentEntity()
                    if (t != null) list.add(t)
                }
                scope.launch(Dispatchers.IO) {
                    dao.clearTournaments()
                    dao.insertTournaments(list)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        tasksListener = tasksRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<DailyTaskEntity>()
                for (child in snapshot.children) {
                    val task = child.toDailyTaskEntity()
                    if (task != null) list.add(task)
                }
                scope.launch(Dispatchers.IO) {
                    dao.clearDailyTasks()
                    dao.insertDailyTasks(list)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        transactionsListener = transactionsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<TransactionRecordEntity>()
                for (child in snapshot.children) {
                    val tx = child.toTransactionRecordEntity()
                    if (tx != null) list.add(tx)
                }
                scope.launch(Dispatchers.IO) {
                    dao.clearTransactions()
                    dao.insertTransactions(list)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        promosListener = promosRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<PromoSliderEntity>()
                for (child in snapshot.children) {
                    val p = child.toPromoSliderEntity()
                    if (p != null) list.add(p)
                }
                scope.launch(Dispatchers.IO) {
                    dao.clearPromoSliders()
                    dao.insertPromoSliders(list)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun seedDefaultsIfEmpty() {
        settingsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    val defaults = mapOf(
                        "unity_game_id" to "5763487",
                        "unity_rewarded_id" to "Rewarded_Android",
                        "unity_interstitial_id" to "Interstitial_Android"
                    )
                    settingsRef.setValue(defaults)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        tournamentsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    val now = System.currentTimeMillis()
                    val initialTournaments = listOf(
                        TournamentEntity("match_01", "BGMI Squad Master Series S1", "BGMI", "Sanhok", 30.0, 5000.0, 25, 25, 3, now - 3600000, "COMPLETED", "987213", "pass88"),
                        TournamentEntity("match_02", "Apex Custom Legends Cup", "Apex Legends", "World's Edge", 25.0, 2500.0, 14, 20, 2, now + 1200000, "UPCOMING", "554611", "apex99"),
                        TournamentEntity("match_03", "CODM Lone Wolf Duel League", "Call of Duty", "Nuketown", 20.0, 1500.0, 8, 8, 0, now + 3000000, "OPEN", "771212", "codm33"),
                        TournamentEntity("match_04", "Free Fire Solo Survival Arena", "Free Fire", "Bermuda", 0.0, 1000.0, 12, 100, 4, now + 14400000, "OPEN", "112233", "ff99")
                    )
                    for (t in initialTournaments) {
                        tournamentsRef.child(t.id).setValue(t)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        tasksRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    val initialTasks = listOf(
                        DailyTaskEntity("task_01", "Play Game 2 Minutes", "PLAY_MINS", 2, 5.0),
                        DailyTaskEntity("task_02", "Play Game 15 Minutes", "PLAY_MINS", 15, 10.0),
                        DailyTaskEntity("task_03", "Play Game 20 Minutes", "PLAY_MINS", 20, 15.0),
                        DailyTaskEntity("task_04", "Join 1 Tournament", "JOIN_TOURNEY", 1, 8.0),
                        DailyTaskEntity("task_05", "Win 1 Tournament Match", "WIN_MATCH", 1, 25.0),
                        DailyTaskEntity("task_06", "Refer 1 Friend", "REFER", 1, 15.0)
                    )
                    for (task in initialTasks) {
                        tasksRef.child(task.id).setValue(task)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun saveUserDirectly(user: UserEntity) {
        usersRef.child(user.emailKey).setValue(user)
    }

    fun saveTournamentDirectly(tournament: TournamentEntity) {
        tournamentsRef.child(tournament.id).setValue(tournament)
    }

    fun deleteTournamentDirectly(id: String) {
        tournamentsRef.child(id).removeValue()
    }

    fun saveTaskTemplateDirectly(task: DailyTaskEntity) {
        tasksRef.child(task.id).setValue(task)
    }

    fun deleteTaskTemplateDirectly(id: String) {
        tasksRef.child(id).removeValue()
    }

    fun saveTaskProgressDirectly(progress: TaskProgressEntity) {
        val ref = database.getReference("task_progress")
            .child(progress.emailKey)
            .child(progress.taskId)
        
        val values = mapOf(
            "currentValue" to progress.currentValue,
            "claimed" to progress.claimed
        )
        ref.setValue(values)
    }

    fun saveTransactionDirectly(tx: TransactionRecordEntity) {
        transactionsRef.child(tx.id).setValue(tx)
    }

    fun savePromoSliderDirectly(promo: PromoSliderEntity) {
        promosRef.child(promo.id).setValue(promo)
    }

    fun deletePromoSliderDirectly(id: String) {
        promosRef.child(id).removeValue()
    }
}

private fun DataSnapshot.toUserEntity(): UserEntity? {
    return try {
        val emailKey = key ?: ""
        val name = child("name").getValue(String::class.java) ?: ""
        val email = child("email").getValue(String::class.java) ?: ""
        val mainWallet = child("mainWallet").getValue(Double::class.java) ?: 0.0
        val bonusWallet = child("bonusWallet").getValue(Double::class.java) ?: 0.0
        val winningWallet = child("winningWallet").getValue(Double::class.java) ?: 0.0
        val coins = child("coins").getValue(Double::class.java) ?: 0.0
        val matchesPlayed = child("matchesPlayed").getValue(Int::class.java) ?: 0
        val matchesWon = child("matchesWon").getValue(Int::class.java) ?: 0
        val banned = child("banned").getValue(Boolean::class.java) ?: false
        val isAdmin = child("isAdmin").getValue(Boolean::class.java) ?: false
        val password = child("password").getValue(String::class.java) ?: ""
        val gameUid = child("gameUid").getValue(String::class.java) ?: ""
        val referCode = child("referCode").getValue(String::class.java) ?: ""
        val inboxMessage = child("inboxMessage").getValue(String::class.java) ?: ""
        val lastGameUidChangeTime = child("lastGameUidChangeTime").getValue(Long::class.java) ?: 0L
        val joinedTournaments = child("joinedTournaments").getValue(String::class.java) ?: ""
        val dailyRewardDay = child("dailyRewardDay").getValue(Int::class.java) ?: 1
        val lastDailyRewardTime = child("lastDailyRewardTime").getValue(Long::class.java) ?: 0L
        UserEntity(emailKey, name, email, mainWallet, bonusWallet, winningWallet, coins, matchesPlayed, matchesWon, banned, isAdmin, password, gameUid, referCode, inboxMessage, lastGameUidChangeTime, joinedTournaments, dailyRewardDay, lastDailyRewardTime)
    } catch (e: Exception) {
        null
    }
}

private fun DataSnapshot.toTournamentEntity(): TournamentEntity? {
    return try {
        val id = key ?: ""
        val title = child("title").getValue(String::class.java) ?: ""
        val gameType = child("gameType").getValue(String::class.java) ?: ""
        val mapType = child("mapType").getValue(String::class.java) ?: ""
        val entryFee = child("entryFee").getValue(Double::class.java) ?: 0.0
        val prizePool = child("prizePool").getValue(Double::class.java) ?: 0.0
        val slotsFilled = child("slotsFilled").getValue(Int::class.java) ?: 0
        val totalSlots = child("totalSlots").getValue(Int::class.java) ?: 0
        val adsRequired = child("adsRequired").getValue(Int::class.java) ?: 0
        val scheduleTimeMillis = child("scheduleTimeMillis").getValue(Long::class.java) ?: 0L
        val status = child("status").getValue(String::class.java) ?: "OPEN"
        val roomId = child("roomId").getValue(String::class.java) ?: ""
        val roomPassword = child("roomPassword").getValue(String::class.java) ?: ""
        val bannerUrl = child("bannerUrl").getValue(String::class.java) ?: ""
        val description = child("description").getValue(String::class.java) ?: ""
        TournamentEntity(id, title, gameType, mapType, entryFee, prizePool, slotsFilled, totalSlots, adsRequired, scheduleTimeMillis, status, roomId, roomPassword, bannerUrl, description)
    } catch (e: Exception) {
        null
    }
}

private fun DataSnapshot.toDailyTaskEntity(): DailyTaskEntity? {
    return try {
        val id = key ?: ""
        val title = child("title").getValue(String::class.java) ?: ""
        val taskType = child("taskType").getValue(String::class.java) ?: ""
        val targetValue = child("targetValue").getValue(Int::class.java) ?: 1
        val coinReward = child("coinReward").getValue(Double::class.java) ?: 0.0
        DailyTaskEntity(id, title, taskType, targetValue, coinReward)
    } catch (e: Exception) {
        null
    }
}

private fun DataSnapshot.toTransactionRecordEntity(): TransactionRecordEntity? {
    return try {
        val id = key ?: ""
        val emailKey = child("emailKey").getValue(String::class.java) ?: ""
        val type = child("type").getValue(String::class.java) ?: ""
        val amount = child("amount").getValue(Double::class.java) ?: 0.0
        val coins = child("coins").getValue(Double::class.java) ?: 0.0
        val status = child("status").getValue(String::class.java) ?: ""
        val timestamp = child("timestamp").getValue(Long::class.java) ?: 0L
        val details = child("details").getValue(String::class.java) ?: ""
        val screenshotUrl = child("screenshotUrl").getValue(String::class.java) ?: ""
        TransactionRecordEntity(id, emailKey, type, amount, coins, status, timestamp, details, screenshotUrl)
    } catch (e: Exception) {
        null
    }
}

private fun DataSnapshot.toPromoSliderEntity(): PromoSliderEntity? {
    return try {
        val id = key ?: ""
        val imageUrl = child("imageUrl").getValue(String::class.java) ?: ""
        val title = child("title").getValue(String::class.java) ?: ""
        val actionUrl = child("actionUrl").getValue(String::class.java) ?: ""
        val active = child("active").getValue(Boolean::class.java) ?: true
        PromoSliderEntity(id, imageUrl, title, actionUrl, active)
    } catch (e: Exception) {
        null
    }
}
