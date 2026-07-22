package dev.shiko.bootpatcher

import org.junit.Assert.assertEquals
import org.junit.Test

class OutputFileNameTest {
    @Test
    fun keepsImgExtension() {
        assertEquals("custom.img", normalizeOutputFileName("custom.img"))
    }

    @Test
    fun addsImgExtension() {
        assertEquals("custom.img", normalizeOutputFileName("custom"))
    }

    @Test
    fun keepsUserNameAndMakesImgTheFinalExtension() {
        assertEquals("custom.bin.img", normalizeOutputFileName("custom.bin"))
    }

    @Test
    fun restoresDefaultForBlankName() {
        assertEquals(DEFAULT_OUTPUT_FILE_NAME, normalizeOutputFileName("  "))
    }

    @Test
    fun removesPathSeparators() {
        assertEquals("folder_name.img", normalizeOutputFileName("folder/name"))
    }
}
