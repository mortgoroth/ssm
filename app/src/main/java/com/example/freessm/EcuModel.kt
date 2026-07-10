package com.example.freessm

data class EcuModel(
    val signature: String,        // Часть или полный ROM-ID для поиска
    val engineName: String,       // Красивое имя мотора на экране
    val dataBytes: Int,           // Размер блока параметров (Data)
    val switchBytes: Int,         // Размер блока кнопок (Switches)
    val isMatch: (String) -> Boolean // Функция-условие для проверки (startsWith или contains)
)
