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

package com.android.systemui.qs.panels.data.repository

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import com.android.systemui.common.ui.data.repository.ConfigurationRepository
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.util.kotlin.emitOnStart
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import com.android.systemui.vibe.VibeSettingsConstants
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@SysUISingleton
class QSColumnsRepository
@Inject
constructor(
    @Application private val applicationContext: Context,
    @ShadeDisplayAware private val resources: Resources,
    @ShadeDisplayAware configurationRepository: ConfigurationRepository,
) {
    private val vibeColumnsSetting: Flow<Int> = conflatedCallbackFlow {
        val uri = Settings.System.getUriFor(VibeSettingsConstants.KEY_QS_COLUMNS)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val value = Settings.System.getIntForUser(
                    applicationContext.contentResolver,
                    VibeSettingsConstants.KEY_QS_COLUMNS,
                    VibeSettingsConstants.DEFAULT_QS_COLUMNS,
                    UserHandle.USER_CURRENT
                )
                trySend(value)
            }
        }
        applicationContext.contentResolver.registerContentObserver(
            uri,
            false,
            observer,
            UserHandle.USER_ALL
        )
        val initial = Settings.System.getIntForUser(
            applicationContext.contentResolver,
            VibeSettingsConstants.KEY_QS_COLUMNS,
            VibeSettingsConstants.DEFAULT_QS_COLUMNS,
            UserHandle.USER_CURRENT
        )
        trySend(initial)
        awaitClose {
            applicationContext.contentResolver.unregisterContentObserver(observer)
        }
    }.distinctUntilChanged()

    val isCustomColumns: Flow<Boolean> =
        vibeColumnsSetting.map { it in 3..6 }.distinctUntilChanged()

    val splitShadeColumns: Flow<Int> =
        combine(
            configurationRepository.onConfigurationChange.emitOnStart(),
            vibeColumnsSetting
        ) { _, vibeCols ->
            val defaultCols = resources.getInteger(R.integer.quick_settings_split_shade_num_columns)
            if (vibeCols in 3..6) vibeCols * 2 else defaultCols
        }

    val dualShadeColumns: Flow<Int> =
        combine(
            configurationRepository.onConfigurationChange.emitOnStart(),
            vibeColumnsSetting
        ) { _, vibeCols ->
            val defaultCols = resources.getInteger(R.integer.quick_settings_dual_shade_num_columns)
            if (vibeCols in 3..6) vibeCols * 2 else defaultCols
        }

    val columns: Flow<Int> =
        combine(
            configurationRepository.onConfigurationChange.emitOnStart(),
            vibeColumnsSetting
        ) { _, vibeCols ->
            val defaultCols = resources.getInteger(R.integer.quick_settings_infinite_grid_num_columns)
            if (vibeCols in 3..6) {
                val effectiveCols = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    maxOf(defaultCols, vibeCols)
                } else {
                    vibeCols
                }
                effectiveCols * 2
            } else {
                defaultCols
            }
        }

    val defaultColumns: Int
        get() {
            val defaultCols = resources.getInteger(R.integer.quick_settings_infinite_grid_num_columns)
            val vibeCols = Settings.System.getIntForUser(
                applicationContext.contentResolver,
                VibeSettingsConstants.KEY_QS_COLUMNS,
                VibeSettingsConstants.DEFAULT_QS_COLUMNS,
                UserHandle.USER_CURRENT
            )
            return if (vibeCols in 3..6) {
                val effectiveCols = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    maxOf(defaultCols, vibeCols)
                } else {
                    vibeCols
                }
                effectiveCols * 2
            } else {
                defaultCols
            }
        }
}
