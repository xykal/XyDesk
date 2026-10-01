package id.xyverse.xydesk.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.xyverse.xydesk.R
import id.xyverse.xydesk.ui.kit.Xy

/**
 * Latar atas halaman login: ilustrasi penuh yang diam, lalu gradasi putih
 * menimpa bagian bawah supaya kartu login tumbuh dari ilustrasi.
 */
@Composable
fun LoginHero(height: Dp = 400.dp) {
    Box(Modifier.fillMaxWidth().height(height)) {
        Image(painterResource(R.drawable.hero_login), null, Modifier.fillMaxSize(), alignment = Alignment.TopCenter, contentScale = ContentScale.Crop)
        Box(
            Modifier.fillMaxWidth().height(height * 0.45f).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Xy.bg.copy(alpha = 0.92f), Xy.bg))),
        )
    }
}
