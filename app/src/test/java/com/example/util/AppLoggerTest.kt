package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLoggerTest {
    @Test
    fun `keeps every logged line for full clipboard export`() {
        AppLogger.clear()

        repeat(1001) { index ->
            AppLogger.log("Test", "message $index")
        }

        assertEquals(1001, AppLogger.logs.value.size)
    }
}
