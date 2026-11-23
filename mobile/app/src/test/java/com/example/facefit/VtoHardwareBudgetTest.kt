package com.example.facefit

import android.os.PowerManager
import com.example.facefit.ui.camera.VtoHardwareBudget
import com.example.facefit.ui.camera.VtoThermalMonitor
import org.junit.Assert.*
import org.junit.Test

class VtoHardwareBudgetTest {
    @Test fun callbacksShedAndRestoreSecondaryWork() {
        lateinit var status: (Int) -> Unit
        var registrations = 0
        var unregistrations = 0
        val monitor = VtoThermalMonitor { callback ->
            registrations++
            status = callback
            AutoCloseable { unregistrations++ }
        }
        monitor.start()
        monitor.start()
        assertEquals(1, registrations)
        for (level in listOf(PowerManager.THERMAL_STATUS_NONE, PowerManager.THERMAL_STATUS_LIGHT)) {
            status(level)
            assertEquals(480, monitor.budget.analysisDimension(480))
            assertTrue(monitor.budget.opticalFlow)
            assertTrue(monitor.budget.secondaryPasses)
        }
        status(PowerManager.THERMAL_STATUS_MODERATE)
        val reduced = monitor.budget.analysisDimension(480)
        assertTrue(reduced in 360 until 480)
        assertTrue(monitor.budget.opticalFlow)
        assertFalse(monitor.budget.secondaryPasses)
        for (level in listOf(PowerManager.THERMAL_STATUS_SEVERE, PowerManager.THERMAL_STATUS_CRITICAL)) {
            status(level)
            assertTrue(monitor.budget.analysisDimension(480) in 360..reduced)
            assertFalse(monitor.budget.opticalFlow)
            assertFalse(monitor.budget.secondaryPasses)
        }
        val stale = status
        monitor.close()
        monitor.close()
        assertEquals(1, unregistrations)
        stale(PowerManager.THERMAL_STATUS_NONE)
        assertEquals(VtoHardwareBudget.CONSTRAINED, monitor.budget)
        monitor.start()
        status(PowerManager.THERMAL_STATUS_LIGHT)
        stale(PowerManager.THERMAL_STATUS_CRITICAL)
        assertEquals(VtoHardwareBudget.FULL, monitor.budget)
        monitor.close()
        assertEquals(2, unregistrations)
    }

    @Test fun unsupportedThermalServiceKeepsFullBudget() {
        val monitor = VtoThermalMonitor { null }
        monitor.start()
        assertEquals(VtoHardwareBudget.FULL, monitor.budget)
        monitor.close()
    }
    @Test fun detectorInputKeeps360PixelShortEdgeInEveryThermalState() {
        for (budget in VtoHardwareBudget.entries) {
            for ((w, h) in listOf(480 to 640, 640 to 480, 126 to 256, 720 to 1480)) {
                val (inputW, inputH) = VtoHardwareBudget.inputSize(w, h, budget.analysisDimension(256))
                assertTrue(minOf(inputW, inputH) >= 360)
                assertEquals(w.toDouble() / h, inputW.toDouble() / inputH, .003)
            }
        }
    }
    @Test fun fullNativePortraitFrameUsesCompactUniformInput() {
        assertEquals(360 to 480, VtoHardwareBudget.inputSize(720, 960, 480))
        assertEquals(480 to 360, VtoHardwareBudget.inputSize(960, 720, 480))
    }
}
