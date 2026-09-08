package cn.campus.core

import kotlin.test.*

class PhoneGuideTest {
    @Test fun manufacturerAliasesAndUnknown() {
        mapOf("HONOR" to PhoneGuide.HONOR,"HUAWEI" to PhoneGuide.HUAWEI,"Redmi" to PhoneGuide.XIAOMI,"POCO" to PhoneGuide.XIAOMI,
            "OnePlus" to PhoneGuide.OPPO,"realme" to PhoneGuide.OPPO,"iQOO" to PhoneGuide.VIVO,"Samsung" to PhoneGuide.SAMSUNG,"Google" to PhoneGuide.OTHER)
            .forEach {(brand,expected)->assertEquals(expected,PhoneGuide.detect("unknown",brand))}
        assertEquals(PhoneGuide.HONOR,PhoneGuide.detect("HUAWEI","HONOR"))
    }
    @Test fun allGuidesHaveManualInstructions() {
        assertEquals(7,PhoneGuide.entries.size)
        assertTrue(PhoneGuide.entries.all {it.instruction.isNotBlank() && it.label.isNotBlank()})
    }
}
