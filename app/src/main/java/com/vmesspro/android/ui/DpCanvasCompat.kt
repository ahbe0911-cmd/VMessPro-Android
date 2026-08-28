package com.vmesspro.android.ui

import android.content.res.Resources
import androidx.compose.ui.unit.Dp

/**
 * Pixel projections used by the A54 power-button Canvas.
 *
 * A54PowerButton has a Dp parameter named `size`, which intentionally defines
 * the responsive button diameter. Inside its Canvas that name shadows
 * DrawScope.size. These projections keep the existing UI structure intact
 * while converting the Dp diameter to real device pixels for drawing.
 */
private val systemDensity: Float
    get() = Resources.getSystem().displayMetrics.density

internal val Dp.minDimension: Float
    get() = value * systemDensity

internal val Dp.width: Float
    get() = value * systemDensity

internal val Dp.height: Float
    get() = value * systemDensity
