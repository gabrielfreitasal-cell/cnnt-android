package com.cnnt.app.data.dao

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(
    entities = [
        NotebookEntity::class,
        BoardEntity::class,
        LayerEntity::class,
        StrokeEntity::class,
        SpatialObjectEntity::class,
        FlashcardEntity::class,
        PaletteEntity::class,
        BrushPresetEntity::class,
        ContentBlockEntity::class,
        LinkEdgeEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CnntDatabase : RoomDatabase() {
    abstract fun notebookDao(): NotebookDao
    abstract fun boardDao(): BoardDao
    abstract fun strokeDao(): StrokeDao
    abstract fun spatialObjectDao(): SpatialObjectDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun contentBlockDao(): ContentBlockDao
    abstract fun linkEdgeDao(): LinkEdgeDao

    companion object {
        @Volatile
        private var INSTANCE: CnntDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `content_blocks` (
                        `id` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `posX` REAL NOT NULL,
                        `posY` REAL NOT NULL,
                        `width` REAL NOT NULL,
                        `height` REAL NOT NULL,
                        `rotation` REAL NOT NULL,
                        `zIndex` INTEGER NOT NULL,
                        `contentJson` TEXT NOT NULL,
                        `notebookId` TEXT NOT NULL,
                        `layerId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`notebookId`) REFERENCES `notebooks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`layerId`) REFERENCES `layers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_content_blocks_notebookId` ON `content_blocks` (`notebookId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_content_blocks_layerId` ON `content_blocks` (`layerId`)")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `link_edges` (
                        `id` TEXT NOT NULL,
                        `sourceBlockId` TEXT NOT NULL,
                        `targetBlockId` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `style` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`sourceBlockId`) REFERENCES `content_blocks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`targetBlockId`) REFERENCES `content_blocks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_link_edges_sourceBlockId` ON `link_edges` (`sourceBlockId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_link_edges_targetBlockId` ON `link_edges` (`targetBlockId`)")

                database.execSQL(
                    """
                    INSERT OR IGNORE INTO content_blocks
                    (id, type, posX, posY, width, height, rotation, zIndex, contentJson, notebookId, layerId, createdAt, updatedAt)
                    SELECT
                        so.id,
                        CASE so.type
                            WHEN 'IMAGE' THEN 'IMAGE'
                            WHEN 'PDF' THEN 'PDF'
                            ELSE 'TEXT'
                        END,
                        so.x,
                        so.y,
                        MAX(so.width, 160.0),
                        MAX(so.height, 100.0),
                        so.rotation,
                        so.zIndex,
                        so.contentJson,
                        b.notebookId,
                        so.layerId,
                        so.createdAt,
                        so.updatedAt
                    FROM spatial_objects so
                    INNER JOIN layers l ON l.id = so.layerId
                    INNER JOIN boards b ON b.id = l.boardId
                    WHERE so.type IN ('TEXT', 'IMAGE', 'PDF')
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    INSERT OR IGNORE INTO content_blocks
                    (id, type, posX, posY, width, height, rotation, zIndex, contentJson, notebookId, layerId, createdAt, updatedAt)
                    SELECT
                        COALESCE(NULLIF(f.linkedRegionId, ''), 'flashcard_' || f.id),
                        'FLASHCARD',
                        COALESCE(
                            (SELECT so.x FROM spatial_objects so WHERE so.id = f.linkedRegionId LIMIT 1),
                            96.0 + ((f.createdAt / 37) % 5) * 48.0
                        ),
                        COALESCE(
                            (SELECT so.y FROM spatial_objects so WHERE so.id = f.linkedRegionId LIMIT 1),
                            96.0 + ((f.createdAt / 53) % 7) * 36.0
                        ),
                        COALESCE((SELECT so.width FROM spatial_objects so WHERE so.id = f.linkedRegionId LIMIT 1), 260.0),
                        COALESCE((SELECT so.height FROM spatial_objects so WHERE so.id = f.linkedRegionId LIMIT 1), 170.0),
                        0.0,
                        COALESCE((SELECT so.zIndex FROM spatial_objects so WHERE so.id = f.linkedRegionId LIMIT 1), 0),
                        '{"flashcardId":"' || f.id || '","previewText":"' ||
                            replace(replace(substr(f.front, 1, 90), '"', '\"'), char(10), ' ') ||
                            '","noteType":"' ||
                            CASE WHEN lower(f.tags) LIKE '%type:cloze%' THEN 'cloze' ELSE 'basic' END ||
                            '"}',
                        NULLIF(COALESCE(
                            (SELECT b.notebookId FROM boards b WHERE b.id = f.boardId LIMIT 1),
                            (SELECT b2.notebookId
                               FROM boards b2
                               INNER JOIN layers l2 ON l2.boardId = b2.id
                               WHERE l2.id = (SELECT so.layerId FROM spatial_objects so WHERE so.id = f.linkedRegionId LIMIT 1)
                               LIMIT 1),
                            (SELECT notebookId FROM boards ORDER BY createdAt ASC LIMIT 1),
                            ''
                        ), ''),
                        NULLIF(COALESCE(
                            (SELECT so.layerId FROM spatial_objects so WHERE so.id = f.linkedRegionId LIMIT 1),
                            (SELECT l.id FROM layers l INNER JOIN boards b ON b.id = l.boardId WHERE b.id = f.boardId ORDER BY l.`order` ASC LIMIT 1),
                            (SELECT id FROM layers ORDER BY `order` ASC LIMIT 1),
                            ''
                        ), ''),
                        f.createdAt,
                        f.createdAt
                    FROM flashcards f
                    WHERE NULLIF(COALESCE(
                        (SELECT so.layerId FROM spatial_objects so WHERE so.id = f.linkedRegionId LIMIT 1),
                        (SELECT l.id FROM layers l INNER JOIN boards b ON b.id = l.boardId WHERE b.id = f.boardId ORDER BY l.`order` ASC LIMIT 1),
                        (SELECT id FROM layers ORDER BY `order` ASC LIMIT 1),
                        ''
                    ), '') IS NOT NULL
                    AND NULLIF(COALESCE(
                        (SELECT b.notebookId FROM boards b WHERE b.id = f.boardId LIMIT 1),
                        (SELECT b2.notebookId
                           FROM boards b2
                           INNER JOIN layers l2 ON l2.boardId = b2.id
                           WHERE l2.id = (SELECT so.layerId FROM spatial_objects so WHERE so.id = f.linkedRegionId LIMIT 1)
                           LIMIT 1),
                        (SELECT notebookId FROM boards ORDER BY createdAt ASC LIMIT 1),
                        ''
                    ), '') IS NOT NULL
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): CnntDatabase {
            return INSTANCE ?: synchronized(this) {
                var instance = buildDatabase(context, destructiveFallback = false)
                try {
                    instance.openHelper.writableDatabase
                } catch (exception: Exception) {
                    Log.e("CNNT", "Database open failed, attempting safe recovery", exception)
                    instance.close()
                    backupDatabaseFiles(context)
                    context.deleteDatabase("cnnt_database")
                    instance = buildDatabase(context, destructiveFallback = true)
                    instance.openHelper.writableDatabase
                }
                INSTANCE = instance
                instance
            }
        }

        private fun buildDatabase(context: Context, destructiveFallback: Boolean): CnntDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                CnntDatabase::class.java,
                "cnnt_database"
            ).apply {
                if (destructiveFallback) {
                    fallbackToDestructiveMigration()
                } else {
                    addMigrations(MIGRATION_1_2)
                }
            }.build()
        }

        private fun backupDatabaseFiles(context: Context) {
            val databaseDir = context.getDatabasePath("cnnt_database").parentFile ?: return
            val backupDir = File(context.filesDir, "db_recovery").apply { mkdirs() }
            val timestamp = System.currentTimeMillis()
            listOf("cnnt_database", "cnnt_database-wal", "cnnt_database-shm").forEach { name ->
                val source = File(databaseDir, name)
                if (!source.exists()) return@forEach
                val target = File(backupDir, "${timestamp}_$name.bak")
                runCatching { source.copyTo(target, overwrite = true) }
            }
        }
    }
}
