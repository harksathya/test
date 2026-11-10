package com.hark.sothudiary.domain.docs

import com.hark.sothudiary.data.DocChatMsg
import com.hark.sothudiary.data.DocKind
import com.hark.sothudiary.data.DocText
import com.hark.sothudiary.data.LandRecord
import com.hark.sothudiary.data.PropertyDocument
import com.hark.sothudiary.data.nowMillis
import com.hark.sothudiary.domain.ai.ChatResult
import com.hark.sothudiary.domain.ai.LlmConfig
import com.hark.sothudiary.domain.ai.chat

/**
 * 2.9.0 EC chat. A long EC is read a few pages per AI call (no single slow call, works with providers that take
 * images but not PDFs), the text is saved on the phone, and every question afterwards is a fast text-only call.
 */
object EcChatRules {
    /** PDF pages sent in one reading call (a Tamil EC page can be ~3 000 output tokens). */
    const val PAGES_PER_CALL = 2
    /** One reading call may take this long. */
    const val READ_TIMEOUT_MS = 180_000
    /** One question may take this long. */
    const val ANSWER_TIMEOUT_MS = 150_000
    /** Document text put into one question (longer texts are cut, the EC first). */
    const val MAX_CONTEXT_CHARS = 150_000
    /** Earlier chat messages sent with a question, so follow-ups ("what about 2005?") work. */
    const val HISTORY = 10
}

/** Prompt to copy the text of pages [from]..[to] (1-based) of a document. */
fun transcribePrompt(kind: DocKind, from: Int, to: Int, total: Int): String = buildString {
    append("These are ")
    append(if (total > 0) "pages $from to $to of $total" else "the pages")
    appendLine(" of a Tamil Nadu land document (${kind.label().first}).")
    appendLine("Copy ALL the printed and handwritten text exactly, in its own language (Tamil stays in Tamil script). Do not summarise, translate, guess or skip anything; write [unreadable] where you cannot read it.")
    appendLine("For an Encumbrance Certificate (EC) write each entry on its own block: Sl.No | Date of execution and registration | Document No./Year | Nature of document | Executant(s) | Claimant(s) | Survey No. | Extent | Consideration / market value | Remarks.")
    appendLine("Start each page with a line \"=== Page N ===\" (N = the page number in the whole document). Plain text only, no JSON, no comments.")
}

/** Reads one document page by page. [onProgress] gets (pages done, total pages; 0 when unknown). */
suspend fun transcribeDoc(
    llm: LlmConfig,
    d: PropertyDocument,
    readWhole: (String) -> ByteArray?,
    pages: (String, Int, Int) -> Pair<Int, List<ByteArray>>?,
    onProgress: (Int, Int) -> Unit = { _, _ -> },
): Result<DocText> {
    val isPdf = d.mime == "application/pdf"
    val first = if (isPdf) pages(d.fileName, 0, EcChatRules.PAGES_PER_CALL) else null
    if (first == null) {
        val bytes = readWhole(d.fileName) ?: return Result.failure(Exception("file missing"))
        onProgress(0, 0)
        return when (val r = readDocumentsWithAi(llm, transcribePrompt(d.kind, 1, 1, 0), listOf(DocFile(d.mime, bytes, d.displayName)),
            EcChatRules.READ_TIMEOUT_MS, jsonOut = false)) {
            is DocAiResult.Ok -> Result.success(DocText(d.id, 1, r.text.trim(), r.model, nowMillis()))
            is DocAiResult.Failed -> Result.failure(Exception(r.message + (if (r.model.isNotBlank()) " · ${r.model}" else "")))
        }
    }
    val total = first.first
    val out = StringBuilder()
    var model = ""
    var from = 0
    var batch: List<ByteArray> = first.second
    while (from < total) {
        if (batch.isEmpty()) return Result.failure(Exception("page ${from + 1} could not be opened"))
        onProgress(from, total)
        val to = from + batch.size
        val r = readDocumentsWithAi(llm, transcribePrompt(d.kind, from + 1, to, total), batch.map { DocFile("image/jpeg", it, d.displayName) },
            EcChatRules.READ_TIMEOUT_MS, jsonOut = false)
        when (r) {
            is DocAiResult.Ok -> { out.appendLine(r.text.trim()); model = r.model }
            is DocAiResult.Failed -> return Result.failure(Exception("pages ${from + 1}–$to: " + r.message + (if (r.model.isNotBlank()) " · ${r.model}" else "")))
        }
        from = to
        if (from < total) batch = pages(d.fileName, from, EcChatRules.PAGES_PER_CALL)?.second.orEmpty()
    }
    onProgress(total, total)
    return Result.success(DocText(d.id, total, out.toString().trim(), model, nowMillis()))
}

