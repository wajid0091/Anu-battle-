package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val emailKey: String,
    val name: String,
    val email: String,
    val mainWallet: Double,
    val bonusWallet: Double,
    val winningWallet: Double,
    val coins: Double,
    val matchesPlayed: Int,
    val matchesWon: Int,
    val banned: Boolean = false,
    val isAdmin: Boolean = false,
    val password: String = "",
    val gameUid: String = "",
    val referCode: String = "",
    val referredBy: String = "",
    val deviceId: String = "",
    val totalReferrals: Int = 0,
    val inboxMessage: String = "",
    val lastGameUidChangeTime: Long = 0L,
    val joinedTournaments: String = "",
    val dailyRewardDay: Int = 1,
    val lastDailyRewardTime: Long = 0L
)

@Entity(tableName = "tournaments")
data class TournamentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val gameType: String,
    val mapType: String,
    val entryFee: Double,
    val prizePool: Double,
    val perKillPrize: Double = 0.0,
    val rankPrizes: String = "", // comma-separated like "1000,500,250"
    val slotsFilled: Int,
    val totalSlots: Int,
    val adsRequired: Int,
    val scheduleTimeMillis: Long,
    val status: String, // "OPEN", "UPCOMING", "LIVE", "COMPLETED"
    val roomId: String = "",
    val roomPassword: String = "",
    val bannerUrl: String = "",
    val description: String = ""
)

@Entity(tableName = "app_config")
data class AppConfigEntity(
    @PrimaryKey val id: String = "config",
    val referCoinReward: Double = 0.0,
    val referCashReward: Double = 0.0
)

@Entity(tableName = "promo_sliders")
data class PromoSliderEntity(
    @PrimaryKey val id: String,
    val imageUrl: String,
    val title: String = "",
    val actionUrl: String = "",
    val active: Boolean = true
)

@Entity(tableName = "daily_tasks")
data class DailyTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val taskType: String, // "WATCH_AD", "PLAY_MINS", "REFER", "WIN_MATCH", "JOIN_TOURNEY"
    val targetValue: Int,
    val coinReward: Double
)

@Entity(tableName = "task_progress")
data class TaskProgressEntity(
    @PrimaryKey val compositeKey: String, // emailKey_taskId
    val emailKey: String,
    val taskId: String,
    val currentValue: Int,
    val claimed: Boolean
)

@Entity(tableName = "transaction_records")
data class TransactionRecordEntity(
    @PrimaryKey val id: String,
    val emailKey: String,
    val type: String, // "DEPOSIT", "WITHDRAW", "COIN_PURCHASE", "REWARD_CLAIM", "JOIN"
    val amount: Double,
    val coins: Double = 0.0,
    val status: String, // "PENDING", "APPROVED", "REJECTED", "SUCCESS"
    val timestamp: Long,
    val details: String = "",
    val screenshotUrl: String = ""
)

@Entity(tableName = "diamond_packages")
data class DiamondPackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val coinCost: Int
)

@Dao
interface EsportsDao {
    @Query("SELECT * FROM users WHERE emailKey = :emailKey")
    suspend fun getUserSync(emailKey: String): UserEntity?

    @Query("SELECT * FROM users WHERE emailKey = :emailKey")
    fun getUserFlow(emailKey: String): Flow<UserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM users")
    fun getAllUsersFlow(): Flow<List<UserEntity>>

    @Query("DELETE FROM users")
    suspend fun clearUsers()

    // Tournaments
    @Query("SELECT * FROM tournaments ORDER BY scheduleTimeMillis ASC")
    fun getAllTournamentsFlow(): Flow<List<TournamentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTournaments(tournaments: List<TournamentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTournament(tournament: TournamentEntity)

    @Query("DELETE FROM tournaments WHERE id = :id")
    suspend fun deleteTournamentById(id: String)

    @Query("DELETE FROM tournaments")
    suspend fun clearTournaments()

    // Daily Tasks
    @Query("SELECT * FROM daily_tasks")
    fun getAllDailyTasksFlow(): Flow<List<DailyTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyTasks(tasks: List<DailyTaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyTask(task: DailyTaskEntity)

    @Query("DELETE FROM daily_tasks WHERE id = :id")
    suspend fun deleteDailyTaskById(id: String)

    @Query("DELETE FROM daily_tasks")
    suspend fun clearDailyTasks()

    // Diamond Packages
    @Query("SELECT * FROM diamond_packages")
    fun getAllDiamondPacksFlow(): Flow<List<DiamondPackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiamondPacks(packs: List<DiamondPackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiamondPack(pack: DiamondPackEntity)

    @Query("DELETE FROM diamond_packages WHERE id = :id")
    suspend fun deleteDiamondPackById(id: String)

    @Query("DELETE FROM diamond_packages")
    suspend fun clearDiamondPacks()

    // Task Progress
    @Query("SELECT * FROM task_progress WHERE emailKey = :emailKey")
    fun getTaskProgressFlow(emailKey: String): Flow<List<TaskProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskProgresses(progress: List<TaskProgressEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskProgress(progress: TaskProgressEntity)

    @Query("DELETE FROM task_progress")
    suspend fun clearTaskProgress()

    // Transactions
    @Query("SELECT * FROM transaction_records ORDER BY timestamp DESC")
    fun getAllTransactionsFlow(): Flow<List<TransactionRecordEntity>>

    @Query("SELECT * FROM transaction_records WHERE emailKey = :emailKey ORDER BY timestamp DESC")
    fun getTransactionsForUserFlow(emailKey: String): Flow<List<TransactionRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionRecordEntity)

    @Query("DELETE FROM transaction_records")
    suspend fun clearTransactions()

    // Config
    @Query("SELECT * FROM app_config WHERE id = 'config'")
    fun getAppConfigFlow(): Flow<AppConfigEntity?>

    @Query("SELECT * FROM app_config WHERE id = 'config'")
    suspend fun getAppConfigSync(): AppConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAppConfig(config: AppConfigEntity)

    // Promo Sliders
    @Query("SELECT * FROM promo_sliders WHERE active = 1")
    fun getActivePromoSlidersFlow(): Flow<List<PromoSliderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPromoSliders(sliders: List<PromoSliderEntity>)

    @Query("DELETE FROM promo_sliders")
    suspend fun clearPromoSliders()
}

@Database(
    entities = [
        UserEntity::class,
        TournamentEntity::class,
        DailyTaskEntity::class,
        TaskProgressEntity::class,
        TransactionRecordEntity::class,
        PromoSliderEntity::class,
        DiamondPackEntity::class,
        AppConfigEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract val dao: EsportsDao
}
