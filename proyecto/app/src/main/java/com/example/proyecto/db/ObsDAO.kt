package com.example.proyecto.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ObsDAO {
    @Query("SELECT * FROM Observacion")
    suspend fun getAll(): List<Observacion>
    @Query("SELECT * FROM Observacion WHERE id IN (:Ids)")
    suspend fun get_id(Ids: String): List<Observacion>
    @Insert
    suspend fun insert(obs : Observacion)
    @Delete
    suspend fun delete(obs : Observacion)
}