/** The saved text of the documents, EC first, cut to [max] characters. */
fun documentsBlock(docs: List<Pair<PropertyDocument, DocText>>, max: Int = EcChatRules.MAX_CONTEXT_CHARS): String = buildString {
    val order = listOf(DocKind.EC, DocKind.PATTA, DocKind.CHITTA, DocKind.ADANGAL, DocKind.SALE_DEED, DocKind.OTHER)
    for ((d, t) in docs.sortedBy { order.indexOf(it.first.kind) }) {
        if (length >= max) break
        appendLine("##### DOCUMENT: ${d.kind.label().first} – ${d.displayName} (${t.pages} pages)")
        val room = max - length
        appendLine(if (t.text.length > room) t.text.take(room) + "\n[… cut, too long]" else t.text)
    }
}

/** Prompt for one EC chat question, with the documents' text and the last messages. */
fun chatPrompt(land: LandRecord, propertyTitle: String, docs: List<Pair<PropertyDocument, DocText>>, history: List<DocChatMsg>, question: String): String = buildString {
    appendLine("You help a property buyer in Tamil Nadu understand their Encumbrance Certificate (EC) and land documents (patta, chitta, adangal, sale deed).")
    appendLine("Answer ONLY from the document text below. Never invent a name, date, number or value. If the text does not show it, say so plainly.")
    appendLine("When the question names a survey number, patta number, person, year or document number, find EVERY matching entry and list them oldest first: date, document No./year, nature, from (executant) → to (claimant), survey No., extent, value, and the Sl.No / page.")
    appendLine("\"Full analysis\" means: the ownership chain for the property's survey number from the oldest entry to the current owner, then every red flag – mortgage or loan not released, court attachment or lis pendens, cancelled deed, power-of-attorney sale or open power, agreement of sale not completed, gaps or breaks in the chain, extent or survey changes, big value drops, patta holder different from the last buyer, government / poramboke land.")
    appendLine("Keep answers short and clear; use simple lines, not tables. Reply in the language of the question (Tamil, English or Tanglish → simple Tamil with English terms). End with one line that this is a first reading, not legal advice, only when you list red flags.")
    appendLine()
    appendLine("Property: \"$propertyTitle\" – district ${land.district.ifBlank { "?" }}, taluk ${land.taluk.ifBlank { "?" }}, village ${land.village.ifBlank { "?" }}, survey No. ${land.surveyFull.ifBlank { land.surveyNo }.ifBlank { "not given" }}, patta No. ${land.pattaNo.ifBlank { "not given" }}.")
    appendLine()
    appendLine(documentsBlock(docs))
    val past = history.takeLast(EcChatRules.HISTORY)
    if (past.isNotEmpty()) {
        appendLine("##### EARLIER CONVERSATION")
        past.forEach { m -> appendLine((if (m.fromUser) "User: " else "You: ") + m.text.take(3000)) }
    }
    appendLine("##### QUESTION")
    appendLine(question.trim())
}

/** Text-only call (any provider), trying the same models as the document reading. */
suspend fun askText(llm: LlmConfig, prompt: String, timeoutMs: Int = EcChatRules.ANSWER_TIMEOUT_MS): DocAiResult {
    if (!llm.hasKey) return DocAiResult.Failed("no AI key")
    val models = docModels(llm)
    if (models.isEmpty()) return DocAiResult.Failed("could not list the models of this key (no internet?)")
    var last = DocAiResult.Failed("failed", models.first())
    for (m in models) {
        when (val r = chat(llm.provider, llm.apiKey, m, prompt, timeoutMs)) {
            is ChatResult.Ok -> if (r.text.isNotBlank()) return DocAiResult.Ok(r.text.trim(), m) else last = DocAiResult.Failed("empty answer", m)
            is ChatResult.Fail -> {
                last = DocAiResult.Failed("HTTP ${r.e.code}: " + r.e.message.take(160), m)
                if (!tryNextModel(r.e.code)) return last
            }
            ChatResult.Offline -> last = DocAiResult.Failed("no internet / no answer in ${timeoutMs / 1000} s", m)
        }
    }
    return last
}

/** Ready-made questions for the chips above the chat box. */
fun quickQuestions(land: LandRecord, tamil: Boolean): List<String> = buildList {
    val sv = land.surveyFull.ifBlank { land.surveyNo }
    if (sv.isNotBlank()) add(if (tamil) "சர்வே எண் $sv – முழு உரிமை வரிசை" else "Trace survey No. $sv – full ownership chain")
    if (land.pattaNo.isNotBlank()) add(if (tamil) "பட்டா எண் ${land.pattaNo} உள்ள பதிவுகள்" else "Entries for patta No. ${land.pattaNo}")
    add(if (tamil) "முழு பகுப்பாய்வு – உரிமை வரிசை + எச்சரிக்கைகள்" else "Full analysis – chain + red flags")
    add(if (tamil) "இப்போது உரிமையாளர் யார்?" else "Who is the current owner?")
    add(if (tamil) "கடன் / அடமானம் / நீதிமன்ற தடை உள்ளதா?" else "Any loan, mortgage or court attachment?")
    add(if (tamil) "EC-ல் உள்ள எல்லா சர்வே எண்களும்" else "List every survey No. in the EC")
}
