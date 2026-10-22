package com.example.localledger.widget

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Xml
import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import com.example.localledger.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WidgetProviderManifestTest {
    @Test
    fun receiverStaysEnabledAndNamedForThePicker() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val component = ComponentName(context, LedgerSummaryWidgetReceiver::class.java)
        val info = context.packageManager.getReceiverInfo(
            component,
            PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS,
        )
        assertTrue(info.exported)
        assertTrue(info.enabled)
        assertEquals(context.getString(R.string.widget_name), info.loadLabel(context.packageManager).toString())
        assertEquals(R.string.widget_description, info.descriptionRes)
        assertEquals(R.xml.ledger_widget_info, info.metaData.getInt("android.appwidget.provider"))
    }

    @Test
    fun providerXmlDeclaresBitmapPreviewAndFourByTwoCells() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val parser = context.resources.getXml(R.xml.ledger_widget_info)
        try {
            while (parser.next() != XmlPullParser.START_TAG) Unit
            assertEquals("appwidget-provider", parser.name)
            val typed = context.resources.obtainAttributes(
                Xml.asAttributeSet(parser),
                intArrayOf(
                    android.R.attr.minWidth,
                    android.R.attr.minHeight,
                    android.R.attr.minResizeWidth,
                    android.R.attr.minResizeHeight,
                    android.R.attr.previewImage,
                    android.R.attr.previewLayout,
                    android.R.attr.description,
                ),
            )
            try {
                val density = context.resources.displayMetrics.density
                assertEquals(250f, typed.getDimension(0, -1f) / density, 0.5f)
                assertEquals(110f, typed.getDimension(1, -1f) / density, 0.5f)
                assertEquals(250f, typed.getDimension(2, -1f) / density, 0.5f)
                assertEquals(110f, typed.getDimension(3, -1f) / density, 0.5f)
                val ns = "http://schemas.android.com/apk/res/android"
                val rawPreview = parser.getAttributeValue(ns, "previewImage")
                val previewId = parser.getAttributeResourceValue(ns, "previewImage", 0)
                assertEquals("previewImage raw=$rawPreview id=$previewId", R.drawable.widget_ledger_preview, previewId)
                assertEquals(R.layout.ledger_widget_preview, parser.getAttributeResourceValue(ns, "previewLayout", 0))
                assertEquals(R.string.widget_description, parser.getAttributeResourceValue(ns, "description", 0))
                val category = parser.getAttributeValue(ns, "widgetCategory")
                assertTrue("widgetCategory=$category", category == "home_screen" || category == "0x1" || category == "1")
                assertEquals(4, parser.getAttributeIntValue(ns, "targetCellWidth", 0))
                assertEquals(2, parser.getAttributeIntValue(ns, "targetCellHeight", 0))
            } finally {
                typed.recycle()
            }
        } finally {
            parser.close()
        }
    }

    @Test
    fun previewImageHasTransparentCornersAndAnOpaqueCenter() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.widget_ledger_preview)
        assertNotNull(bitmap)
        assertTrue(bitmap.width >= 400)
        assertTrue(bitmap.height >= 180)
        assertEquals(255, Color.alpha(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)))
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        assertEquals(0, Color.alpha(bitmap.getPixel(bitmap.width - 1, 0)))
        assertEquals(0, Color.alpha(bitmap.getPixel(0, bitmap.height - 1)))
        assertEquals(0, Color.alpha(bitmap.getPixel(bitmap.width - 1, bitmap.height - 1)))
        // Nodpi bitmap cannot read the system radius. It uses the 20dp fallback
        // against the 250dp minimum width, with the champagne hairline still inset.
        val radius = 20f * bitmap.width / 250f
        assertTrue(radius > 16f)
        val midY = bitmap.height / 2
        assertEquals(255, Color.alpha(bitmap.getPixel(2, midY)))
        assertEquals(0xFFD9D0C3.toInt(), bitmap.getPixel(1, midY))
        val inside = radius.toInt().coerceAtMost(bitmap.width / 2)
        assertEquals(255, Color.alpha(bitmap.getPixel(inside, midY)))
        assertEquals(0, Color.alpha(bitmap.getPixel(2, 2)))
    }

    @Test
    fun layoutsClipContentToTheRoundedSurface() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertRoundedClippedBackground(context, R.layout.ledger_widget_loading)
        assertRoundedClippedBackground(context, R.layout.ledger_widget_preview)
    }

    @Test
    @Config(sdk = [26], application = android.app.Application::class)
    fun backgroundRadiusFallsBackTo20DpBeforeAndroid12() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val density = context.resources.displayMetrics.density
        val radius = context.resources.getDimension(R.dimen.widget_background_radius)
        assertEquals(20f, radius / density, 0.5f)
        val drawable = context.getDrawable(R.drawable.widget_background) as GradientDrawable
        assertEquals(radius, drawable.cornerRadius, 0.5f)
    }

    private fun assertRoundedClippedBackground(context: android.content.Context, layout: Int) {
        val root = LayoutInflater.from(context).inflate(layout, null)
        assertEquals(android.R.id.background, root.id)
        assertTrue(root.clipToOutline)
        val background = root.background as GradientDrawable
        val radius = context.resources.getDimension(R.dimen.widget_background_radius)
        val system = context.resources.getDimension(android.R.dimen.system_app_widget_background_radius)
        assertEquals(system, radius, 0.5f)
        assertEquals(radius, background.cornerRadius, 0.5f)
        assertEquals(context.getColor(R.color.widget_surface), background.color?.defaultColor)
    }
}
