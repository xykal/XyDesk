package id.xyverse.xydesk.ui.kit

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.R

/**
 * Logo resmi utama XyDesk (menggunakan asset asli).
 */
@Composable
fun XyDeskMark(
    size: Dp = 56.dp,
    modifier: Modifier = Modifier,
    elevated: Boolean = true,
) {
    val shape = RoundedCornerShape(size * 0.22f)
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (elevated) {
                    Modifier.shadow(
                        elevation = size * 0.12f,
                        shape = shape,
                        ambientColor = Xy.accent.copy(alpha = 0.25f),
                        spotColor = Xy.accent.copy(alpha = 0.35f),
                    )
                } else Modifier,
            )
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_xy),
            contentDescription = "XyDesk Logo",
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    }
}
