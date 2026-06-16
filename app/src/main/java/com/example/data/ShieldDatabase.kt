package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- Room Entities ---

@Entity(tableName = "shield_settings")
data class ShieldSetting(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "alert_logs")
data class AlertLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val appName: String,
    val detectedType: String, // "DNS Filter", "Text Keyword", "AI Image Scan", "App Block"
    val contentSnippet: String,
    val status: String,       // "BLOCKED", "WARNED"
    val screenshotAsset: String? = null // To display simulated image block
)

@Entity(tableName = "blocked_rules")
data class BlockedRule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val value: String,        // e.g. "instagram.com/xxx", "violence"
    val ruleType: String      // "URL" or "KEYWORD"
)

// --- Daos ---

@Dao
interface ShieldDao {
    // Settings Queries
    @Query("SELECT * FROM shield_settings")
    fun getAllSettingsFlow(): Flow<List<ShieldSetting>>

    @Query("SELECT * FROM shield_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSettingByKey(key: String): ShieldSetting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: ShieldSetting)

    @Query("DELETE FROM shield_settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String)

    // Alert Log Queries
    @Query("SELECT * FROM alert_logs ORDER BY timestamp DESC")
    fun getAllAlertLogsFlow(): Flow<List<AlertLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlertLog(log: AlertLog)

    @Query("DELETE FROM alert_logs")
    suspend fun clearAlertLogs()

    // Blocked Rule Queries
    @Query("SELECT * FROM blocked_rules ORDER BY id DESC")
    fun getAllRulesFlow(): Flow<List<BlockedRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: BlockedRule)

    @Query("DELETE FROM blocked_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Int)
}

// --- App Database ---

@Database(entities = [ShieldSetting::class, AlertLog::class, BlockedRule::class], version = 1, exportSchema = false)
abstract class ShieldDatabase : RoomDatabase() {
    abstract val shieldDao: ShieldDao

    companion object {
        @Volatile
        private var INSTANCE: ShieldDatabase? = null

        fun getDatabase(context: Context): ShieldDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShieldDatabase::class.java,
                    "shield_database.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- Repository Implementation ---

class ShieldRepository(private val shieldDao: ShieldDao) {
    val allSettings: Flow<List<ShieldSetting>> = shieldDao.getAllSettingsFlow()
    val allAlertLogs: Flow<List<AlertLog>> = shieldDao.getAllAlertLogsFlow()
    val allRules: Flow<List<BlockedRule>> = shieldDao.getAllRulesFlow()

    suspend fun getSetting(key: String): String? {
        return shieldDao.getSettingByKey(key)?.value
    }

    suspend fun setSetting(key: String, value: String) {
        shieldDao.insertSetting(ShieldSetting(key, value))
    }

    suspend fun logAlert(appName: String, detectedType: String, contentSnippet: String, status: String, screenshotAsset: String? = null) {
        shieldDao.insertAlertLog(
            AlertLog(
                appName = appName,
                detectedType = detectedType,
                contentSnippet = contentSnippet,
                status = status,
                screenshotAsset = screenshotAsset
            )
        )
    }

    suspend fun clearLogs() {
        shieldDao.clearAlertLogs()
    }

    suspend fun addRule(value: String, ruleType: String) {
        shieldDao.insertRule(BlockedRule(value = value, ruleType = ruleType))
    }

    suspend fun removeRule(id: Int) {
        shieldDao.deleteRuleById(id)
    }
}
