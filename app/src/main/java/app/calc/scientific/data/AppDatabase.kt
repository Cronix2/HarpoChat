// SPDX-License-Identifier: MIT
package app.calc.scientific.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        ThreadEntity::class, MessageEntity::class, KvEntity::class,
        InviteEntity::class, OutRequestEntity::class, InRequestEntity::class, PeerEntity::class, OutboxEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun threadDao(): ThreadDao
    abstract fun messageDao(): MessageDao
    abstract fun kvDao(): KvDao
    abstract fun pairingDao(): PairingDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        /** Nom de fichier neutre. */
        const val FILE_NAME = "calc_cache.db"

        @Volatile private var nativeLoaded = false

        /** v3 : ajout de contacts et acheminement par le mesh. Conversations et réglages conservés. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `invites` (`id` TEXT NOT NULL, `secret` BLOB NOT NULL, `expiresAt` INTEGER NOT NULL, `inPerson` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `requests_out` (`id` TEXT NOT NULL, `secret` BLOB NOT NULL, `ePrivate` BLOB NOT NULL, `ePublic` BLOB NOT NULL, `kB` BLOB NOT NULL, `sas` TEXT NOT NULL, `inPerson` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `requests_in` (`id` TEXT NOT NULL, `inviteId` TEXT NOT NULL, `ePublic` BLOB NOT NULL, `kB` BLOB NOT NULL, `name` TEXT NOT NULL, `sas` TEXT NOT NULL, `receivedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `peers` (`threadId` TEXT NOT NULL, `boxOut` BLOB NOT NULL, `boxIn` BLOB NOT NULL, `keyOut` BLOB NOT NULL, `keyIn` BLOB NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`threadId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `outbox` (`id` TEXT NOT NULL, `box` BLOB NOT NULL, `blob` BLOB NOT NULL, `messageId` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

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
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
