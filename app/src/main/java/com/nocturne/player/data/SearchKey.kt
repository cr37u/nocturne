package com.nocturne.player.data

import java.text.Normalizer
import java.util.Locale

/**
 * Accent- and case-insensitive search key: "Lumini de oraș" → "lumini de oras".
 * Latin diacritics are stripped (ș/ş → s, ț/ţ → t, ă/â → a, î → i, é → e …); Cyrillic and other
 * scripts are kept and lower-cased with a fixed locale, so "Горы" matches "горы" and ё ↔ е.
 */
object SearchKey {
    private val marks = Regex("\\p{Mn}+")
    private val spaces = Regex("\\s+")

    fun of(vararg parts: String?): String = normalize(parts.filterNotNull().joinToString(" "))

    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return marks.replace(decomposed, "")
            .replace('ё', 'е').replace('Ё', 'Е')
            .replace('ı', 'i')
            .lowercase(Locale.ROOT)
            .replace(spaces, " ")
            .trim()
    }
}
