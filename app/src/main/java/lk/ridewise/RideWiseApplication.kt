package lk.ridewise

import android.app.Application
import androidx.room.Room
import lk.ridewise.capture.screencapture.CaptureSession
import lk.ridewise.data.local.RideWiseDatabase
import lk.ridewise.data.repository.RoomRideRepository
import lk.ridewise.live.LiveAssistantController
import lk.ridewise.live.LiveAssistantStore

class RideWiseApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, RideWiseDatabase::class.java, "ridewise.db").build() }
    val repository by lazy { RoomRideRepository(database.dao()) }
    val captureSession = CaptureSession()
    val automaticSession = lk.ridewise.capture.automatic.AutomaticSession()
    val liveStore by lazy { LiveAssistantStore(this) }
    val liveAssistant by lazy { LiveAssistantController(this, liveStore) }
}
