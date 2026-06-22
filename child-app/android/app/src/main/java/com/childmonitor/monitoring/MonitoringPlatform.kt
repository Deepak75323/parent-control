package com.childmonitor.monitoring

import com.childmonitor.platform.AndroidPlatform

/** Android 15+ requires short dataSync FGS bursts instead of a 24/7 foreground service. */
object MonitoringPlatform {

    fun useBurstFgs(): Boolean = AndroidPlatform.useBurstFgs()

    fun requiresFgsTimeoutHandler(): Boolean = AndroidPlatform.requiresFgsTimeoutHandler()
}
