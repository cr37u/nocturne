package com.nocturne.player

import com.nocturne.player.data.SearchKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchKeyTest {
    @Test fun stripsRomanianDiacritics() {
        assertEquals("lumini de oras", SearchKey.normalize("Lumini de Oraș"))
        assertEquals("tara tarziu", SearchKey.normalize("Țară  târziu"))
        assertTrue(SearchKey.of("Ceață peste lac", "Luca M.").contains(SearchKey.normalize("ceata")))
    }

    @Test fun cyrillicIsCaseInsensitive() {
        assertTrue(SearchKey.of("Горы по колено", "Макс Корж").contains(SearchKey.normalize("горы")))
        assertTrue(SearchKey.of("Ёлка", "").contains(SearchKey.normalize("елка")))
    }

    @Test fun cedillaVariantsMatch() {
        assertEquals(SearchKey.normalize("Şi"), SearchKey.normalize("Și"))
    }
}
