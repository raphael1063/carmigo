package com.lrlr.carmigo.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MomentTextTest {
    @Test fun legacyCardsUseNewCopyWithoutChangingSavedData() {
        val json = Json { encodeDefaults = true }
        val old = Moment("heat-2026", MomentKind.SEASON, "첫 폭염의 주행", "2026년 여름", "예전 설명", 0)
        val before = json.encodeToString(old)
        val restored = json.decodeFromString<Moment>(before)
        assertEquals("33℃ 이상에서 달린 첫날", restored.displayTitle)
        assertTrue(restored.displayWhy.startsWith("기록을 시작한 뒤"))
        assertFalse(restored.displayWhy.contains("기상청"))
        assertEquals(before, json.encodeToString(restored))
        assertFalse(before.contains("displayTitle"))
        assertEquals(restored.displayTitle, restored.fullTitle)
    }

    @Test fun companionTitlesRemainSpecificToTheRecordedDayCount() {
        val old = Moment("companion-150", MomentKind.PEOPLE, "옆자리 150번째 날", "", "", 0)
        assertEquals("옆자리에 누군가 탄 150번째 날", old.displayTitle)
        assertEquals(old.displayTitle, old.fullTitle)
        val unrelated = old.copy(id = "custom", title = "내가 남긴 제목", why = "내 설명")
        assertEquals(unrelated.title, unrelated.displayTitle)
        assertEquals(unrelated.why, unrelated.displayWhy)
    }
}
