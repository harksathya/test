package com.hark.kuralkathaigal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hark.kuralkathaigal.data.KuralStory
import com.hark.kuralkathaigal.ui.theme.Chip
import com.hark.kuralkathaigal.ui.theme.InkSoft

/** Round outlined icon button, as used in the reference's top bars. */
@Composable
fun CircleIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(1.dp, Color(0xFFE6E6EC), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = InkSoft, modifier = Modifier.size(20.dp))
    }
}

/** Illustrated "book cover" drawn from the story's gradient and emoji. */
@Composable
fun StoryCover(story: KuralStory, modifier: Modifier = Modifier, corner: Dp = 10.dp, glyphSize: Int = 44, showNumber: Boolean = true) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(Color(story.coverStart), Color(story.coverEnd)))),
        contentAlignment = Alignment.Center
    ) {
        Text(story.emoji, fontSize = glyphSize.sp, textAlign = TextAlign.Center)
        if (showNumber) {
            Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.Start) {
                Text(
                    "குறள் ${story.number}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x33000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun TagChip(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        color = InkSoft,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Chip)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}
