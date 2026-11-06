package com.example.malttuk

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import com.example.malttuk.ui.theme.MalttukTheme

class MainActivity : ComponentActivity() {

    private var vehicle: Vehicle? = null
    private var naviHelper: NaviHelper? = null

    private val handler = Handler(Looper.getMainLooper())

    private var gearReady by mutableStateOf(false)
    private var gearText by mutableStateOf("아직 조회하지 않았습니다.")

    // ── Android 표준 위치 (내비를 거치지 않음) ──
    private var locationManager: LocationManager? = null
    private var androidLocStatus by mutableStateOf("위치 권한 확인 중")
    private var androidLocText by mutableStateOf("아직 위치를 받지 않았습니다.")
    private var androidLocCount by mutableStateOf(0)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.KOREA)

    private val androidLocListener = LocationListener { loc: Location ->
        if (isDestroyed) return@LocationListener
        androidLocCount++
        androidLocText =
            "자동 갱신 수신: ${androidLocCount}회 (${loc.provider})\n" +
                    "위도: ${loc.latitude}\n" +
                    "경도: ${loc.longitude}\n" +
                    "받은 시각: ${timeFormat.format(Date())}"
    }

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                startAndroidLocation()
            } else {
                androidLocStatus = "위치 권한이 거부되었습니다."
            }
        }
    private var naviReady by mutableStateOf(false)
    private var naviStatus by mutableStateOf("내비 서비스 연결 중")
    private var locationText by mutableStateOf("아직 위치를 받지 않았습니다.")
    private var locationPending by mutableStateOf(false)
    private var locationCount by mutableStateOf(0)

    private val locationTimeout = Runnable {
        if (locationPending && !isDestroyed) {
            locationPending = false
            locationText =
                "10초 동안 위치 응답이 없습니다.\n" +
                        "이것만으로 위치 기능 미지원이라고 확정할 수는 없습니다."
        }
    }

    private val naviListener = object : NaviHelperEventListener {

        override fun onCurrentLocationInfo(
            currentLocationInfo: CurrentLocationInfo
        ) {
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread

                handler.removeCallbacks(locationTimeout)
                locationPending = false
                locationCount++

                locationText =
                    "위치 응답 수신: ${locationCount}회\n" +
                            "위도: ${currentLocationInfo.latitude}\n" +
                            "경도: ${currentLocationInfo.longitude}\n" +
                            "방향: ${currentLocationInfo.heading}\n" +
                            "주소: ${currentLocationInfo.address}"
            }
        }

        override fun onError(errorCode: NaviErrorCode) {
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread

                handler.removeCallbacks(locationTimeout)
                locationPending = false
                locationText = "내비 SDK 오류: $errorCode"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gearText = "아직 조회하지 않았습니다."

        setContent {
            MalttukTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(40.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "말뚝 · 기어 / 위치 연동 확인",
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Text(
                            text = "기어 조회",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Text(text = gearText)

                        Button(
                            onClick = { readGear() },
                            enabled = gearReady
                        ) {
                            Text("현재 기어 조회")
                        }

                        Text(
                            text = "Android 위치 (1초마다 자동 갱신)",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Text(text = androidLocStatus)
                        Text(text = androidLocText)

                        Text(
                            text = "NaviHelper 위치 조회",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Text(text = naviStatus)
                        Text(text = locationText)

                        Button(
                            onClick = { readLocation() },
                            enabled = naviReady && !locationPending
                        ) {
                            Text(
                                if (locationPending) "위치 응답 대기 중..."
                                else "현재 위치 조회"
                            )
                        }

                        Text(
                            text = "두 기능 모두 수동 조회 테스트입니다.\n" +
                                    "위치는 내비 서비스가 보내준 값이며, " +
                                    "실시간 갱신 여부는 별도로 검증해야 합니다."
                        )
                    }
                }
            }
        }

        initializeVehicle()
        initializeNavigation()
        initializeAndroidLocation()
    }

    private fun initializeAndroidLocation() {
        val granted = checkSelfPermission(
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            startAndroidLocation()
        } else {
            androidLocStatus = "위치 권한을 요청했습니다. 화면에서 허용을 눌러 주세요."
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAndroidLocation() {
        try {
            val lm = getSystemService(LocationManager::class.java)
            locationManager = lm

            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                androidLocStatus = "GPS 위치 제공자가 꺼져 있습니다."
                return
            }

            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { last ->
                androidLocText =
                    "마지막으로 알려진 위치 (아직 새 갱신 아님)\n" +
                            "위도: ${last.latitude}\n" +
                            "경도: ${last.longitude}"
            }

            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1_000L,
                0f,
                androidLocListener,
                Looper.getMainLooper()
            )
            androidLocStatus = "GPS 자동 갱신 등록 성공"
        } catch (e: Exception) {
            androidLocStatus =
                "Android 위치 등록 실패: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    private fun initializeVehicle() {
        try {
            val sdk = Vehicle(applicationContext)
            vehicle = sdk
            sdk.initialize()
            gearReady = true
        } catch (e: Exception) {
            gearText = "차량 SDK 초기화 실패: ${e.message}"
        }
    }

    private fun initializeNavigation() {
        try {
            val sdk = NaviHelper(applicationContext)
            naviHelper = sdk

            sdk.initialize(
                onConnected = {
                    runOnUiThread {
                        if (isDestroyed) return@runOnUiThread

                        sdk.addListener(naviListener)
                        naviReady = true
                        naviStatus = "내비 서비스 연결 성공"
                    }
                },
                onFailed = { error ->
                    runOnUiThread {
                        if (isDestroyed) return@runOnUiThread

                        naviReady = false
                        naviStatus =
                            "내비 연결 실패: " +
                                    "${error.javaClass.simpleName}: ${error.message}"
                    }
                }
            )
        } catch (e: Exception) {
            naviReady = false
            naviStatus = "내비 SDK 초기화 실패: ${e.message}"
        }
    }

    @Suppress("DEPRECATION")
    private fun readGear() {
        gearText = "조회 중..."

        try {
            vehicle?.getGear()?.getCurrentGearSelectionState(
                onComplete = { gear ->
                    runOnUiThread {
                        if (isDestroyed) return@runOnUiThread

                        val result = when (gear) {
                            VehicleGear.GEAR_PARK -> "P · P단 맞음"
                            VehicleGear.GEAR_DRIVE -> "D · P단 아님"
                            VehicleGear.GEAR_REVERSE -> "R · P단 아님"
                            VehicleGear.GEAR_NEUTRAL -> "N · P단 아님"
                            null,
                            VehicleGear.GEAR_UNKNOWN -> "확인 불가"
                            else -> "${gear.name} · P단 아님"
                        }

                        gearText =
                            "$result\nSDK 응답: ${gear?.name ?: "null"}"
                    }
                },
                onFailed = { error ->
                    runOnUiThread {
                        if (!isDestroyed) {
                            gearText = "기어 조회 실패: ${error.message}"
                        }
                    }
                }
            )
        } catch (e: Exception) {
            gearText = "기어 호출 실패: ${e.message}"
        }
    }

    private fun readLocation() {
        val sdk = naviHelper ?: return

        handler.removeCallbacks(locationTimeout)
        locationPending = true
        locationText = "내비 서비스에 현재 위치를 요청했습니다."
        handler.postDelayed(locationTimeout, 10_000)

        try {
            sdk.getCurrentLocationInfo()
        } catch (e: Exception) {
            handler.removeCallbacks(locationTimeout)
            locationPending = false
            locationText =
                "위치 요청 실패: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)

        locationManager?.let { lm ->
            runCatching { lm.removeUpdates(androidLocListener) }
        }
        locationManager = null

        naviHelper?.let { sdk ->
            runCatching { sdk.removeListener(naviListener) }
            runCatching { sdk.release() }
        }

        vehicle?.let { sdk ->
            runCatching { sdk.release() }
        }

        naviHelper = null
        vehicle = null

        super.onDestroy()
    }
}