package lk.ridewise.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import lk.ridewise.data.local.*
import lk.ridewise.domain.model.*

interface RideRepository {
    val history: Flow<List<RideAnalysis>>
    val settings: Flow<VehicleCostSettings>
    suspend fun save(analysis: RideAnalysis)
    suspend fun delete(id: String)
    suspend fun clear()
    suspend fun updateSettings(settings: VehicleCostSettings)
}

class RoomRideRepository(private val dao: RideWiseDao) : RideRepository {
    override val history = dao.observeHistory().map { entities -> entities.map { SnapshotCodec.decode(it.snapshot) } }
    override val settings = dao.observeSettings().map { it?.let { SnapshotCodec.settings(it.snapshot) } ?: VehicleCostSettings() }
    override suspend fun save(analysis: RideAnalysis) = dao.saveAnalysis(AnalysisEntity(analysis.id, analysis.analyzedAt, SnapshotCodec.encode(analysis)))
    override suspend fun delete(id: String) = dao.deleteAnalysis(id)
    override suspend fun clear() = dao.clearHistory()
    override suspend fun updateSettings(settings: VehicleCostSettings) = dao.saveSettings(SettingsEntity(snapshot = SnapshotCodec.settingsJson(settings)))
}
