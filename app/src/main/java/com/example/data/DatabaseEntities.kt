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
    val referCode: String = ""
)

@Entity(tableName = "tournaments")
data class TournamentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val gameType: String,
    val mapType: String,
    val entryFee: Double,
    val prizePool: Double,
    val slotsFilled: Int,
    val totalSlots: Int,
    val adsRequired: Int,
    val scheduleTimeMillis: Long,
    val status: String, // "OPEN", "UPCOMING", "LIVE", "COMPLETED"
    val roomId: String = "",
    val roomPassword: String = ""
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
    val details: String = ""
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
}

@Database(
    entities = [
        UserEntity::class,
        TournamentEntity::class,
        DailyTaskEntity::class,
        TaskProgressEntity::class,
        TransactionRecordEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract val dao: EsportsDao
}
