package com.jiexian.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jiexian.app.model.ActiveLock
import com.jiexian.app.model.AppRule
import com.jiexian.app.model.AppTarget

@Database(entities = [AppRule::class, ActiveLock::class], version = 2, exportSchema = false)
@TypeConverters(RuleTargetsConverter::class)
abstract class JieXianDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao

    companion object {
        @Volatile private var instance: JieXianDatabase? = null

        fun get(context: android.content.Context): JieXianDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        JieXianDatabase::class.java,
                        "jiexian.db",
                    ).addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}

val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `app_rules_new` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `targets` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `limitMinutes` INTEGER NOT NULL,
                    `startMinute` INTEGER NOT NULL,
                    `endMinute` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            val converter = RuleTargetsConverter()
            db.query("SELECT * FROM `app_rules`").use { cursor ->
                while (cursor.moveToNext()) {
                    val appName = cursor.getString(cursor.getColumnIndexOrThrow("appName"))
                    val target =
                        AppTarget(
                            packageName = cursor.getString(cursor.getColumnIndexOrThrow("packageName")),
                            appName = appName,
                        )
                    val values =
                        ContentValues().apply {
                            put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")))
                            put("name", appName)
                            put("targets", converter.fromTargets(listOf(target)))
                            put("type", cursor.getString(cursor.getColumnIndexOrThrow("type")))
                            put("limitMinutes", cursor.getInt(cursor.getColumnIndexOrThrow("limitMinutes")))
                            put("startMinute", cursor.getInt(cursor.getColumnIndexOrThrow("startMinute")))
                            put("endMinute", cursor.getInt(cursor.getColumnIndexOrThrow("endMinute")))
                            put("createdAt", cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")))
                        }
                    db.insert("app_rules_new", SQLiteDatabase.CONFLICT_ABORT, values)
                }
            }
            db.execSQL("DROP TABLE `app_rules`")
            db.execSQL("ALTER TABLE `app_rules_new` RENAME TO `app_rules`")

            // Rebuild instead of adding a SQL default so Room's expected schema stays exact.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `active_locks_new` (
                    `id` TEXT NOT NULL,
                    `sourceRuleId` TEXT NOT NULL,
                    `ruleName` TEXT NOT NULL,
                    `packageName` TEXT NOT NULL,
                    `appName` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `unlockAt` INTEGER NOT NULL,
                    `detail` TEXT NOT NULL,
                    `startedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO `active_locks_new` (
                    `id`, `sourceRuleId`, `ruleName`, `packageName`, `appName`,
                    `type`, `unlockAt`, `detail`, `startedAt`
                )
                SELECT `id`, `sourceRuleId`, `appName`, `packageName`, `appName`,
                    `type`, `unlockAt`, `detail`, `startedAt`
                FROM `active_locks`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `active_locks`")
            db.execSQL("ALTER TABLE `active_locks_new` RENAME TO `active_locks`")
        }
    }
