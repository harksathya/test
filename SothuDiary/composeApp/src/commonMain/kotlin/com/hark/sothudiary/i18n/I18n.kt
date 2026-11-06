package com.hark.sothudiary.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import com.hark.sothudiary.data.Lang

/** One UI string in English and Tamil. Placeholders are {0}, {1}, ... */
data class Tr(val en: String, val ta: String) {
    fun of(lang: Lang): String = if (lang == Lang.TA) ta else en
    fun format(lang: Lang, vararg args: Any?): String = fill(of(lang), args)
}

internal fun fill(template: String, args: Array<out Any?>): String {
    var out = template
    args.forEachIndexed { i, a -> out = out.replace("{$i}", a?.toString() ?: "") }
    return out
}

val LocalLang = compositionLocalOf { Lang.EN }

@Composable
@ReadOnlyComposable
fun Tr.s(): String = of(LocalLang.current)

@Composable
@ReadOnlyComposable
fun Tr.f(vararg args: Any?): String = fill(of(LocalLang.current), args)

/** "1 property" / "3 properties" (Tamil: "1 சொத்து" / "3 சொத்துகள்"). */
@androidx.compose.runtime.Composable
fun propertiesCount(n: Int): String = if (n == 1) S.oneProperty.s() else S.nResults.f(n)
