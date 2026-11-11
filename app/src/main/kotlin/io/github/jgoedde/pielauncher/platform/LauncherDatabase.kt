package io.github.jgoedde.pielauncher.platform

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import io.github.jgoedde.pielauncher.core.drawer.NewBadge
import io.github.jgoedde.pielauncher.core.shortcuts.ShortcutKey
import io.github.jgoedde.pielauncher.core.shortcuts.StoredPin
import kotlinx.coroutines.flow.Flow

/** App Drawer state (ADR 0002): pinned Shortcut records and New Badges. */
@Database(entities = [PinnedShortcutRow::class, NewBadgeRow::class], version = 1)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun pinnedShortcuts(): PinnedShortcutDao

    abstract fun newBadges(): NewBadgeDao
}

/** A Shortcut this launcher pinned, kept even while the system cannot report it (ADR 0002, PRD 7.7). */
@Entity(tableName = "pinned_shortcut", primaryKeys = ["package_name", "shortcut_id", "user_serial"])
data class PinnedShortcutRow(
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "shortcut_id") val shortcutId: String,
    @ColumnInfo(name = "user_serial") val userSerial: Long,
    val label: String,
) {
    fun toStoredPin() = StoredPin(ShortcutKey(packageName, shortcutId, userSerial), label)

    companion object {
        fun of(pin: StoredPin) = PinnedShortcutRow(pin.key.packageName, pin.key.id, pin.key.userSerial, pin.label)
    }
}

@Dao
interface PinnedShortcutDao {
    @Query("SELECT * FROM pinned_shortcut")
    fun observeAll(): Flow<List<PinnedShortcutRow>>

    @Query("SELECT * FROM pinned_shortcut")
    suspend fun all(): List<PinnedShortcutRow>

    @Upsert
    suspend fun upsert(rows: List<PinnedShortcutRow>)

    @Query("DELETE FROM pinned_shortcut WHERE package_name = :packageName AND shortcut_id = :shortcutId AND user_serial = :userSerial")
    suspend fun delete(
        packageName: String,
        shortcutId: String,
        userSerial: Long,
    )
}

@Entity(tableName = "new_badge")
data class NewBadgeRow(
    @PrimaryKey @ColumnInfo(name = "entry_key") val entryKey: String,
    @ColumnInfo(name = "added_at_millis") val addedAtMillis: Long,
) {
    fun toNewBadge() = NewBadge(entryKey, addedAtMillis)
}

@Dao
interface NewBadgeDao {
    @Query("SELECT * FROM new_badge")
    fun observeAll(): Flow<List<NewBadgeRow>>

    /** A fresh install of an app that was badged before restarts its badge. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(rows: List<NewBadgeRow>)

    @Query("DELETE FROM new_badge WHERE entry_key = :entryKey")
    suspend fun delete(entryKey: String)

    @Query("DELETE FROM new_badge WHERE added_at_millis <= :cutoffMillis")
    suspend fun deleteAddedUpTo(cutoffMillis: Long)
}
