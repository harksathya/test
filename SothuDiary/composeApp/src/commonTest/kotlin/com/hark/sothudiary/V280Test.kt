package com.hark.sothudiary

import com.hark.sothudiary.data.DocKind
import com.hark.sothudiary.data.FlagLevel
import com.hark.sothudiary.data.LandRecord
import com.hark.sothudiary.domain.ai.ApiStyle
import com.hark.sothudiary.domain.docs.DocFile
import com.hark.sothudiary.domain.docs.Nature
import com.hark.sothudiary.domain.docs.buildAnalysis
import com.hark.sothudiary.domain.docs.daysBetween
import com.hark.sothudiary.domain.docs.isoDate
import com.hark.sothudiary.domain.docs.multimodalBody
import com.hark.sothudiary.domain.docs.natureFromText
import com.hark.sothudiary.domain.docs.parseAiReading
import com.hark.sothudiary.domain.docs.samePeople
import com.hark.sothudiary.domain.docs.surveyMatches
import com.hark.sothudiary.domain.docs.tryNextModel
import com.hark.sothudiary.domain.docs.DocRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class V280Test {
    private val land = LandRecord(district = "Madurai", village = "Thirumangalam", surveyNo = "123", subdivision = "4B", pattaNo = "456")

    /** An EC read by the AI: clean chain 1995 -> 2008 -> 2016, a mortgage released, an open mortgage, another survey. */
    private val answer = """
        Here is the JSON:
        ```json
        {"ecFrom":"01-01-1990","ecTo":"15-09-2026",
         "entries":[
          {"docNo":"1201","year":1995,"date":"10-02-1995","nature":"Sale","natureCode":"SALE","executants":["Raman S/o Muthu"],"claimants":["Kannan S/o Raman"],"value":"Rs. 45,000","surveyNos":["123/4B"]},
          {"docNo":"880","year":2008,"date":"05-06-2008","nature":"கிரயம்","executants":["Kannan"],"claimants":["Selvi W/o Murugan"],"value":350000,"surveyNos":["S.F.No. 123/4B"]},
          {"docNo":"910","year":2010,"date":"01-03-2010","nature":"Mortgage","executants":["Selvi"],"claimants":["Indian Bank"],"value":200000,"surveyNos":["123/4B"]},
          {"docNo":"1500","year":2012,"date":"02-04-2012","nature":"Receipt","natureCode":"MORTGAGE_RELEASE","executants":["Indian Bank"],"claimants":["Selvi"],"refDocNo":"910/2010","surveyNos":["123/4B"]},
          {"docNo":"2222","year":2016,"date":"11-11-2016","nature":"Sale","executants":["Selvi"],"claimants":["Arun Kumar"],"value":1200000,"surveyNos":["123/4B"]},
          {"docNo":"3001","year":2019,"date":"20-01-2019","nature":"Mortgage deed","executants":["Arun Kumar"],"claimants":["Sri Finance"],"value":500000,"surveyNos":["123/4B"]},
          {"docNo":"777","year":2001,"date":"01-01-2001","nature":"Sale","executants":["Other Person"],"claimants":["Someone Else"],"value":10000,"surveyNos":["98/2"]}
         ],
         "documents":[{"kind":"PATTA","holders":["Arun Kumar"],"pattaNo":"456","surveyNos":["123/4B"],"extent":"0.02.0 Hect","landClass":"Punjai"}],
         "flags":[{"code":"stamp","level":"low","en":"Stamp paper value not visible.","ta":"முத்திரைத்தாள் மதிப்பு தெரியவில்லை.","ref":"2222/2016"}],
         "summaryEn":"Arun Kumar owns the land since 2016.","summaryTa":"2016 முதல் அருண் குமார் உரிமையாளர்."}
        ```
    """.trimIndent()

    @Test fun readsTheAiAnswer() {
        val r = assertNotNull(parseAiReading(answer))
        assertEquals(7, r.entries.size)
        assertEquals(45_000L, r.entries[0].value)
        assertEquals(Nature.SALE, r.entries[1].natureCode, "Tamil nature without a code -> SALE")
        assertEquals(Nature.MORTGAGE, r.entries[2].natureCode)
        assertEquals(DocKind.PATTA, r.docs.single().kind)
        val f = r.aiFlags.single()
        assertTrue(f.byAi && f.level == FlagLevel.LOW && f.code == "AI_STAMP")
        assertNull(parseAiReading("sorry, I cannot read this"))
    }

    @Test fun natureWords() {
        assertEquals(Nature.MORTGAGE_RELEASE, natureFromText("Receipt for discharge of mortgage"))
        assertEquals(Nature.MORTGAGE, natureFromText("அடமானப் பத்திரம்"))
        assertEquals(Nature.SETTLEMENT, natureFromText("Settlement deed"))
        assertEquals(Nature.POWER, natureFromText("General Power of Attorney"))
        assertEquals(Nature.POWER_CANCEL, natureFromText("Cancellation of power"))
        assertEquals(Nature.CANCELLATION, natureFromText("Cancellation of sale deed"))
        assertEquals(Nature.SALE, natureFromText("கிரய ஆவணம்"))
    }

    @Test fun namesAndSurveys() {
        assertTrue(samePeople(listOf("Kannan"), listOf("Kannan S/o Raman")))
        assertTrue(samePeople(listOf("S. Selvi"), listOf("Selvi W/o Murugan")))
        assertFalse(samePeople(listOf("Raman"), listOf("Kumar")))
        assertTrue(surveyMatches("123/4B", "S.F.No. 123/4B"))
        assertTrue(surveyMatches("123", "123/4B"))
        assertTrue(surveyMatches("123/4", "123/4B"))
        assertFalse(surveyMatches("123", "1234"))
        assertFalse(surveyMatches("123/4", "123/41"))
        assertEquals("2015-03-12", isoDate("12-03-2015"))
        assertEquals(30, daysBetween("2026-09-15", "2026-10-15"))
    }

    @Test fun chainAndFlags() {
        val a = buildAnalysis(parseAiReading(answer)!!, land, hasEc = true, now = "2026-11-10", analysedAt = 1, provider = "Google Gemini", model = "m", docIds = listOf("d"))
        // Chain: only transfers of survey 123/4B, oldest first; the 98/2 sale is left out.
        assertEquals(listOf("1201", "880", "2222"), a.chain.map { it.docNo })
        assertEquals(listOf("Arun Kumar"), a.chain.last().to)
        val codes = a.flags.map { it.code }
        assertFalse("CHAIN_BREAK" in codes, codes.toString())
        assertEquals(1, codes.count { it == "OPEN_MORTGAGE" }, "910/2010 was released, 3001/2019 was not")
        assertTrue(a.flags.first { it.code == "OPEN_MORTGAGE" }.ref == "3001/2019")
        assertFalse("PATTA_NOT_SELLER" in codes)
        assertTrue("EC_OLD" in codes, "EC ends 15-09-2026, more than 30 days before 2026-11-10")
        assertFalse("EC_SHORT" in codes)
        assertTrue("AI_STAMP" in codes)
        assertEquals(FlagLevel.HIGH, a.flags.first().level, "worst flags first")
        // Every flag has both languages.
        assertTrue(a.flags.all { it.en.isNotBlank() && it.ta.isNotBlank() })
    }

    @Test fun breaksAndWrongPatta() {
        val text = """{"ecFrom":"2015","ecTo":"2026","entries":[
            {"docNo":"1","year":2015,"nature":"Sale","executants":["Raman"],"claimants":["Kannan"],"surveyNos":["123/4B"]},
            {"docNo":"2","year":2020,"nature":"Sale","executants":["Velu"],"claimants":["Arun"],"surveyNos":["123/4B"],"viaPowerAgent":true},
            {"docNo":"3","year":2021,"nature":"Sale agreement","executants":["Arun"],"claimants":["Babu"],"surveyNos":["123/4B"]}],
           "documents":[{"kind":"CHITTA","holders":["Kannan"],"pattaNo":"999","surveyNos":["77/1"],"landClass":"Natham poramboke"}]}"""
        val a = buildAnalysis(parseAiReading(text)!!, land, hasEc = true, now = "2026-01-10", analysedAt = 1, provider = "p", model = "m", docIds = emptyList())
        val codes = a.flags.map { it.code }.toSet()
        assertTrue("CHAIN_BREAK" in codes, "Velu never bought it")
        assertTrue("POWER_SALE" in codes)
        assertTrue("OPEN_AGREEMENT" in codes)
        assertTrue("PATTA_NOT_SELLER" in codes, "chitta says Kannan, EC says Arun")
        assertTrue("PATTA_NO_MISMATCH" in codes)
        assertTrue("SURVEY_MISMATCH" in codes)
        assertTrue("GOVT_LAND" in codes)
        assertTrue("EC_SHORT" in codes, "2015–2026 is 11 years")
    }

    @Test fun noEcIsFlagged() {
        val text = """{"entries":[],"documents":[{"kind":"PATTA","holders":["Arun"]}]}"""
        val a = buildAnalysis(parseAiReading(text)!!, land, hasEc = false, now = "2026-01-10", analysedAt = 1, provider = "p", model = "m", docIds = emptyList())
        assertEquals("NO_EC", a.flags.first().code)
    }

    @Test fun requestBodiesCarryTheFiles() {
        val files = listOf(DocFile("image/jpeg", byteArrayOf(1, 2, 3)), DocFile("application/pdf", byteArrayOf(4, 5), "ec.pdf"))
        val g = multimodalBody(ApiStyle.GEMINI, "gemini-2.5-flash", "read", files)
        assertTrue("\"inline_data\"" in g && "\"application/pdf\"" in g && "AQID" in g && "responseMimeType" in g)
        val o = multimodalBody(ApiStyle.OPENAI, "gpt", "read", files)
        assertTrue("data:image/jpeg;base64,AQID" in o && "\"file_data\"" in o && "ec.pdf" in o)
        val c = multimodalBody(ApiStyle.ANTHROPIC, "claude", "read", files)
        assertTrue("\"document\"" in c && "\"image\"" in c && "\"base64\"" in c)
    }

    /** 2.8.1: a timeout / busy / quota answer moves on to the next model; a refused key does not. */
    @Test fun nextModelRules() {
        assertTrue(tryNextModel(-1) && tryNextModel(429) && tryNextModel(503) && tryNextModel(404) && tryNextModel(400))
        assertFalse(tryNextModel(401) || tryNextModel(403))
        assertTrue(DocRules.AI_TIMEOUT_MS >= 240_000 && DocRules.AI_BUDGET_MS >= DocRules.AI_TIMEOUT_MS)
    }
}
