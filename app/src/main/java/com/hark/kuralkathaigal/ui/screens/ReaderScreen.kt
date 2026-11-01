package com.hark.kuralkathaigal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hark.kuralkathaigal.data.Library
import com.hark.kuralkathaigal.data.kuralStories
import com.hark.kuralkathaigal.data.storyByNumber
import com.hark.kuralkathaigal.ui.components.CircleIconButton
import com.hark.kuralkathaigal.ui.components.StoryCover
import com.hark.kuralkathaigal.ui.theme.Accent
import com.hark.kuralkathaigal.ui.theme.AccentSoft
import com.hark.kuralkathaigal.ui.theme.Ink
import com.hark.kuralkathaigal.ui.theme.InkSoft
import com.hark.kuralkathaigal.ui.theme.Muted

@Composable
fun ReaderScreen(number: Int, library: Library, onBack: () -> Unit, onGoTo: (Int) -> Unit) {
    val story = storyByNumber(number)
    val scale = library.fontScale.value
    val total = kuralStories.size
    val scroll = rememberScrollState()

    LaunchedEffect(number) {
        library.markRead(number)
        scroll.scrollTo(0)
    }

    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding().navigationBarsPadding()) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "பின்செல்", onBack)
            Text(
                story.title, fontSize = 14.sp, color = Ink, maxLines = 1,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp), textAlign = TextAlign.Center
            )
            Spacer(Modifier.width(40.dp))
        }

        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 22.dp)) {
            Text(
                "கதை ${story.number}",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp), textAlign = TextAlign.Center
            )

            // Kural card
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AccentSoft).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(story.line1, fontSize = (15 * scale).sp, fontWeight = FontWeight.SemiBold, color = Ink, textAlign = TextAlign.Center, lineHeight = (24 * scale).sp)
                Text(story.line2, fontSize = (15 * scale).sp, fontWeight = FontWeight.SemiBold, color = Ink, textAlign = TextAlign.Center, lineHeight = (24 * scale).sp)
                Spacer(Modifier.height(8.dp))
                Text("— திருக்குறள் ${story.number}", fontSize = 11.sp, color = Accent)
            }

            Spacer(Modifier.height(18.dp))

            SelectionContainer {
                Column {
                    story.story.forEachIndexed { i, para ->
                        Text(
                            para,
                            fontSize = (15 * scale).sp,
                            lineHeight = (27 * scale).sp,
                            color = Ink,
                            textAlign = TextAlign.Justify,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )
                        // Illustration after the second paragraph, like the reference reader.
                        if (i == 1) {
                            StoryCover(
                                story,
                                Modifier.fillMaxWidth().height(180.dp).padding(bottom = 14.dp),
                                corner = 6.dp, glyphSize = 84, showNumber = false
                            )
                        }
                    }
                }
            }

            // Meaning + moral
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF6F6F8)).padding(16.dp)
            ) {
                Text("பொருள்", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink)
                Spacer(Modifier.height(6.dp))
                Text(story.meaning, fontSize = (14 * scale).sp, lineHeight = (23 * scale).sp, color = InkSoft)
                Spacer(Modifier.height(12.dp))
                Text("கதை சொல்லும் நீதி", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Accent)
                Spacer(Modifier.height(6.dp))
                Text(story.moral, fontSize = (14 * scale).sp, lineHeight = (23 * scale).sp, color = Ink)
            }

            // Prev / next
            Row(
                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (number > 1) NavPill("முந்தைய கதை", Icons.AutoMirrored.Outlined.ArrowBack, true) { onGoTo(number - 1) }
                else Spacer(Modifier.width(1.dp))
                if (number < total) NavPill("அடுத்த கதை", Icons.AutoMirrored.Outlined.ArrowForward, false) { onGoTo(number + 1) }
            }
        }

        HorizontalDivider(color = Color(0xFFEDEDF1))
        // Bottom bar, like the reference: chapter + page, then tools.
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("அதிகாரம் 1", fontSize = 11.sp, color = Muted, modifier = Modifier.weight(1f))
                Text("பக்கம்", fontSize = 11.sp, color = Muted)
            }
            Row(Modifier.fillMaxWidth()) {
                Text("கடவுள் வாழ்த்து", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
                Text("$number / $total", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                val marked = library.isBookmarked(number)
                ToolIcon(if (marked) Icons.Filled.Star else Icons.Outlined.StarBorder, "புத்தகக்குறி", marked) {
                    library.toggleBookmark(number)
                }
                ToolIcon(Icons.Outlined.TextFields, "எழுத்து அளவு", scale != 1f) { library.cycleFont() }
                ToolIcon(Icons.AutoMirrored.Outlined.ArrowForward, "அடுத்தது", false) {
                    onGoTo(if (number < total) number + 1 else 1)
                }
            }
        }
    }
}

@Composable
private fun NavPill(label: String, icon: ImageVector, leading: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(AccentSoft).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading) { Icon(icon, null, tint = Accent, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(label, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (!leading) { Spacer(Modifier.width(6.dp)); Icon(icon, null, tint = Accent, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
private fun ToolIcon(icon: ImageVector, desc: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = if (active) Accent else InkSoft, modifier = Modifier.size(22.dp))
    }
}
