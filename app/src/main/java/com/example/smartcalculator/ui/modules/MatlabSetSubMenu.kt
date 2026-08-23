package com.example.smartcalculator.ui.modules

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartcalculator.ui.components.GlassItemButton
import com.example.smartcalculator.ui.components.isDarkTheme
import com.example.smartcalculator.ui.theme.Text200

/**
 * MATLAB 集二级子菜单。
 *
 * 渲染 [MATLAB_SUB_MODULES] 里的子模块列表（当前只有「解微分方程」），
 * 点击某项 → `onSubModulePick(subId)` → 调用方更新
 * `MatlabSetModuleState.activeSubModule` 并切换到 MATLAB 集模式。
 *
 * 本文件独立维护；新增子模块只需在 [MATLAB_SUB_MODULES] 追加一项，
 * 无需改动此渲染逻辑。
 */
@Composable
fun MatlabSetSubMenu(
    activeSubModule: String,
    onSubModulePick: (String) -> Unit = {},
) {
    val dark = isDarkTheme()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 4.dp, top = 2.dp, bottom = 4.dp),
    ) {
        MATLAB_SUB_MODULES.forEach { sub ->
            val selected = sub.id == activeSubModule
            GlassItemButton(
                modifier = Modifier.fillMaxWidth(),
                selected = selected,
                onClick = { onSubModulePick(sub.id) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 子项圆点标记（选中时用 primary 色，否则弱化）
                    Box(
                        modifier = Modifier.size(6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "·",
                            color = if (selected) LocalContentColor.current
                                   else if (dark) Text200.copy(alpha = 0.50f)
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = sub.label,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
