package com.example.personalassistant.feature.finance.presentation.add

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Phase 2：附件选择区
 *
 * - 用户点击"添加图片"→ 调起 PhotoPicker（无需运行时权限）
 * - 多选最多 [MAX_ITEMS] 张，每张可单独删除
 * - UI 仅展示层；压缩 / 落盘 / 写入 attachmentUri 由 ViewModel 负责
 *
 * @param uris 当前已选的附件 URI 列表（已写入文件的 file:// Uri，可直接渲染）
 * @param onPicked Picker 返回的原始 Uri 列表（增量追加，由 ViewModel 负责压缩）
 * @param onRemove 按索引删除附件
 */
@Composable
fun AttachmentSection(
    uris: List<android.net.Uri>,
    onPicked: (List<android.net.Uri>) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_ITEMS),
    ) { picked ->
        if (picked.isNotEmpty()) onPicked(picked)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "附件",
            style = MaterialTheme.typography.titleMedium,
        )

        // "添加图片"按钮（已选满则隐藏）
        if (uris.size < MAX_ITEMS) {
            OutlinedButton(
                onClick = {
                    pickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("添加图片")
            }
        }
    }

    if (uris.isNotEmpty()) {
        Spacer(modifier = Modifier.size(4.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 8.dp),
        ) {
            items(uris.size) { index ->
                AttachmentThumbnail(
                    uri = uris[index],
                    onRemove = { onRemove(index) },
                )
            }
        }
    }
}

@Composable
private fun AttachmentThumbnail(
    uri: android.net.Uri,
    onRemove: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "附件图片",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        // 删除按钮（右上角小圆 X）
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "删除附件",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private const val MAX_ITEMS = 3

// LazyRow.items(count: Int) 扩展来自 androidx.compose.foundation.lazy.LazyListScope