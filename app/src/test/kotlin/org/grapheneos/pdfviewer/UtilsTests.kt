package org.grapheneos.pdfviewer

import org.junit.Assert
import org.junit.Test

class UtilsTests {
    @Test
    fun `Should parse File Size (kB)`() {
        val fileSize: Long = 1_000
        Assert.assertEquals("1 kB (${fileSize} Bytes)", Utils.parseFileSize(fileSize))
    }

    @Test
    fun `Should parse File Size (decimal kB)`() {
        val fileSize: Long = 1_024
        Assert.assertEquals("1,03 kB (${fileSize} Bytes)", Utils.parseFileSize(fileSize))
    }

    @Test
    fun `Should parse File Size (MB)`() {
        val fileSize: Long = 1_000_000
        Assert.assertEquals("1 MB (${fileSize} Bytes)", Utils.parseFileSize(fileSize))
    }

    @Test
    fun `Should parse File Size (decimal MB)`() {
        val fileSize: Long = 1_240_000
        Assert.assertEquals("1,24 MB (${fileSize} Bytes)", Utils.parseFileSize(fileSize))
    }
}