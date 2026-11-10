package com.hark.sothudiary

import com.hark.sothudiary.ui.screens.maskKey
import kotlin.test.Test
import kotlin.test.assertEquals

/** 3.0.1: the AI key is shown masked. */
class V301Test {
    @Test fun keyShowsOnlyLastFour() {
        assertEquals("••••WXYZ", maskKey("AIzaSyABCDEFGHWXYZ"))
        assertEquals("••••", maskKey("short"))
        assertEquals("", maskKey("  "))
    }
}
