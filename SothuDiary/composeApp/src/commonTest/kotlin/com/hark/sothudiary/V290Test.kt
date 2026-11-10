package com.hark.sothudiary

import com.hark.sothudiary.data.DocChatMsg
import com.hark.sothudiary.data.DocKind
import com.hark.sothudiary.data.DocText
import com.hark.sothudiary.data.LandRecord
import com.hark.sothudiary.data.PropertyDocument
import com.hark.sothudiary.domain.docs.EcChatRules
import com.hark.sothudiary.domain.docs.chatPrompt
import com.hark.sothudiary.domain.docs.documentsBlock
import com.hark.sothudiary.domain.docs.quickQuestions
import com.hark.sothudiary.domain.docs.transcribePrompt
import kotlin.test.Test
import kotlin.test.assertTrue

/** 2.9.0: EC chat prompts. */
class V290Test {
    private val ec = PropertyDocument("d1", DocKind.EC, "doc_1.pdf", "application/pdf", 0, "EC.pdf")
    private val deed = PropertyDocument("d2", DocKind.SALE_DEED, "doc_2.jpg", "image/jpeg", 0, "deed.jpg")

    @Test fun ecComesFirstAndLongTextIsCut() {
        val b = documentsBlock(listOf(deed to DocText("d2", 1, "DEED TEXT"), ec to DocText("d1", 3, "x".repeat(500))), max = 300)
        assertTrue(b.indexOf("EC.pdf") < b.indexOf("deed.jpg") || "deed.jpg" !in b)
        assertTrue("cut, too long" in b)
    }

    @Test fun chatPromptCarriesQuestionHistoryAndLand() {
        val land = LandRecord(surveyNo = "136/1A", pattaNo = "1234", village = "Kanakkapillai Valasai")
        val hist = (1..15).map { DocChatMsg(it % 2 == 1, "msg$it") }
        val pr = chatPrompt(land, "Plot", listOf(ec to DocText("d1", 2, "=== Page 1 ===\nSl.No 1 ...")), hist, "  who owned it in 2005? ")
        assertTrue("136/1A" in pr && "1234" in pr && "Sl.No 1" in pr)
        assertTrue(pr.trimEnd().endsWith("who owned it in 2005?"))
        assertTrue("msg15" in pr && "msg5" !in pr.replace("msg15", ""), "only the last ${EcChatRules.HISTORY} messages")
    }

    @Test fun quickQuestionsUseSurveyAndPatta() {
        val q = quickQuestions(LandRecord(surveyNo = "136/1A", pattaNo = "77"), tamil = false)
        assertTrue(q.first().contains("136/1A") && q.any { "77" in it } && q.any { it.startsWith("Full analysis") })
        assertTrue(quickQuestions(LandRecord(), tamil = true).none { "சர்வே எண் " in it && "முழு உரிமை" in it })
    }

    @Test fun transcribePromptNamesPages() {
        val t = transcribePrompt(DocKind.EC, 3, 4, 12)
        assertTrue("pages 3 to 4 of 12" in t && "=== Page N ===" in t && "Tamil" in t)
    }
}
