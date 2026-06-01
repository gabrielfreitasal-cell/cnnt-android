package com.cnnt.app.data.dao

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        NotebookEntity::class,
        BoardEntity::class,
        LayerEntity::class,
        StrokeEntity::class,
        SpatialObjectEntity::class,
        FlashcardEntity::class,
        PaletteEntity::class,
        BrushPresetEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CnntDatabase : RoomDatabase() {
    abstract fun notebookDao(): NotebookDao
    abstract fun boardDao(): BoardDao
    abstract fun strokeDao(): StrokeDao
    abstract fun spatialObjectDao(): SpatialObjectDao
    abstract fun flashcardDao(): FlashcardDao

    companion object {
        @Volatile
        private var INSTANCE: CnntDatabase? = null

        fun getDatabase(context: Context): CnntDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CnntDatabase::class.java,
                    "cnnt_database"
                ).fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
