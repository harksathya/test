package com.hark.kuralkathaigal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hark.kuralkathaigal.data.AUTHOR
import com.hark.kuralkathaigal.data.Library
import com.hark.kuralkathaigal.data.storyByNumber
import com.hark.kuralkathaigal.ui.components.StoryCover
import com.hark.kuralkathaigal.ui.theme.Canvas
import com.hark.kuralkathaigal.ui.theme.Ink
import com.hark.kuralkathaigal.ui.theme.InkSoft
import com.hark.kuralkathaigal.ui.theme.Muted

@Composable
fun SavedScreen(library: Library, onOpenStory: (Int) -> Unit, contentPadding: PaddingValues) {
    val saved = library.bookmarks.toList()
    Column(Modifier.fillMaxSize().background(Canvas).padding(contentPadding)) {
        Text(
            "சேமித்த கதைகள்", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink,
            modifier = Modifier.fillMaxWidth().padding(20.dp), textAlign = TextAlign.Center
        )
        if (saved.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(40.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🔖", fontSize = 48.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "இன்னும் எதுவும் சேமிக்கவில்லை.\nகதை அட்டையில் உள்ள புத்தகக்குறியைத் தட்டுங்கள்.",
                    fontSize = 14.sp, color = Muted, textAlign = TextAlign.Center, lineHeight = 22.sp
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                items(saved, key = { it }) { n ->
                    val s = storyByNumber(n)
                    Row(
                        Modifier.padding(vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Color.White).clickable { onOpenStory(n) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StoryCover(s, Modifier.size(width = 56.dp, height = 72.dp), corner = 6.dp, glyphSize = 26, showNumber = false)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(s.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text("$AUTHOR • குறள் ${s.number}", fontSize = 12.sp, color = InkSoft)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen(contentPadding: PaddingValues) {
    Column(Modifier.fillMaxSize().background(Canvas).padding(contentPadding).padding(24.dp)) {
        Text("குறள் கதைகள் பற்றி", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
        Spacer(Modifier.height(12.dp))
        Text(
            "திருக்குறளின் முதல் அதிகாரமான ‘கடவுள் வாழ்த்து’-இல் உள்ள பத்து குறள்களையும், " +
                "ஒவ்வொன்றையும் ஒரு சிறு கதையாக இந்தச் செயலி சொல்கிறது. ஒவ்வொரு கதையிலும் குறள், " +
                "அதன் எளிய பொருள், கதை, அது சொல்லும் நீதி ஆகியவை உள்ளன.",
            fontSize = 14.sp, lineHeight = 23.sp, color = InkSoft
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "குறள் பாடம்: Project Madurai ஒருங்குறிப் பதிப்பு.\nவடிவமைப்புத் தூண்டுதல்: Read.io (Dribbble).",
            fontSize = 12.sp, lineHeight = 20.sp, color = Muted
        )
    }
}
