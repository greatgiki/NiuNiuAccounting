package com.example.localledger.data.work

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoBackupRetentionTest {
    @Test
    fun keepsNewestMatchingNames() {
        val files = listOf(
            "牛牛记账自动备份-20261021-090000.nnbk" to 30L,
            "notes.txt" to 100L,
            "牛牛记账自动备份-20261020-090000.nnbk" to 20L,
            "牛牛记账自动备份-20261019-090000.nnbk" to 10L,
        )
        assertEquals(
            listOf("牛牛记账自动备份-20261019-090000.nnbk"),
            AutoBackupRetention.staleNames(files, keep = 2),
        )
    }
}
