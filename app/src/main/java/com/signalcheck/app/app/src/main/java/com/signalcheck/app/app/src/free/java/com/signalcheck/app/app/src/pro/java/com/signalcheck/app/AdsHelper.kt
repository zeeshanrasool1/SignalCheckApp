package com.signalcheck.app

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout

object AdsHelper {
    fun initialize(context: Context) {
        // No ads in Pro version
    }

    fun loadBannerAd(activity: Activity, container: FrameLayout) {
        container.visibility = View.GONE
    }
}
