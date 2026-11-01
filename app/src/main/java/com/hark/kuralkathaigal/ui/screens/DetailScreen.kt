package com.hark.kuralkathaigal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hark.kuralkathaigal.data.AUTHOR
import com.hark.kuralkathaigal.data.CHAPTER
import com.hark.kuralkathaigal.data.Library
import com.hark.kuralkathaigal.data.SECTION
import com.hark.kuralkathaigal.data.storyByNumber
import com.hark.kuralkathaigal.ui.components.CircleIconButton
import com.hark.kuralkathaigal.ui.components.StoryCover
import com.hark.kuralkathaigal.ui.components.TagChip
import com.hark.kuralkathaigal.ui.theme.Accent
import com.hark.kuralkathaigal.ui.theme.AccentSoft
import com.hark.kuralkathaigal.ui.theme.Canvas
import com.hark.kuralkathaigal.ui.theme.Ink
import com.hark.kuralkathaigal.ui.theme.InkSoft
import com.hark.kuralkathaigal.ui.theme.Muted

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(number: Int, library: Library, onBack: () -> Unit, onRead: () -> Unit) {
    val story = storyByNumber(number)
    val bookmarked = library.isBookmarked(number)

    Column(Modifier.fillMaxSize().background(Canvas).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "பின்செல்", onBack)
            Text(
                "விவரம்", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center
            )
            CircleIconButton(
                if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                "புத்தகக்குறி"
            ) { library.toggleBookmark(number) }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // Cover + title block
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Box(Modifier.width(118.dp).height(160.dp)) {
                    Box(
                        Modifier.offset(x = (-6).dp, y = (-6).dp).width(112.dp).height(156.dp)
                            .clip(RoundedCornerShape(4.dp)).background(Color(0xFF1E2A55))
                    )
                    StoryCover(story, Modifier.width(112.dp).height(156.dp), corner = 4.dp, glyphSize = 48)
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(story.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 24.sp, color = Ink)
                    Spacer(Modifier.height(4.dp))
                    Text("$AUTHOR • குறள் ${story.number}", fontSize = 12.sp, color = Muted)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, tint = Accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${story.readMinutes} நிமிட வாசிப்பு", fontSize = 12.sp, color = InkSoft)
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TagChip(story.category)
                        story.tags.forEach { TagChip(it) }
                    }
                }
            }

            // The kural itself
            Column(
                Modifier.padding(20.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)).background(AccentSoft).padding(18.dp)
            ) {
                Text("குறள் ${story.number} • $CHAPTER", fontSize = 11.sp, color = Accent, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(story.line1, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink, lineHeight = 26.sp)
                Text(story.line2, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink, lineHeight = 26.sp)
                Spacer(Modifier.height(8.dp))
                Text(story.transliteration, fontSize = 11.sp, color = InkSoft, fontStyle = FontStyle.Italic)
            }

            SectionTitle("பொருள்")
            Text(story.meaning, fontSize = 14.sp, lineHeight = 23.sp, color = InkSoft, modifier = Modifier.padding(horizontal = 20.dp))

            SectionTitle("கதைச் சுருக்கம்")
            Text(story.story.first(), fontSize = 14.sp, lineHeight = 23.sp, color = InkSoft, modifier = Modifier.padding(horizontal = 20.dp))

            SectionTitle("நூல்")
            Row(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Color.White).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(19.dp)).background(AccentSoft),
                    contentAlignment = Alignment.Center
                ) { Text("வ", color = Accent, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("திருக்குறள் — $AUTHOR", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(SECTION, fontSize = 12.sp, color = Muted)
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        Button(
            onClick = onRead,
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("படிக்கத் தொடங்கு", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp)
    )
}
