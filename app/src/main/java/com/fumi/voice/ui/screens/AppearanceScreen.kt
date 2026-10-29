package com.fumi.voice.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fumi.voice.R
import com.fumi.voice.ui.components.pageEntrance
import com.fumi.voice.ui.theme.Appearance
import com.fumi.voice.ui.theme.AppearanceHub
import com.fumi.voice.ui.theme.Charcoal
import com.fumi.voice.ui.theme.Divider
import com.fumi.voice.ui.theme.Indigo
import com.fumi.voice.ui.theme.LocalAppearance
import com.fumi.voice.ui.theme.Radius
import com.fumi.voice.ui.theme.SurfaceMaterial
import com.fumi.voice.ui.theme.TextPrimary
import com.fumi.voice.ui.theme.TextSecondary
import com.fumi.voice.ui.theme.TimecodeSmall
import com.fumi.voice.ui.theme.WallpaperKind
import com.fumi.voice.ui.theme.appSurface
import com.fumi.voice.ui.wallpaper.WallpaperArt
import kotlin.math.roundToInt

/**
 * 外观页（材质 / 壁纸 / 背景）。
 *
 * 对齐电脑端的 AppearancePanel：**改完立刻预览、立刻生效、自动保存**，
 * 没有"确定 / 取消"这一步 —— 外观是主观的，用户需要边看边调。
 *
 * 三种预览各自回答一个问题：
 *  - 顶部大图：这套材质 + 壁纸在我常用的页面里长什么样；
 *  - 材质格：四种材质并排、背景是当前壁纸，差异一眼可比；
 *  - 壁纸格：六张内置背景各自缩略，选"无"就回到 1.0.10 的纯色底。
 */
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appearance = LocalAppearance.current

    /** 改一项就落盘并广播，外壳与所有 appSurface() 同时跟着变。 */
    fun apply(next: Appearance) = AppearanceHub.update(context, next)

    Column(modifier = modifier.fillMaxSize().pageEntrance()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(start = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = TextPrimary,
                )
            }
            Text(
                stringResource(R.string.appearance_title),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            PreviewCard(appearance)

            AppearanceSectionTitle(stringResource(R.string.appearance_section_material))
            MaterialPicker(
                appearance = appearance,
                onPick = { material -> apply(appearance.copy(material = material)) },
            )

            AppearanceSectionTitle(stringResource(R.string.appearance_section_wallpaper))
            WallpaperPicker(
                appearance = appearance,
                onPick = { kind -> apply(appearance.copy(wallpaper = kind)) },
            )

            AppearanceSectionTitle(stringResource(R.string.appearance_section_backdrop))
            BackdropControls(
                appearance = appearance,
                onDim = { value -> apply(appearance.copy(dim = value)) },
                onMotion = { on -> apply(appearance.copy(animateWallpaper = on)) },
            )
        }
    }
}

/** 顶部预览：壁纸 + 舞台 + 一张该材质的示例卡。 */
@Composable
private fun PreviewCard(appearance: Appearance) {
    val shape = RoundedCornerShape(Radius.Lg)
    Box(Modifier.fillMaxWidth().height(186.dp).clip(shape)) {
        WallpaperArt(appearance.wallpaper, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Charcoal.copy(alpha = appearance.material.stageAlpha)))
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = appearance.dim.coerceIn(0f, 1f) * 0.6f)),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
                .fillMaxWidth(0.84f)
                .appSurface(appearance.material)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                stringResource(R.string.appearance_preview_card_title),
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                stringResource(R.string.appearance_preview_card_sub),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.appearance_saved_hint),
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
    )
}

@Composable
private fun MaterialPicker(appearance: Appearance, onPick: (SurfaceMaterial) -> Unit) {
    // 材质格必须有壁纸做背景，否则四种材质压在同一个实色舞台上几乎看不出差别
    val backdrop = if (appearance.wallpaper == WallpaperKind.NONE) WallpaperKind.AURORA else appearance.wallpaper
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SurfaceMaterial.entries.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { material ->
                    MaterialTile(
                        modifier = Modifier.weight(1f),
                        material = material,
                        backdrop = backdrop,
                        selected = appearance.material == material,
                        onClick = { onPick(material) },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MaterialTile(
    modifier: Modifier,
    material: SurfaceMaterial,
    backdrop: WallpaperKind,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val outer = RoundedCornerShape(Radius.Md)
    Box(
        modifier = modifier
            .height(94.dp)
            .clip(outer)
            .clickable(onClick = onClick),
    ) {
        WallpaperArt(backdrop, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Charcoal.copy(alpha = material.stageAlpha)))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                .appSurface(material, RoundedCornerShape(Radius.Sm))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(material.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Indigo,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(material.descRes),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 2,
            )
        }
        if (selected) {
            Box(Modifier.fillMaxSize().border(1.5.dp, Indigo, outer))
        }
    }
}

@Composable
private fun WallpaperPicker(appearance: Appearance, onPick: (WallpaperKind) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WallpaperKind.entries.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { kind ->
                    WallpaperTile(
                        modifier = Modifier.weight(1f),
                        kind = kind,
                        selected = appearance.wallpaper == kind,
                        onClick = { onPick(kind) },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WallpaperTile(
    modifier: Modifier,
    kind: WallpaperKind,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.Md)
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(78.dp)
                .clip(shape)
                .border(1.dp, if (selected) Indigo else Divider, shape),
        ) {
            WallpaperArt(kind, Modifier.fillMaxSize())
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Indigo),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(kind.labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Indigo else TextSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun BackdropControls(
    appearance: Appearance,
    onDim: (Float) -> Unit,
    onMotion: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .appSurface()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.appearance_dim),
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.appearance_percent, (appearance.dim * 100).roundToInt()),
                style = TimecodeSmall,
                color = TextPrimary,
            )
        }
        Slider(
            value = appearance.dim.coerceIn(0f, 0.9f),
            onValueChange = onDim,
            valueRange = 0f..0.9f,
            colors = SliderDefaults.colors(
                thumbColor = Indigo,
                activeTrackColor = Indigo,
                inactiveTrackColor = Divider,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.appearance_motion),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                )
                Text(
                    stringResource(R.string.appearance_motion_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Switch(
                checked = appearance.animateWallpaper,
                onCheckedChange = onMotion,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Indigo,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = Charcoal,
                    uncheckedBorderColor = TextSecondary,
                ),
            )
        }
    }
}

@Composable
private fun AppearanceSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = Indigo,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}
