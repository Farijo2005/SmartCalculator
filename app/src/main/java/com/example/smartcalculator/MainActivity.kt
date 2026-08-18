package com.example.smartcalculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartcalculator.calc.CalculatorViewModel
import com.example.smartcalculator.ui.CalculatorScreen
import com.example.smartcalculator.ui.theme.SmartCalculatorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: CalculatorViewModel = viewModel(
                factory = CalculatorViewModel.Companion.Factory(application),
            )
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            SmartCalculatorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    var menuOpen by remember { mutableStateOf(false) }
                    var historyOpen by remember { mutableStateOf(false) }
                    var settingsOpen by remember { mutableStateOf(false) }
                    var aboutOpen by remember { mutableStateOf(false) }

                    val closeDrawers = {
                        menuOpen = false
                        historyOpen = false
                        settingsOpen = false
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        CalculatorScreen(
                            viewModel = viewModel,
                            onMenuClick = {
                                closeDrawers()
                                menuOpen = true
                            },
                            onHistoryClick = {
                                closeDrawers()
                                historyOpen = true
                            },
                            onSettingsClick = {
                                closeDrawers()
                                settingsOpen = true
                            },
                            onAbout = {
                                aboutOpen = true
                            },
                            isMenuOpen = menuOpen,
                            isHistoryOpen = historyOpen,
                            isSettingsOpen = settingsOpen,
                            onCloseDrawers = { closeDrawers() },
                        )

                        if (aboutOpen) {
                            AboutDialog(onDismiss = { aboutOpen = false })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("好") }
        },
        title = {
            Text(
                text = "科学计算器",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("版本 1.0（阶段一）", style = MaterialTheme.typography.bodyMedium)
                Text("已完成：框架与三个抽屉", style = MaterialTheme.typography.bodySmall)
                Text("待完善：显示区、按键区、计算引擎", style = MaterialTheme.typography.bodySmall)
            }
        },
        modifier = Modifier.width(320.dp),
    )
}
