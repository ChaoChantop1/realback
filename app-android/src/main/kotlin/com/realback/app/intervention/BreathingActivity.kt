package com.realback.app.intervention

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.realback.app.R
import com.realback.app.util.formatDurationMillis

/**
 * Breathing confirmation page (M2).
 *
 * Opened via the intervention notification's full-screen intent. It slows
 * the user down for a moment — no blocking, just a pause and the facts.
 */
class BreathingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: getString(R.string.app_name)
        val usedMillis = intent.getLongExtra(EXTRA_USED_MILLIS, 0L)
        val limitMillis = intent.getLongExtra(EXTRA_LIMIT_MILLIS, 0L)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BreathingContent(label, usedMillis, limitMillis, onDone = { finish() })
                }
            }
        }
    }

    companion object {
        const val EXTRA_LABEL = "label"
        const val EXTRA_USED_MILLIS = "usedMillis"
        const val EXTRA_LIMIT_MILLIS = "limitMillis"
    }
}

@Composable
private fun BreathingContent(
    label: String,
    usedMillis: Long,
    limitMillis: Long,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "深呼吸，停一停",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(32.dp))
        BreathingCircle()
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "「$label」今日已用 ${formatDurationMillis(usedMillis)}",
            style = MaterialTheme.typography.titleMedium,
        )
        if (limitMillis > 0L) {
            Text(
                text = "你给自己设的限额是 ${formatDurationMillis(limitMillis)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "接下来的时间，你打算怎么用？",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onDone) {
            Text("我知道了")
        }
    }
}

/** Slow pulsing circle: inhale 4s, exhale 4s. */
@Composable
private fun BreathingCircle() {
    val transition = rememberInfiniteTransition(label = "breath")
    val scale by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    Box(
        modifier = Modifier
            .size(160.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    )
}
