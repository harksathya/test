package com.hark.kuralkathaigal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hark.kuralkathaigal.data.KuralStory
import com.hark.kuralkathaigal.data.Library
import com.hark.kuralkathaigal.data.categories
import com.hark.kuralkathaigal.data.kuralStories
import com.hark.kuralkathaigal.data.storyByNumber
import com.hark.kuralkathaigal.ui.components.CircleIconButton
import com.hark.kuralkathaigal.ui.components.StoryCover
import com.hark.kuralkathaigal.ui.theme.Accent
import com.hark.kuralkathaigal.ui.theme.Canvas
import com.hark.kuralkathaigal.ui.theme.Ink
import com.hark.kuralkathaigal.ui.theme.InkSoft
import com.hark.kuralkathaigal.ui.theme.Muted

@Composable
fun HomeScreen(
    library: Library,
    onOpenStory: (Int) -> Unit,
    onAbout: () -> Unit,
    contentPadding: PaddingValues,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(0) }

    val filtered = kuralStories.filter { s ->
        (selected == 0 || s.category == categories[selected]) &&
            (query.isBlank() || s.title.contains(query, true) || s.line1.contains(query) ||
                s.line2.contains(query) || s.transliteration.contains(query, true) ||
                s.number.toString() == query.trim())
    }
    val featured = if (library.lastRead.value > 0) storyByNumber(library.lastRead.value) else kuralStories.first()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Canvas),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 16.dp,
            top = contentPadding.calculateTopPadding())
    ) {
        // Top bar
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleIconButton(Icons.Outlined.Menu, "மெனு", onAbout)
                Text(
                    "குறள் கதைகள்",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                CircleIconButton(Icons.Outlined.Info, "பற்றி", onAbout)
            }
        }
        // Search
        item {
            Row(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Search, null, tint = InkSoft, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("குறள் அல்லது கதையைத் தேடுங்கள்…", fontSize = 13.sp, color = Muted)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = Ink),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(Modifier.width(1.dp).height(20.dp).background(Color(0xFFE6E6EC)))
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Outlined.Tune, null, tint = InkSoft, modifier = Modifier.size(20.dp))
            }
        }
        // Category tabs
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                items(categories.size) { i ->
                    Column(
                        Modifier.clickable { selected = i },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            categories[i],
                            fontSize = 13.sp,
                            fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (i == selected) Accent else Muted
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.height(2.dp).width(28.dp)
                                .background(if (i == selected) Accent else Color.Transparent)
                        )
                    }
                }
            }
        }
        // Featured banner
        if (query.isBlank() && selected == 0) {
            item { FeaturedBanner(featured, continuing = library.lastRead.value > 0) { onOpenStory(featured.number) } }
            item { SectionHeader("மிகவும் பிரபலம்", "${kuralStories.size} கதைகள்") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(kuralStories, key = { it.number }) { s ->
                        PopularCard(s, library.isBookmarked(s.number), { library.toggleBookmark(s.number) }) { onOpenStory(s.number) }
                    }
                }
            }
        }
        item { SectionHeader("அதிகாரம் 1 • கடவுள் வாழ்த்து", if (filtered.isEmpty()) "முடிவு இல்லை" else "${filtered.size}") }
        items(filtered, key = { "row-${it.number}" }) { s ->
            StoryRow(s) { onOpenStory(s.number) }
        }
    }
}

@Composable
private fun FeaturedBanner(story: KuralStory, continuing: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            Modifier.weight(1f).fillMaxSize().background(Color(0xFF1E2A55)).padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    if (continuing) "தொடர்ந்து படியுங்கள்" else "இன்றைய கதை",
                    color = Color(0xFFFFB199), fontSize = 11.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    story.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    lineHeight = 21.sp, maxLines = 3, overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("படிக்கத் தொடங்கு", color = Color.White, fontSize = 11.sp)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        StoryCover(story, Modifier.weight(1f).fillMaxSize(), corner = 0.dp, glyphSize = 56)
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink), modifier = Modifier.weight(1f))
        Text(trailing, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun PopularCard(story: KuralStory, bookmarked: Boolean, onBookmark: () -> Unit, onClick: () -> Unit) {
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box {
            StoryCover(story, Modifier.fillMaxWidth().height(150.dp), corner = 4.dp)
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp)
                    .clip(CircleShape).background(Color.White).clickable(onClick = onBookmark),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    "புத்தகக்குறி",
                    tint = if (bookmarked) Accent else InkSoft,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            story.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text("குறள் ${story.number} • ${story.readMinutes} நிமிடம்", fontSize = 11.sp, color = Muted)
    }
}

@Composable
private fun StoryRow(story: KuralStory, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StoryCover(story, Modifier.size(width = 56.dp, height = 72.dp), corner = 6.dp, glyphSize = 26, showNumber = false)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(story.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(story.line1, fontSize = 12.sp, color = InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(story.line2, fontSize = 12.sp, color = InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${story.number}",
            color = Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier.clip(CircleShape).background(Color(0xFFFFE7DF)).padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
