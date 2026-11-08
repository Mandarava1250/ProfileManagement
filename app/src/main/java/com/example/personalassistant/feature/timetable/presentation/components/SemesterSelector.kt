package com.example.personalassistant.feature.timetable.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.timetable.domain.model.Semester

/**
 * 学期选择器：点击展开 DropdownMenu，选择后回调
 *
 * - 菜单底部有一条「+ 新建学期」入口，触发 onRequestCreate 让上层弹学期创建 Dialog。
 * - onRequestCreate 不为空时菜单才会显示该项（让"无学期 + 顶部 selector 直接 +"成为冗余路径）。
 */
@Composable
fun SemesterSelector(
    current: Semester?,
    semesters: List<Semester>,
    onSelect: (String) -> Unit,
    onRequestCreate: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = current?.name ?: "未选学期",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        SemesterDropdownMenu(
            expanded = expanded,
            semesters = semesters,
            current = current,
            onSelect = onSelect,
            onRequestCreate = onRequestCreate,
            onDismissRequest = { expanded = false },
        )
    }
}

/**
 * 学期下拉菜单 —— 独立出来给别的触发器复用。
 *
 * 课表页的副标题里那个「⌄」要自己控制展开状态、还要能点开菜单，
 * 用 [SemesterSelector] 那个固定样式的胶囊触发器不合适 —— 抽成菜单本体，
 * 触发器由调用方自己决定长什么样。
 *
 * @param expanded 菜单是否展开
 * @param semesters 全部学期
 * @param current 当前学期（会被标成「· 当前」并染成品牌色）
 * @param onSelect 选中某个学期（参数是学期名）
 * @param onRequestCreate 点了「新建学期」；null = 不显示这一项
 * @param onDismissRequest 菜单收起
 */
@Composable
fun SemesterDropdownMenu(
    expanded: Boolean,
    semesters: List<Semester>,
    current: Semester?,
    onSelect: (String) -> Unit,
    onRequestCreate: (() -> Unit)? = null,
    onDismissRequest: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        semesters.forEach { sem ->
            DropdownMenuItem(
                text = {
                    Text(
                        sem.name + if (sem.isCurrent) " · 当前" else "",
                        color = if (sem.isCurrent)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                },
                onClick = {
                    onDismissRequest()
                    onSelect(sem.name)
                },
            )
        }
        if (onRequestCreate != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "新建学期",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                onClick = {
                    onDismissRequest()
                    onRequestCreate()
                },
            )
        }
    }
}