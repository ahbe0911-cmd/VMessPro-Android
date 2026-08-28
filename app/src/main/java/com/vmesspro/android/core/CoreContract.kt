package com.vmesspro.android.core

internal object CoreContract {
    const val ACTION_CONNECT = "com.vmesspro.android.core.CONNECT"
    const val ACTION_DISCONNECT = "com.vmesspro.android.core.DISCONNECT"
    const val ACTION_STATE = "com.vmesspro.android.core.STATE"
    const val ACTION_TELEMETRY = "com.vmesspro.android.core.TELEMETRY"

    const val EXTRA_PROFILE_ID = "profile_id"
    const val EXTRA_SPLIT_MODE = "split_mode"
    const val EXTRA_INCLUDED = "included_packages"
    const val EXTRA_EXCLUDED = "excluded_packages"
    const val EXTRA_BANKING = "banking_packages"
    const val EXTRA_CUSTOM_DNS = "custom_dns"
    const val EXTRA_STATE = "state"
    const val EXTRA_REASON = "reason"
    const val EXTRA_SINCE = "since"

    const val EXTRA_UPLINK = "uplink"
    const val EXTRA_DOWNLINK = "downlink"
    const val EXTRA_UPLINK_TOTAL = "uplink_total"
    const val EXTRA_DOWNLINK_TOTAL = "downlink_total"
    const val EXTRA_CONNECTIONS_IN = "connections_in"
    const val EXTRA_CONNECTIONS_OUT = "connections_out"
    const val EXTRA_TRAFFIC_AVAILABLE = "traffic_available"

    const val STATE_DISCONNECTED = "DISCONNECTED"
    const val STATE_PREPARING = "PREPARING"
    const val STATE_CONNECTING = "CONNECTING"
    const val STATE_VERIFYING = "VERIFYING"
    const val STATE_CONNECTED = "CONNECTED"
    const val STATE_RECONNECTING = "RECONNECTING"
    const val STATE_ERROR = "ERROR"
}
