package com.example.localledger.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OemGuideTest {
    @Test
    fun knownOemBrandsGetAutostartSteps() {
        assertTrue(oemGuide("Xiaomi", "Redmi")!!.title.contains("HyperOS"))
        assertTrue(oemGuide("HUAWEI", "HUAWEI")!!.steps.contains("自启动"))
        assertTrue(oemGuide("HONOR", "HONOR")!!.title.contains("HarmonyOS"))
        assertTrue(oemGuide("OPPO", "OPPO")!!.title.contains("ColorOS"))
        assertTrue(oemGuide("vivo", "iQOO")!!.steps.contains("自启动"))
        assertNull(oemGuide("Google", "Pixel"))
        assertEquals(oemGuide("samsung", "samsung"), null)
    }
}
