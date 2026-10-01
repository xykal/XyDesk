package id.xyverse.xydesk.ui.kit

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import id.xyverse.xydesk.R

enum class Social(val res: Int) {
    TIKTOK(R.drawable.ic_social_tiktok),
    INSTAGRAM(R.drawable.ic_social_instagram),
    YOUTUBE(R.drawable.ic_social_youtube),
    X(R.drawable.ic_social_x),
    WEB(R.drawable.ic_social_web),
}

/** Logo resmi dari Simple Icons (CC0), diwarnai putih di atas lingkaran brand. */
@Composable
fun SocialMark(kind: Social, modifier: Modifier = Modifier, color: Color = Color.White) {
    Image(painterResource(kind.res), kind.name, modifier, colorFilter = ColorFilter.tint(color))
}

@Composable
fun GoogleMark(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_google_g), "Google", modifier)
}
