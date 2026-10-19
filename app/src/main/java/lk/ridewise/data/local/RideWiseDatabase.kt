package lk.ridewise.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "analyses")
data class AnalysisEntity(@PrimaryKey val id: String, val analyzedAt: Long, val snapshot: String)

@Entity(tableName = "vehicle_settings")
data class SettingsEntity(@PrimaryKey val id: Int = 1, val snapshot: String)

@Dao
interface RideWiseDao {
    @Query("SELECT * FROM analyses ORDER BY analyzedAt DESC") fun observeHistory(): Flow<List<AnalysisEntity>>
    @Upsert suspend fun saveAnalysis(entity: AnalysisEntity)
    @Query("DELETE FROM analyses WHERE id = :id") suspend fun deleteAnalysis(id: String)
    @Query("DELETE FROM analyses") suspend fun clearHistory()
    @Query("SELECT * FROM vehicle_settings WHERE id = 1") fun observeSettings(): Flow<SettingsEntity?>
    @Upsert suspend fun saveSettings(entity: SettingsEntity)
}

@Database(entities = [AnalysisEntity::class, SettingsEntity::class], version = 1, exportSchema = true)
abstract class RideWiseDatabase : RoomDatabase() { abstract fun dao(): RideWiseDao }
