package com.example.localledger.data.backup

import com.example.localledger.data.local.database.WALLET_SCHEMA_VERSION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    @Test
    fun plainZipRoundTripKeepsTablesAndSchemaVersion() {
        val document = sampleDocument()
        val bytes = BackupCodec.encode(document, password = null, iterations = 1_000)
        val header = BackupCodec.header(bytes)
        assertFalse(header.encrypted)
        assertEquals(WALLET_SCHEMA_VERSION, header.schemaVersion)
        val decoded = BackupCodec.decode(bytes, password = null)
        assertEquals(document, decoded)
        assertEquals("午饭", decoded.tables.transactions.single().note)
    }

    @Test
    fun passwordRoundTripAndWrongPasswordFails() {
        val document = sampleDocument()
        val bytes = BackupCodec.encode(document, "secret-pass".toCharArray(), iterations = 1_000)
        assertTrue(BackupCodec.header(bytes).encrypted)
        assertEquals(document, BackupCodec.decode(bytes, "secret-pass".toCharArray()))
        val missing = assertThrows(BackupFailure::class.java) {
            BackupCodec.decode(bytes, password = null)
        }
        assertEquals(BackupFailure.Kind.NeedsPassword, missing.kind)
        val wrong = assertThrows(BackupFailure::class.java) {
            BackupCodec.decode(bytes, "nope".toCharArray())
        }
        assertEquals(BackupFailure.Kind.WrongPassword, wrong.kind)
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val bytes = BackupCodec.encode(sampleDocument(), "secret-pass".toCharArray(), iterations = 1_000)
        bytes[bytes.lastIndex] = (bytes[bytes.lastIndex].toInt() xor 0x5A).toByte()
        assertThrows(BackupFailure::class.java) {
            BackupCodec.decode(bytes, "secret-pass".toCharArray())
        }
    }

    @Test
    fun validatorRejectsOlderSchemaAndBrokenAccountReference() {
        val older = assertThrows(BackupFailure::class.java) {
            BackupValidator.validate(sampleDocument().copy(schemaVersion = WALLET_SCHEMA_VERSION - 1))
        }
        assertTrue(older.message!!.contains("无法直接恢复"))
        val newer = assertThrows(BackupFailure::class.java) {
            BackupValidator.validate(sampleDocument().copy(schemaVersion = WALLET_SCHEMA_VERSION + 1))
        }
        assertTrue(newer.message!!.contains("请先更新"))
        val broken = sampleDocument().let { document ->
            document.copy(
                tables = document.tables.copy(
                    transactions = document.tables.transactions.map { it.copy(accountId = "missing") },
                ),
            )
        }
        val invalid = assertThrows(BackupFailure::class.java) { BackupValidator.validate(broken) }
        assertTrue(invalid.message!!.contains("账户"))
    }

    private fun sampleDocument(): BackupDocument {
        val ledger = LedgerRow("ledger", "日常", "CNY", 10L, false)
        val account = AccountRow("cash", "ledger", "现金", "CASH", 0, 0, false, 10L, 10L, "CNY")
        val category = CategoryRow("food", "ledger", "餐饮", "EXPENSE", "food", 0, false, 10L, 10L)
        val transaction = TransactionRow(
            id = "tx",
            ledgerId = "ledger",
            type = "EXPENSE",
            amountMinor = 1_250,
            accountId = "cash",
            categoryId = "food",
            transactionAt = 20L,
            note = "午饭",
            createdAt = 20L,
            updatedAt = 20L,
            baseAmountMinor = 1_250,
            fxRate = "1",
        )
        return BackupDocument(
            schemaVersion = WALLET_SCHEMA_VERSION,
            createdAtEpochMillis = 20L,
            tables = BackupTables(
                ledgers = listOf(ledger),
                accounts = listOf(account),
                categories = listOf(category),
                transactions = listOf(transaction),
            ),
        )
    }
}
