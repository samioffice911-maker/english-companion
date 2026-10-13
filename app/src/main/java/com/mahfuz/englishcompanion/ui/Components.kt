package com.mahfuz.englishcompanion.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Ico(@DrawableRes id: Int, tint: Color = C.Text, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Icon(painterResource(id), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    border: Color = C.Border,
    background: Color = C.Card,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    var m = modifier
        .clip(shape)
        .background(background)
        .border(1.dp, border.copy(alpha = 0.6f), shape)
    if (onClick != null) m = m.clickable { onClick() }
    Box(m.padding(14.dp)) { content() }
}

@Composable
fun StatTile(@DrawableRes icon: Int, iconTint: Color, label: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) { Ico(icon, iconTint, 15.dp) }
                Spacer(Modifier.width(8.dp))
                Text(label, color = C.Muted, fontSize = 12.sp, maxLines = 1)
            }
            Spacer(Modifier.size(8.dp))
            Text(value, color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
fun EmojiTile(emoji: String, label: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(label, color = C.Muted, fontSize = 12.sp, maxLines = 1)
            }
            Spacer(Modifier.size(8.dp))
            Text(value, color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
fun ProgressRing(progress: Float, size: Dp = 64.dp, stroke: Dp = 7.dp) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(800), label = "ring")
    Canvas(Modifier.size(size)) {
        val s = stroke.toPx()
        drawArc(C.Border, 0f, 360f, false, style = Stroke(s))
        drawArc(
            Brush.sweepGradient(listOf(C.Cyan, C.Green, C.Cyan)),
            -90f, 360f * p, false, style = Stroke(s, cap = StrokeCap.Round)
        )
    }
}

/** The glowing voice orb. Pulses gently, and grows with the user's voice. */
@Composable
fun VoiceOrb(size: Dp, active: Boolean, level: Float, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "orb")
    val pulse by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "pulse"
    )
    val lv by animateFloatAsState(if (active) level else 0f, tween(120), label = "lv")
    Box(
        Modifier.size(size).clickable(
            interactionSource = null, indication = null, onClick = onClick
        ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2f
            val core = r * 0.58f * (1f + lv * 0.25f)
            // expanding rings
            if (active) {
                for (k in 0..1) {
                    val ph = (pulse + k * 0.5f) % 1f
                    drawCircle(C.Cyan.copy(alpha = (1f - ph) * 0.35f), radius = core + (r - core) * ph, style = Stroke(2.dp.toPx()))
                }
            }
            // glow
            drawCircle(
                Brush.radialGradient(listOf(C.Blue.copy(alpha = 0.55f), Color.Transparent), radius = r),
                radius = r
            )
            // ring
            drawCircle(
                Brush.sweepGradient(listOf(C.Cyan, C.Blue, Color(0xFF7B5CFF), C.Cyan)),
                radius = core, style = Stroke(5.dp.toPx())
            )
            drawCircle(Brush.radialGradient(listOf(C.BlueDeep, Color(0xFF0A1640)), radius = core), radius = core - 3.dp.toPx())
        }
        // sound bars inside
        Row(horizontalArrangement = Arrangement.spacedBy(size * 0.025f), verticalAlignment = Alignment.CenterVertically) {
            val base = listOf(0.30f, 0.55f, 0.85f, 0.55f, 0.30f)
            base.forEachIndexed { i, b ->
                val wobble = if (active) (0.15f * kotlin.math.sin((pulse * 6.28f * 2) + i)) else 0f
                val h = (b + lv * 0.6f + wobble).coerceIn(0.15f, 1f)
                Box(
                    Modifier
                        .width(size * 0.045f)
                        .size(width = size * 0.045f, height = size * 0.32f * h)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                )
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = modifier.padding(vertical = 6.dp))
}

@Composable
fun ModeCard(
    @DrawableRes icon: Int, color: Color, title: String, subtitle: String,
    selected: Boolean, onClick: () -> Unit
) {
    GlassCard(
        Modifier.fillMaxWidth(),
        border = if (selected) color else C.Border,
        background = if (selected) C.CardHi else C.Card,
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) { Ico(icon, color, 22.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = C.Muted, fontSize = 12.sp)
            }
            Ico(com.mahfuz.englishcompanion.R.drawable.ic_chevron, C.Muted, 20.dp)
        }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(C.Card).border(1.dp, C.Border, RoundedCornerShape(50)).padding(3.dp)
    ) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) C.Blue else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(o, color = if (sel) Color.White else C.Muted, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}
