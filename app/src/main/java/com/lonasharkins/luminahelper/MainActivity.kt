package com.lonasharkins.luminahelper

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonasharkins.luminahelper.accessibility.AccessibilityStatus
import com.lonasharkins.luminahelper.model.InstrumentProfile
import com.lonasharkins.luminahelper.music.KeyLayoutFactory

class MainActivity : ComponentActivity() {
    private var accessibilityEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LuminaApp(
                accessibilityEnabled = accessibilityEnabled,
                onOpenAccessibilitySettings = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        accessibilityEnabled = AccessibilityStatus.isEnabled(this)
    }
}

private val LuminaColors = darkColorScheme(
    primary = Color(0xFFB69CFF),
    onPrimary = Color(0xFF221149),
    secondary = Color(0xFF71E6D1),
    background = Color(0xFF0D0B14),
    surface = Color(0xFF171321),
    onBackground = Color(0xFFF5F0FF),
    onSurface = Color(0xFFF5F0FF),
)

@Composable
private fun LuminaApp(
    accessibilityEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
) {
    MaterialTheme(colorScheme = LuminaColors) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = "Lumina Helper",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Instrumentos de qualquer tamanho, mapeados do seu jeito.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    fontSize = 16.sp,
                )

                AccessibilityCard(
                    enabled = accessibilityEnabled,
                    onOpenSettings = onOpenAccessibilitySettings,
                )

                InstrumentBuilderPreview()
            }
        }
    }
}

@Composable
private fun AccessibilityCard(
    enabled: Boolean,
    onOpenSettings: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .background(
                            color = if (enabled) Color(0xFF55E69A) else Color(0xFFFFB05C),
                            shape = CircleShape,
                        ),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (enabled) "Serviço de toque ativado" else "Serviço de toque desativado",
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = "Você controla quando o Lumina pode tocar nas posições que forem calibradas.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(if (enabled) "Ver acessibilidade" else "Ativar acessibilidade")
            }
        }
    }
}

@Composable
private fun InstrumentBuilderPreview() {
    var keyCount by rememberSaveable { mutableIntStateOf(8) }
    val profile = remember(keyCount) {
        KeyLayoutFactory.centeredChromatic(
            id = "preview",
            name = "Instrumento personalizado",
            keyCount = keyCount,
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Quantidade livre de teclas",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Use poucas teclas para o piano de emoji do JJS ou aumente para instrumentos maiores.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { keyCount-- },
                    enabled = keyCount > 1,
                ) {
                    Text("−")
                }
                Text(
                    text = "$keyCount teclas",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                )
                Button(
                    onClick = { keyCount++ },
                    enabled = keyCount < 88,
                ) {
                    Text("+")
                }
            }

            InstrumentKeys(profile)

            Text(
                text = "A posição de cada tecla será marcada sobre o jogo na etapa de calibração.",
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun InstrumentKeys(profile: InstrumentProfile) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        profile.keys.chunked(5).forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                rowKeys.forEach { key ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = key.label,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
                repeat(5 - rowKeys.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
