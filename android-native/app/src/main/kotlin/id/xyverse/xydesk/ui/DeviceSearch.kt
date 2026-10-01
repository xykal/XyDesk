package id.xyverse.xydesk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.ui.kit.Icon
import id.xyverse.xydesk.ui.kit.Xy
import id.xyverse.xydesk.ui.kit.XyIcon
import id.xyverse.xydesk.ui.kit.XyText
import id.xyverse.xydesk.ui.kit.t

/** Kolom cari host berbentuk pil: cocok ke nama, alias, atau potongan ID. */
@Composable
fun DeviceSearch(query: String, onQuery: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(Xy.pill)).background(Xy.input).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XyIcon(Icon.SEARCH, tint = Xy.textLow, size = 18.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) XyText(t("Cari nama atau ID host"), Xy.body.copy(color = Xy.textLow))
            BasicTextField(query, onQuery, textStyle = Xy.body, singleLine = true, cursorBrush = SolidColor(Xy.accent))
        }
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clickable(remember { MutableInteractionSource() }, null) { onQuery("") }) { XyIcon(Icon.CLOSE, tint = Xy.textMid, size = 16.dp) }
        }
    }
}
