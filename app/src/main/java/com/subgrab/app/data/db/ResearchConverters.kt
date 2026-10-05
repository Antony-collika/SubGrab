package com.subgrab.app.data.db

import androidx.room.TypeConverter

class ResearchConverters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String = value.orEmpty().joinToString("\u001f")

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        value.orEmpty().split("\u001f").filter(String::isNotBlank)
}
