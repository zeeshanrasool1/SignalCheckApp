package com.signalcheck.app

import android.app.Activity
import android.content.Context
import android.widget.FrameLayout
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

object AdsHelper {
    fun initialize(context: Context) {
        MobileAds.initialize(context) {}
    }

    fun loadBannerAd(activity: Activity, container: FrameLayout) {
        val adView = AdView(activity)
        adView.adUnitId = "ca-app-pub-3940256099942544/6300978111"
        adView.setAdSize(AdSize.BANNER)
        container.removeAllViews()
        container.addView(adView)
        adView.loadAd(AdRequest.Builder().build())
    }
}
