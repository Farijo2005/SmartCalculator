package com.example.smartcalculator.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun MenuIcon(modifier: Modifier = Modifier, size: Dp = 22.dp, tint: Color = LocalContentColor.current) {
    VectorIcon(Icons.Rounded.Menu, modifier, size, tint)
}

@Composable
fun HistoryIcon(modifier: Modifier = Modifier, size: Dp = 22.dp, tint: Color = LocalContentColor.current) {
    VectorIcon(Icons.Rounded.History, modifier, size, tint)
}

@Composable
fun SettingsIcon(modifier: Modifier = Modifier, size: Dp = 22.dp, tint: Color = LocalContentColor.current) {
    VectorIcon(Icons.Rounded.Settings, modifier, size, tint)
}

@Composable
fun CloseIcon(modifier: Modifier = Modifier, size: Dp = 20.dp, tint: Color = LocalContentColor.current) {
    VectorIcon(Icons.Rounded.Close, modifier, size, tint)
}

@Composable
fun ChevronRightIcon(modifier: Modifier = Modifier, size: Dp = 18.dp, tint: Color = LocalContentColor.current) {
    VectorIcon(Icons.Rounded.ChevronRight, modifier, size, tint)
}

@Composable
fun DeleteIcon(modifier: Modifier = Modifier, size: Dp = 16.dp, tint: Color = LocalContentColor.current) {
    VectorIcon(Icons.Rounded.Delete, modifier, size, tint)
}

@Composable
fun ArrowUpIcon(modifier: Modifier = Modifier, size: Dp = 14.dp, tint: Color = LocalContentColor.current) {
    VectorIcon(Icons.Rounded.ArrowUpward, modifier, size, tint)
}

@Composable
fun ArrowDownIcon(modifier: Modifier = Modifier, size: Dp = 14.dp, tint: Color = LocalContentColor.current) {
    VectorIcon(Icons.Rounded.ArrowDownward, modifier, size, tint)
}

@Composable
fun AddIcon(modifier: Modifier = Modifier, size: Dp = 18.dp, tint: Color = LocalContentColor.current) {
    VectorIcon(Icons.Rounded.Add, modifier, size, tint)
}

@Composable
fun ImageIcon(modifier: Modifier = Modifier, size: Dp = 14.dp, tint: Color = LocalContentColor.current) {
    VectorIcon(Icons.Rounded.Image, modifier, size, tint)
}

@Composable
private fun VectorIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color = LocalContentColor.current,
) {
    Box(modifier = modifier.size(size)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}
