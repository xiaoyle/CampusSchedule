package cn.campus.schedule

import android.app.Application
import android.content.Context
import androidx.room.*
import cn.campus.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString

@Entity(tableName="app_state") data class StateRow(@PrimaryKey val id: Int = 1, val payload: String)
@Dao interface StateDao {
    @Query("SELECT * FROM app_state WHERE id = 1") fun observe(): Flow<StateRow?>
    @Query("SELECT * FROM app_state WHERE id = 1") suspend fun read(): StateRow?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun put(row: StateRow)
}
@Database(entities=[StateRow::class], version=1, exportSchema=false)
abstract class ScheduleDatabase : RoomDatabase() { abstract fun state(): StateDao }

class Store(context: Context) {
    private val db = Room.databaseBuilder(context.applicationContext, ScheduleDatabase::class.java, "schedule.db").build()
    val data: Flow<AppData> = db.state().observe().map { decode(it) }
    private fun decode(row: StateRow?): AppData = row?.let { dataJson.decodeFromString<AppData>(it.payload) } ?: AppData()
    suspend fun read() = decode(db.state().read())
    suspend fun update(block: (AppData) -> AppData) = db.withTransaction {
        db.state().put(StateRow(payload=dataJson.encodeToString(block(read()))))
    }
}
class ScheduleApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val store by lazy { Store(this) }
    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.createChannel(this)
        RecoveryWorker.install(this)
        scope.launch { runCatching { ReminderScheduler.refresh(this@ScheduleApp) } }
    }
}
val Context.scheduleApp get() = applicationContext as ScheduleApp
