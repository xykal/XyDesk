package id.xyverse.xydesk.ui.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.core.LocalLang
import id.xyverse.xydesk.core.tr
import androidx.compose.ui.unit.sp

/** Teks diterjemahkan otomatis menurut LocalLang. */
@Composable
fun XyText(text: String, style: TextStyle = Xy.body, modifier: Modifier = Modifier, color: Color? = null, maxLines: Int = Int.MAX_VALUE) =
    BasicText(text.tr(LocalLang.current), modifier, style = if (color != null) style.copy(color = color) else style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)

@Composable
fun t(text: String): String = text.tr(LocalLang.current)

/** Tombol utama: gradien ungu, tanpa ripple, mengecil halus saat ditekan. */
@Composable
fun XyButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, ghost: Boolean = false, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = 600f), label = "press")
    val shape = RoundedCornerShape(Xy.pill)
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .scale(scale)
            .clip(shape)
            .then(if (ghost) Modifier.background(Xy.overlay) else Modifier.shadow(10.dp, shape, ambientColor = Xy.shadow, spotColor = Xy.shadow).background(Xy.accentBrush))
            .clickable(source, indication = null, enabled = enabled) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        XyText(text, Xy.title.copy(fontSize = 15.5.sp), color = if (!enabled) Xy.textLow else if (ghost) Xy.textHi else Color.White)
    }
}

@Composable
fun XyField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    mono: Boolean = false,
    hint: String = "",
    transformation: VisualTransformation = VisualTransformation.None,
) {
    val shape = RoundedCornerShape(Xy.pill)
    Column(modifier.fillMaxWidth()) {
        XyText(label.uppercase(), Xy.label)
        Spacer(Modifier.height(6.dp))
        var focused by remember { mutableStateOf(false) }
        val ring by animateColorAsState(if (focused) Xy.accent else Xy.line, label = "ring")
        Box(
            Modifier.fillMaxWidth().clip(shape).background(Xy.input).border(1.5.dp, ring, shape).padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            if (value.isEmpty()) XyText(hint, if (mono) Xy.mono else Xy.body, color = Xy.textLow)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = if (mono) Xy.mono else Xy.body,
                cursorBrush = SolidColor(Xy.lavender),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                visualTransformation = transformation,
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            )
        }
    }
}

@Composable
fun XyCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(Xy.radiusL)
    Box(modifier.fillMaxWidth().shadow(18.dp, shape, ambientColor = Xy.shadow, spotColor = Xy.shadow).clip(shape).background(Xy.raised).padding(Xy.pad)) {
        Column { content() }
    }
}

@Composable
fun XyCheck(text: String, caption: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Xy.radiusS)).clickable(remember { MutableInteractionSource() }, null) { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (checked) Xy.accent else Xy.overlay)
                .border(1.dp, if (checked) Xy.accent else Xy.line, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) XyText("✓", Xy.label.copy(color = Color.White, fontSize = 12.sp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            XyText(text, Xy.body)
            XyText(caption, Xy.caption)
        }
    }
}

@Composable
fun XyToggle(text: String, caption: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val knob by animateFloatAsState(if (checked) 1f else 0f, label = "knob")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Xy.radiusS)).clickable(remember { MutableInteractionSource() }, null) { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            XyText(text, Xy.body)
            XyText(caption, Xy.caption)
        }
        Spacer(Modifier.width(12.dp))
        val track by animateColorAsState(if (checked) Xy.accent else Xy.line, label = "track")
        Box(Modifier.width(44.dp).height(26.dp).clip(CircleShape).background(track).padding(3.dp)) {
            Box(Modifier.padding(start = (18 * knob).dp).size(20.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
fun XyNotice(text: String, tone: Color = Xy.textMid) {
    if (text.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(tone))
        XyText(text, Xy.caption, color = tone)
    }
}
