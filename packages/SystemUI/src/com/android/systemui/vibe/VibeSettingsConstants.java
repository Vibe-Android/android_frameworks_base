/*
 * Copyright (C) 2026 The Vibe Project
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

package com.android.systemui.vibe;

public final class VibeSettingsConstants {

    private VibeSettingsConstants() {}

    // Quick Settings
    public static final String KEY_QS_COLUMNS = "vibe_qs_columns";
    public static final int DEFAULT_QS_COLUMNS = 0;

    public static final String KEY_QS_SHOW_LABELS = "vibe_qs_show_labels";
    public static final int DEFAULT_QS_SHOW_LABELS = 1;

    public static final String KEY_QS_FORCE_COMPACT_TILES = "vibe_qs_force_compact_tiles";
    public static final int DEFAULT_QS_FORCE_COMPACT_TILES = 0;

    public static final String KEY_QS_SHOW_BRIGHTNESS_SLIDER = "vibe_qs_show_brightness_slider";
    public static final int DEFAULT_QS_SHOW_BRIGHTNESS_SLIDER = 1;

    // Status bar clock
    public static final String KEY_STATUSBAR_CLOCK_SECONDS = "vibe_statusbar_clock_seconds";
    public static final int DEFAULT_STATUSBAR_CLOCK_SECONDS = 0;

    public static final String KEY_STATUSBAR_CLOCK_DATE = "vibe_statusbar_clock_date";
    public static final int DEFAULT_STATUSBAR_CLOCK_DATE = 0;

    public static final String KEY_STATUSBAR_CLOCK_DATE_FORMAT = "vibe_statusbar_clock_date_format";
    public static final int DEFAULT_STATUSBAR_CLOCK_DATE_FORMAT = 0;


    // Status bar network traffic
    public static final String KEY_STATUSBAR_NETWORK_TRAFFIC = "vibe_statusbar_network_traffic";
    public static final int DEFAULT_STATUSBAR_NETWORK_TRAFFIC = 0;

    public static final String KEY_STATUSBAR_NETWORK_TRAFFIC_MODE = "vibe_statusbar_network_traffic_mode";
    public static final int DEFAULT_STATUSBAR_NETWORK_TRAFFIC_MODE = 0;

    public static final int NETWORK_TRAFFIC_MODE_COMBINED = 0;
    public static final int NETWORK_TRAFFIC_MODE_DOWNLOAD = 1;
    public static final int NETWORK_TRAFFIC_MODE_UPLOAD = 2;
}
