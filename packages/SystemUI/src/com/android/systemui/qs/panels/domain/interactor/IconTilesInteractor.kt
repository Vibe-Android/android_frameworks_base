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

package com.android.systemui.qs.panels.domain.interactor

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import com.android.internal.logging.UiEventLogger
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.LogLevel
import com.android.systemui.qs.QSEditEvent
import com.android.systemui.qs.panels.data.repository.DefaultLargeTilesRepository
import com.android.systemui.qs.panels.shared.model.PanelsLog
import com.android.systemui.qs.pipeline.domain.interactor.CurrentTilesInteractor
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.pipeline.shared.metricSpec
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import com.android.systemui.vibe.VibeSettingsConstants
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** Interactor for retrieving the list of [TileSpec] to be displayed as icons and resizing icons. */
@SysUISingleton
class IconTilesInteractor
@Inject
constructor(
    @Application private val applicationContext: Context,
    private val repo: DefaultLargeTilesRepository,
    private val currentTilesInteractor: CurrentTilesInteractor,
    private val preferencesInteractor: QSPreferencesInteractor,
    private val uiEventLogger: UiEventLogger,
    @PanelsLog private val logBuffer: LogBuffer,
    @Background private val scope: CoroutineScope,
) {
    private val vibeForceCompactSetting: Flow<Boolean> = conflatedCallbackFlow {
        val uri = Settings.System.getUriFor(VibeSettingsConstants.KEY_QS_FORCE_COMPACT_TILES)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val value = Settings.System.getIntForUser(
                    applicationContext.contentResolver,
                    VibeSettingsConstants.KEY_QS_FORCE_COMPACT_TILES,
                    VibeSettingsConstants.DEFAULT_QS_FORCE_COMPACT_TILES,
                    UserHandle.USER_CURRENT
                ) == 1
                trySend(value)
            }
        }
        applicationContext.contentResolver.registerContentObserver(uri, false, observer, UserHandle.USER_ALL)
        val initial = Settings.System.getIntForUser(
            applicationContext.contentResolver,
            VibeSettingsConstants.KEY_QS_FORCE_COMPACT_TILES,
            VibeSettingsConstants.DEFAULT_QS_FORCE_COMPACT_TILES,
            UserHandle.USER_CURRENT
        ) == 1
        trySend(initial)
        awaitClose {
            applicationContext.contentResolver.unregisterContentObserver(observer)
        }
    }.distinctUntilChanged()

    val largeTilesSpecs: StateFlow<Set<TileSpec>> =
        combine(
            preferencesInteractor.largeTilesSpecs,
            vibeForceCompactSetting,
        ) { specs, forceCompact ->
            if (forceCompact) emptySet() else specs
        }
            .onEach { logChange(it) }
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                if (Settings.System.getIntForUser(
                        applicationContext.contentResolver,
                        VibeSettingsConstants.KEY_QS_FORCE_COMPACT_TILES,
                        VibeSettingsConstants.DEFAULT_QS_FORCE_COMPACT_TILES,
                        UserHandle.USER_CURRENT
                    ) == 1
                ) emptySet() else repo.defaultLargeTiles
            )

    fun isIconTile(spec: TileSpec): Boolean = !largeTilesSpecs.value.contains(spec)

    /** Set the large tiles to be [specs] */
    fun setLargeTiles(specs: Set<TileSpec>) {
        preferencesInteractor.setLargeTilesSpecs(specs)
    }

    /** Remove [specs] from the current set of large tiles */
    fun removeLargeTiles(specs: Set<TileSpec>) {
        preferencesInteractor.removeLargeTilesSpecs(specs)
    }

    fun resetToDefault() {
        preferencesInteractor.setLargeTilesSpecs(repo.defaultLargeTiles)
    }

    fun resize(spec: TileSpec, toIcon: Boolean) {
        if (!isCurrent(spec)) {
            return
        }

        val isIcon = !largeTilesSpecs.value.contains(spec)
        if (toIcon && !isIcon) {
            preferencesInteractor.setLargeTilesSpecs(largeTilesSpecs.value - spec)
            uiEventLogger.log(
                /* event= */ QSEditEvent.QS_EDIT_RESIZE_SMALL,
                /* uid= */ 0,
                /* packageName= */ spec.metricSpec,
            )
        } else if (!toIcon && isIcon) {
            preferencesInteractor.setLargeTilesSpecs(largeTilesSpecs.value + spec)
            uiEventLogger.log(
                /* event= */ QSEditEvent.QS_EDIT_RESIZE_LARGE,
                /* uid= */ 0,
                /* packageName= */ spec.metricSpec,
            )
        }
    }

    private fun isCurrent(spec: TileSpec): Boolean {
        return currentTilesInteractor.currentTilesSpecs.contains(spec)
    }

    private fun logChange(specs: Set<TileSpec>) {
        logBuffer.log(
            LOG_BUFFER_LARGE_TILES_SPECS_CHANGE_TAG,
            LogLevel.DEBUG,
            { str1 = specs.toString() },
            { "Large tiles change: $str1" },
        )
    }

    private companion object {
        const val LOG_BUFFER_LARGE_TILES_SPECS_CHANGE_TAG = "LargeTilesSpecsChange"
    }
}
