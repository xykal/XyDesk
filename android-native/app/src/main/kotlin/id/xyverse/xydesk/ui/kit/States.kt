package id.xyverse.xydesk.ui.kit

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Keadaan kosong: ikon dalam lingkaran lembut, judul, keterangan, aksi opsional. */
@Composable
fun XyEmpty(icon: Icon, title: String, caption: String, action: String? = null, image: Int? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Xy.radiusL)).background(Xy.overlay).padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (image != null) Image(painterResource(image), null, Modifier.height(140.dp).fillMaxWidth(), contentScale = ContentScale.Fit)
        else Box(Modifier.size(72.dp).background(Color.White, CircleShape).border(1.dp, Xy.line, CircleShape), contentAlignment = Alignment.Center) {
            XyIcon(icon, tint = Xy.accent, size = 30.dp)
        }
        Spacer(Modifier.height(16.dp))
        XyText(title, Xy.title.copy(textAlign = TextAlign.Center))
        Spacer(Modifier.height(4.dp))
        XyText(caption, Xy.caption.copy(textAlign = TextAlign.Center))
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            XyButton(action, Modifier.width(200.dp), ghost = false, onClick = onAction)
        }
    }
}

/** Keadaan gagal: nada merah, alasan, dua aksi (coba lagi / kembali). */
@Composable
fun XyError(title: String, reason: String, retry: String? = null, onRetry: () -> Unit = {}, back: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Xy.radiusL)).background(Color.White).border(1.dp, Xy.line, RoundedCornerShape(Xy.radiusL)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp).background(Xy.danger.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
            XyIcon(Icon.CLOSE, tint = Xy.danger, size = 28.dp)
        }
        Spacer(Modifier.height(14.dp))
        XyText(title, Xy.title.copy(textAlign = TextAlign.Center))
        Spacer(Modifier.height(4.dp))
        XyText(reason, Xy.caption.copy(textAlign = TextAlign.Center))
        Spacer(Modifier.height(18.dp))
        if (retry != null) {
            XyButton(retry, onClick = onRetry)
            Spacer(Modifier.height(8.dp))
        }
        XyButton(back, ghost = true, onClick = onBack)
    }
}

/** Baris pengaturan: ikon kiri, judul + keterangan, nilai/chevron kanan. */
@Composable
fun XyRow(icon: Icon, title: String, caption: String = "", value: String = "", chevron: Boolean = true, tint: Color = Xy.accent, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Xy.radiusM))
            .then(if (onClick != null) Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(38.dp).background(tint.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
            XyIcon(icon, tint = tint, size = 19.dp)
        }
        Column(Modifier.weight(1f)) {
            XyText(title, Xy.body)
            if (caption.isNotEmpty()) XyText(caption, Xy.caption)
        }
        if (value.isNotEmpty()) XyText(value, Xy.caption.copy(color = Xy.textLow))
        if (chevron && onClick != null) XyIcon(Icon.CHEVRON, tint = Xy.textLow, size = 18.dp)
    }
}
