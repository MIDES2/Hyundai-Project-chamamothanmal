package io.chamamothanmal.vehiclestate.source

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import io.chamamothanmal.vehiclestate.LocationReading
import io.chamamothanmal.vehiclestate.SignalSource

/**
 * Android 표준 GPS 위치를 1초 간격으로 받는다(기존 Pleos 검증 앱의 startAndroidLocation을 옮김).
 *
 * NaviHelper.getCurrentLocationInfo()는 Pleos 에뮬레이터에서 기본 내비가 NMEA를 체크섬 불일치로 거부해
 * 위치가 갱신되지 않으므로 위치 판정에 쓰지 않는다. 콜백은 메인 스레드에서 호출된다.
 */
internal class AndroidGpsLocationSource(
    context: Context,
    private val onReading: (LocationReading) -> Unit,
    private val onUnavailable: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private var locationManager: LocationManager? = null
    private var started = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onReading(location.toReading())
        }

        override fun onProviderDisabled(provider: String) {
            onUnavailable("GPS 위치 제공자가 꺼짐")
        }

        override fun onProviderEnabled(provider: String) = Unit

        // API 28~29 기기에서는 기본 구현이 없어 반드시 재정의해야 한다.
        @Deprecated("Deprecated in Android API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    fun hasPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** @return 등록 성공 여부. 실패 사유는 onUnavailable로 전달된다. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (started) return true
        if (!hasPermission()) {
            onUnavailable("위치 권한 없음 (ACCESS_FINE_LOCATION)")
            return false
        }
        return try {
            val lm = appContext.getSystemService(LocationManager::class.java)
                ?: run { onUnavailable("LocationManager 없음"); return false }
            locationManager = lm

            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                onUnavailable("GPS 위치 제공자가 꺼짐")
                return false
            }

            // 마지막 위치는 실제 측정 시각을 그대로 넘긴다. 오래됐으면 PlaybackGate가 LOCATION_STALE로 보류한다.
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { onReading(it.toReading()) }

            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                UPDATE_INTERVAL_MS,
                0f,
                listener,
                Looper.getMainLooper(),
            )
            started = true
            true
        } catch (e: Exception) {
            onUnavailable("위치 등록 실패: ${e.javaClass.simpleName}")
            false
        }
    }

    fun stop() {
        locationManager?.let { lm -> runCatching { lm.removeUpdates(listener) } }
        locationManager = null
        started = false
    }

    private fun Location.toReading() = LocationReading(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        source = if (isMockLocation()) SignalSource.MOCK else SignalSource.SDK,
        elapsedRealtimeMs = elapsedRealtimeNanos / 1_000_000,
    )

    @Suppress("DEPRECATION")
    private fun Location.isMockLocation(): Boolean =
        if (Build.VERSION.SDK_INT >= 31) isMock else isFromMockProvider

    companion object {
        const val UPDATE_INTERVAL_MS = 1_000L
    }
}
