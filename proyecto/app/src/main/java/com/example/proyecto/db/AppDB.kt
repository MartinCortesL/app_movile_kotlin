package com.example.proyecto.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Observacion::class], version = 1)
abstract class AppDB : RoomDatabase() {
    abstract fun obsDAO(): ObsDAO
}