package ir.dinal.storehub.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.dinal.storehub.R
import ir.dinal.storehub.ui.theme.DinalGold
import ir.dinal.storehub.ui.theme.DinalPlum
import ir.dinal.storehub.ui.theme.DinalPurple
import kotlinx.coroutines.delay

private val SplashRed = Color(0xFFE52E3D)

@Composable
fun DinalSplashScreen(onFinished: () -> Unit) {
    var started by remember { mutableStateOf(false) }
    var titleVisible by remember { mutableStateOf(false) }

    val cartX by animateDpAsState(
        targetValue = if (started) 0.dp else (-280).dp,
        animationSpec = spring(
            dampingRatio = 0.58f,
            stiffness = Spring.StiffnessLow
        ),
        label = "cartX"
    )
    val cartRotation by animateFloatAsState(
        targetValue = if (started) 0f else -16f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow),
        label = "cartRotation"
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (titleVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 620),
        label = "titleAlpha"
    )
    val titleY by animateDpAsState(
        targetValue = if (titleVisible) 0.dp else 18.dp,
        animationSpec = tween(durationMillis = 620),
        label = "titleY"
    )
    val accentWidth by animateDpAsState(
        targetValue = if (titleVisible) 96.dp else 0.dp,
        animationSpec = tween(durationMillis = 650, delayMillis = 80),
        label = "accentWidth"
    )

    LaunchedEffect(Unit) {
        started = true
        delay(420)
        titleVisible = true
        delay(1650)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFFFAF7),
                        Color.White,
                        DinalPurple.copy(alpha = 0.08f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 80.dp, y = (-70).dp)
                .size(220.dp)
                .background(DinalGold.copy(alpha = .10f), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-90).dp, y = 80.dp)
                .size(260.dp)
                .background(DinalPurple.copy(alpha = .07f), CircleShape)
        )

        Column(
            modifier = Modifier.padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier
                    .offset(x = cartX)
                    .graphicsLayer(rotationZ = cartRotation)
                    .shadow(22.dp, CircleShape),
                shape = CircleShape,
                color = Color.White,
                tonalElevation = 8.dp
            ) {
                Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.dinal_cart_mark),
                        contentDescription = "سبد خرید دینال",
                        modifier = Modifier.size(104.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            Column(
                modifier = Modifier
                    .offset(y = titleY)
                    .graphicsLayer(alpha = titleAlpha),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "DINALKADO.COM",
                    color = DinalPlum,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.6.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = "فروشگاه دینال",
                    color = DinalPurple,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .width(accentWidth)
                        .height(4.dp)
                        .background(
                            Brush.horizontalGradient(listOf(SplashRed, DinalGold)),
                            RoundedCornerShape(99.dp)
                        )
                )
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = DinalPlum.copy(alpha = .06f)
                ) {
                    Text(
                        text = "StoreHub • مدیریت هوشمند فروشگاه",
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 7.dp),
                        color = DinalPlum.copy(alpha = .75f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
