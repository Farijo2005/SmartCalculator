package com.example.smartcalculator.ui.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartcalculator.ui.components.isDarkTheme
import com.example.smartcalculator.ui.theme.Text200

/**
 * MATLAB 集二级子菜单。
 *
 * 由兄弟（用户B）负责实现，**本文件独立维护**。
 *
 * 设计原则：
 * 1. **互不干扰**：本文件是 MATLAB 集子菜单的唯一修改点，
 *    其他模块文件（含共享文件）不会被修改。
 * 2. **状态隔离**：子菜单的选中状态由调用方管理，本组件只负责渲染。
 * 3. **历史共享**：所有子模块通过 [ModuleIntent] 写入共享历史记录。
 * 4. **多人协同**：每个子模块建议单独成文件（如 `MatlabSet/NumericalModule.kt`），
 *    本文件只做"子菜单路由"。
 *
 * 当前的占位版本只展示"子模块逐步接入中"提示；
 * 兄弟完成时把下面的占位内容替换为子菜单项列表（每项点击 →
 * `onSubModulePick(subId)` → 调用方更新 `MatlabSetModuleState.activeSubModule`）。
 */
@Composable
fun MatlabSetSubMenu(
    onSubModulePick: (String) -> Unit = {},
) {
    val dark = isDarkTheme()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 26.dp, end = 4.dp, top = 2.dp, bottom = 4.dp),
    ) {
        // —— 占位提示（兄弟完成时删除此处并替换为子菜单项） ——
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "·",
                    color = if (dark) Text200.copy(alpha = 0.50f)
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                    fontSize = 12.sp,
                )
            }
            Text(
                text = "子模块逐步接入中",
                style = MaterialTheme.typography.labelSmall,
                color = if (dark) Text200.copy(alpha = 0.55f)
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f),
            )
        }
    }
}
