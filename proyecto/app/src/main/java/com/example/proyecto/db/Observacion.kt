package com.example.proyecto.db


import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
class Observacion(
    @PrimaryKey val id: String,
    val nombre_comun : String,
    @ColumnInfo("nombre_cientifico") val nom_cientifico : String,
    val fecha : String,
    val hora: String,
    val latitud : String?,
    val longitud : String?,
    val confianza : String,
    @ColumnInfo("imagen") val uri : String
)