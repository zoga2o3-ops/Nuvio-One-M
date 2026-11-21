package com.nuvio.app.core.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals

class SupportCodeTest {
    @Test
    fun `support code matches the account mapping`() {
        assertEquals("AC1C694222", SupportCode.forUserId("91c45662-19ff-44ca-8355-71389e1f3660"))
    }

    @Test
    fun `support code ignores case and surrounding spaces`() {
        assertEquals(
            SupportCode.forUserId("91c45662-19ff-44ca-8355-71389e1f3660"),
            SupportCode.forUserId(" 91C45662-19FF-44CA-8355-71389E1F3660 "),
        )
    }
}
