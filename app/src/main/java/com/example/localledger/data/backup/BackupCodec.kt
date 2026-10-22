package com.example.localledger.data.backup

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupFileHeader(
    val formatVersion: Int,
    val encrypted: Boolean,
    val schemaVersion: Int,
    val createdAtEpochMillis: Long,
)

object BackupCodec {
    const val FORMAT_VERSION = 1
    const val MAGIC = "NNBK"
    private const val HEADER_BYTES = 18
    private const val FLAG_ENCRYPTED = 1
    private const val MAX_UNCOMPRESSED = 32 * 1024 * 1024
    private const val ENTRY_NAME = "backup.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    fun encode(
        document: BackupDocument,
        password: CharArray?,
        iterations: Int = BackupCrypto.DEFAULT_ITERATIONS,
    ): ByteArray {
        val zipped = zip(json.encodeToString(document).toByteArray(Charsets.UTF_8))
        val encrypt = password != null && password.isNotEmpty()
        val payload = if (encrypt) {
            val sealed = BackupCrypto.seal(zipped, password, iterations)
            ByteArrayOutputStream().apply {
                write(sealed.salt)
                write(sealed.iv)
                write(intBytes(sealed.iterations))
                write(sealed.ciphertext)
            }.toByteArray()
        } else {
            zipped
        }
        val header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.BIG_ENDIAN)
        header.put(MAGIC.toByteArray(Charsets.US_ASCII))
        header.put(FORMAT_VERSION.toByte())
        header.put((if (encrypt) FLAG_ENCRYPTED else 0).toByte())
        header.putInt(document.schemaVersion)
        header.putLong(document.createdAtEpochMillis)
        return header.array() + payload
    }

    fun header(bytes: ByteArray): BackupFileHeader {
        if (bytes.size < HEADER_BYTES) throw BackupFailure("备份文件已损坏或无法读取", BackupFailure.Kind.Corrupt)
        val magic = bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII)
        if (magic != MAGIC) throw BackupFailure("无法识别的备份文件", BackupFailure.Kind.Corrupt)
        val format = bytes[4].toInt() and 0xFF
        if (format != FORMAT_VERSION) throw BackupFailure("无法识别的备份格式，请更新牛牛记账后再试")
        val flags = bytes[5].toInt() and 0xFF
        if ((flags and FLAG_ENCRYPTED.inv()) != 0) {
            throw BackupFailure("无法识别的备份格式，请更新牛牛记账后再试")
        }
        val view = ByteBuffer.wrap(bytes, 6, 12).order(ByteOrder.BIG_ENDIAN)
        return BackupFileHeader(
            formatVersion = format,
            encrypted = (flags and FLAG_ENCRYPTED) != 0,
            schemaVersion = view.int,
            createdAtEpochMillis = view.long,
        )
    }

    fun decode(bytes: ByteArray, password: CharArray?): BackupDocument {
        val fileHeader = header(bytes)
        val payload = bytes.copyOfRange(HEADER_BYTES, bytes.size)
        val zipped = if (fileHeader.encrypted) {
            if (password == null || password.isEmpty()) {
                throw BackupFailure("这份备份已加密，请输入密码", BackupFailure.Kind.NeedsPassword)
            }
            if (payload.size < 16 + 12 + 4 + 16) throw BackupFailure("备份文件已损坏或无法读取")
            val salt = payload.copyOfRange(0, 16)
            val iv = payload.copyOfRange(16, 28)
            val iterations = ByteBuffer.wrap(payload, 28, 4).order(ByteOrder.BIG_ENDIAN).int
            val ciphertext = payload.copyOfRange(32, payload.size)
            BackupCrypto.open(
                BackupCrypto.Sealed(salt, iv, iterations, ciphertext),
                password,
            )
        } else {
            payload
        }
        val document = try {
            json.decodeFromString<BackupDocument>(unzip(zipped).toString(Charsets.UTF_8))
        } catch (error: BackupFailure) {
            throw error
        } catch (_: Exception) {
            throw BackupFailure("备份内容无法识别")
        }
        if (document.formatVersion != FORMAT_VERSION) {
            throw BackupFailure("无法识别的备份格式，请更新牛牛记账后再试")
        }
        if (document.schemaVersion != fileHeader.schemaVersion ||
            document.createdAtEpochMillis != fileHeader.createdAtEpochMillis
        ) {
            throw BackupFailure("备份文件已损坏或无法读取")
        }
        return document
    }

    private fun zip(jsonBytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.setLevel(java.util.zip.Deflater.BEST_SPEED)
            zip.putNextEntry(ZipEntry(ENTRY_NAME))
            zip.write(jsonBytes)
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun unzip(bytes: ByteArray): ByteArray {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            val entry = zip.nextEntry ?: throw BackupFailure("备份文件已损坏或无法读取")
            if (entry.name != ENTRY_NAME || entry.name.contains("..")) {
                throw BackupFailure("备份文件已损坏或无法读取")
            }
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = zip.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_UNCOMPRESSED) throw BackupFailure("备份文件过大")
                output.write(buffer, 0, read)
            }
            if (zip.nextEntry != null) throw BackupFailure("备份文件已损坏或无法读取")
            return output.toByteArray()
        }
    }

    private fun intBytes(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array()
}
