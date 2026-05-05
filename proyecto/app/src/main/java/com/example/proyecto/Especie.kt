package com.example.proyecto

data class Especie(
    val id: String,
    val nombre_cientifico: String,
    val nombre_comun: String,
    val nombre_ingles: String,
    val etimologia: String,
    val conservacion: List<String>,
    val descripcion: List<String>,
    val tamano: String,
    val reproduccion: String,
    val alimentacion: String,
    val habitat: String,
    val imagen_1: String,
    val imagen_2: String,
    val mapa: String,
    val canto: String,
    val referencias: String
)