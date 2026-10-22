package com.example.localledger.notification

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.localledger.data.repository.CapturedNotification
import com.example.localledger.data.repository.NotificationRecordResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NotificationRedactorTest {
    @Test
    fun masksAmountsAndMerchantsAndStaysStable() {
        val samples = listOf(
            "向美团付款¥18.00",
            "你已付款6.60元\n收款方：星巴克",
            "微信支付凭证￥１，２５０．５０",
            "在星巴克消费12.50元",
        )
        samples.forEach { sample ->
            val masked = NotificationRedactor.redact(sample)
            assertFalse(masked.contains("美团"))
            assertFalse(masked.contains("星巴克"))
            assertFalse(masked.contains("18.00"))
            assertFalse(masked.contains("6.60"))
            assertFalse(masked.contains("12.50"))
            assertFalse(masked.contains("１，２５０"))
            assertEquals(masked, NotificationRedactor.redact(masked))
        }
        assertTrue(NotificationRedactor.redact("向美团付款¥18.00").contains("向**付款"))
        assertTrue(NotificationRedactor.redact("收款方：星巴克").contains("收款方："))
    }

    @Test
    fun diagnosticsStorePersistsMaskedExcerptAndClearsIt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = NotificationDiagnosticsStore(
            context,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
        store.record(
            CapturedNotification(
                packageName = "com.tencent.mm",
                text = "向美团付款¥18.00",
                postedAt = 1L,
                key = "k",
            ),
            NotificationRecordResult.Ignored("不是支付通知"),
        )
        val excerpt = store.recent.value.single().excerpt
        assertFalse(excerpt.contains("美团"))
        assertFalse(excerpt.contains("18.00"))
        assertTrue(excerpt.contains("**"))
        store.clear()
        assertTrue(store.recent.value.isEmpty())
    }
}
