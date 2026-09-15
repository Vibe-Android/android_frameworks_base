/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.panels.ui.compose

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.android.compose.animation.scene.ContentScope
import com.android.systemui.qs.panels.ui.viewmodel.TileGridViewModel
import com.android.systemui.vibe.VibeSettingsConstants

val LocalShowTileLabels = compositionLocalOf { true }

/**
 * Displays a grid of tiles with an optional reveal animation.
 *
 * @param enableRevealEffect If `true`, the tiles will animate using the reveal animation.
 */
@Composable
fun ContentScope.TileGrid(
    viewModel: TileGridViewModel,
    modifier: Modifier = Modifier,
    listening: () -> Boolean = { true },
    enableRevealEffect: Boolean = false,
) {
    val context = LocalContext.current
    var showLabels by remember {
        mutableStateOf(
            Settings.System.getIntForUser(
                context.contentResolver,
                VibeSettingsConstants.KEY_QS_SHOW_LABELS,
                VibeSettingsConstants.DEFAULT_QS_SHOW_LABELS,
                UserHandle.USER_CURRENT
            ) == 1
        )
    }

    DisposableEffect(context) {
        val uri = Settings.System.getUriFor(VibeSettingsConstants.KEY_QS_SHOW_LABELS)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                showLabels = Settings.System.getIntForUser(
                    context.contentResolver,
                    VibeSettingsConstants.KEY_QS_SHOW_LABELS,
                    VibeSettingsConstants.DEFAULT_QS_SHOW_LABELS,
                    UserHandle.USER_CURRENT
                ) == 1
            }
        }
        context.contentResolver.registerContentObserver(uri, false, observer, UserHandle.USER_ALL)
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    CompositionLocalProvider(LocalShowTileLabels provides showLabels) {
        val gridLayout = viewModel.gridLayout
        val tiles = viewModel.tileViewModels
        with(gridLayout) {
            TileGrid(
                tiles = tiles,
                modifier = modifier,
                listening = listening,
                enableRevealEffect = enableRevealEffect,
            )
        }
    }
}
