// SPDX-License-Identifier: MIT
package app.calc.scientific.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [ThreadEntity::class, MessageEntity::class, KvEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun threadDao(): ThreadDao
    abstract fun messageDao(): MessageDao
    abstract fun kvDao(): KvDao

    companion object {
        /** Nom de fichier neutre. */
        const val FILE_NAME = "calc_cache.db"

        @Volatile private var nativeLoaded = false

        /**
         * Ouvre la base chiffrée avec [key] (32 octets issus du coffre).
         * Appelé uniquement au déverrouillage ; la base est fermée au verrouillage.
         */
        fun open(ctx: Context, key: ByteArray): AppDatabase {
            if (!nativeLoaded) {
                System.loadLibrary("sqlcipher")
                nativeLoaded = true
            }
            return Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, FILE_NAME)
                .openHelperFactory(SupportOpenHelperFactory(key))
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        // Les lignes supprimées sont écrasées par des zéros, pas juste « libérées ».
                        db.query("PRAGMA secure_delete = ON").use { it.moveToFirst() }
                    }
                })
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
