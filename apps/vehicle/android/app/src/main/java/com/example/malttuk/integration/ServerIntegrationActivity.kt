package com.example.malttuk.integration

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.*
import android.os.*
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import io.chamamothanmal.client.VehicleFamilyClient
import io.chamamothanmal.client.VoiceMessageClient
import io.chamamothanmal.vehiclestate.*
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** Explicit/manual integration screen. Never autoplays and never embeds developer credentials. */
class ServerIntegrationActivity : AppCompatActivity() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var monitor: VehicleStateMonitor
    private var ux: CarUxGuard? = null
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var stateText: TextView
    private lateinit var choices: RadioGroup
    private lateinit var consent: CheckBox
    private lateinit var mock: CheckBox
    private lateinit var serverInput: EditText
    private lateinit var userInput: EditText
    private lateinit var codeInput: EditText
    private val guarded = mutableListOf<View>()
    private var resumed = false
    private var destroyed = false
    @Volatile private var generation = 0
    private var client: VehicleFamilyClient? = null
    private var vehicleId: String? = null
    private var vehicleToken: String? = null
    private var selected: Message? = null
    private var trip: Trip? = null
    private var startAttempt: StartAttempt? = null
    private var player: MediaPlayer? = null
    private var hasFocus = false
    @Volatile private var playbackStarted = false
    private var playbackSource = "MANUAL"
    private var preparing = false
    private lateinit var audio: AudioManager
    private lateinit var focus: AudioFocusRequest
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val debug: Boolean get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private data class Message(val id: String, val name: String, val address: String, val destination: Destination)
    private data class StartAttempt(val requestId: String, val messageId: String, val latitude: Double, val longitude: Double, val source: String)
    private class Trip(val api: VehicleFamilyClient, val sessionId: String, val messageId: String, @Volatile var reporter: PlaybackReporter? = null) { val replyIds = mutableMapOf<String, String>() }
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { monitor.start() }
    private val ticker = object : Runnable {
        override fun run() { if (resumed) { stateChanged(); main.postDelayed(this, 1000) } }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monitor = VehicleStateMonitor(this)
        ux = try {
            Class.forName("android.car.Car")
            CarUxGuard(this) { if (::status.isInitialized) stateChanged() }
        } catch (error: Exception) { null } catch (error: LinkageError) { null }
        audio = getSystemService(AudioManager::class.java)
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false).setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) stopVoice("오디오 우선권 상실 · 자동 재개하지 않음", "AUDIO_FOCUS_LOST")
            }, main).build()
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 28, 28, 28) }
        setContentView(ScrollView(this).apply { addView(content) })
        label("차마못한말 · 서버 메시지 연결", 24f)
        label("먼저 가족 구성원으로 로그인하고 관리자가 발급한 차량 연결 코드를 입력하세요. 토큰은 소스·로그에 저장하지 않습니다.")
        stateText = label("차량 상태 수신 대기")
        status = label("미연결")
        mock = CheckBox(this).apply {
            text = "디버그: 모의 P단·도어·도착·UX 조건 사용 (실제 신호 아님)"
            visibility = if (debug) View.VISIBLE else View.GONE
            setOnCheckedChangeListener { _, _ -> stopVoice("시험 조건 변경", "STATE_UNKNOWN"); applyMock(); stateChanged() }
        }
        content.addView(mock)
        serverInput = field("서버 주소", "http://10.0.2.2:8000")
        userInput = field("사용자 access_token / 로컬 수신자 시험 토큰", secret = true)
        codeInput = field("관리자가 발급한 차량 연결 코드", secret = true)
        button("차량 연결") { connect() }
        button("메시지 목록 새로고침") { loadMessages() }
        choices = RadioGroup(this); content.addView(choices)
        consent = CheckBox(this).apply { text = "동승자와 공동 청취에 동의하고 이번 이동에서 듣기"; isChecked = false }
        content.addView(consent); guarded += consent
        button("이번 이동에서 듣기 시작") { startTrip() }
        button("도착·주차 확인 후 재생") { play() }
        button("멈춤") { stopVoice("사용자 중단 · 자동 재개하지 않음", "USER_ACTION") }
        button("고마워요") { reply("THANK_YOU") }
        button("잘 도착했어요") { reply("ARRIVED_SAFELY") }
        button("결과 보고 재시도") { retryReports() }
        button("이동 종료") { endTrip("CANCELLED"); show("이동을 종료했습니다") }
        button("이 기기 연결 정보 지우기") { clearConnection(); show("기기 세션·토큰·캐시를 지웠습니다") }
        if (debug) {
            button("디버그: 문 열림 모의", false) { if (mock.isChecked) monitor.setMockDoor(DoorStatus.OPEN) }
            button("디버그: 출발 모의", false) { if (mock.isChecked) monitor.setMockGear(GearPosition.NOT_PARK) }
            button("디버그: P단·문 닫힘 모의 복구", false) { if (mock.isChecked) applyMock() }
        }
        monitor.addListener { stateChanged() }
        monitor.addNaviEventListener { event ->
            when (event) {
                NaviEvent.DESTINATION_CHANGED -> { endTrip("DESTINATION_CHANGED"); show("내비 목적지 변경 · 이전 이동 종료") }
                NaviEvent.ROUTE_CANCELLED -> { endTrip("CANCELLED"); show("내비 경로 취소 · 이전 이동 종료") }
                else -> stateChanged()
            }
        }
        val missing = VehicleStateMonitor.REQUIRED_RUNTIME_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
    }
    private fun label(text: String, size: Float = 17f) = TextView(this).apply {
        this.text = text; textSize = size; setPadding(0, 10, 0, 10); content.addView(this)
    }
    private fun field(hint: String, value: String = "", secret: Boolean = false) = EditText(this).apply {
        this.hint = hint; setText(value); isSingleLine = true; isSaveEnabled = false
        inputType = InputType.TYPE_CLASS_TEXT or if (secret) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_URI
        content.addView(this); guarded += this
    }
    private fun button(title: String, guardedByState: Boolean = true, action: () -> Unit) {
        val b = Button(this).apply { text = title; minHeight = 64; setOnClickListener { action() } }
        content.addView(b); if (guardedByState) guarded += b
    }
    private fun show(text: String) { if (!destroyed) status.text = text }
    private fun parked(): Boolean {
        val gear = monitor.snapshot.gear ?: return false
        val age = SystemClock.elapsedRealtime() - gear.elapsedRealtimeMs
        return gear.position == GearPosition.PARK && age in 0..10_000
    }
    private fun uxAllowed() = ux?.allowsInteraction == true || (debug && mock.isChecked)
    private fun canInteract() = resumed && parked() && uxAllowed()
    private fun decision(): PlaybackDecision = selected?.let { monitor.evaluate(it.destination) }
        ?: PlaybackDecision.Hold(HoldReason.GEAR_UNKNOWN, "메시지를 선택하세요")
    private fun stateChanged() {
        if (!::stateText.isInitialized || !::mock.isInitialized) return
        val source = monitor.snapshot.gear?.source?.name ?: "UNKNOWN"
        stateText.text = "기어: ${monitor.snapshot.gear?.position ?: "UNKNOWN"} / 출처: $source / UX: ${if (uxAllowed()) "허용" else "미확인·제한"}"
        guarded.forEach { it.isEnabled = canInteract() }
        if (::choices.isInitialized) for (index in 0 until choices.childCount) choices.getChildAt(index).isEnabled = canInteract()
        if (player != null && (!resumed || !uxAllowed() || decision() !is PlaybackDecision.Allow)) {
            val hold = decision() as? PlaybackDecision.Hold
            stopVoice("차량·UX 조건 변경으로 중단 · ${hold?.detail ?: "사용 제한"}", hold?.reason?.contractReason ?: "STATE_UNKNOWN")
        }
    }
    private fun applyMock() {
        val enabled = debug && mock.isChecked
        monitor.setMockGear(if (enabled) GearPosition.PARK else null)
        monitor.setMockDoor(if (enabled) DoorStatus.CLOSED else null)
        val d = selected?.destination
        monitor.setMockLocation(if (enabled) d?.latitude else null, if (enabled) d?.longitude else null)
        monitor.setMockNavi(if (enabled) d?.latitude else null, if (enabled) d?.longitude else null, enabled && d != null)
    }
    private fun requireInteraction(): Boolean {
        if (canInteract()) return true
        show("주차 및 사용 제한을 확인할 수 없어 보류합니다. 디버그 모의 시험은 명시적으로 선택하세요")
        return false
    }
    private fun connect() {
        if (!requireInteraction()) return
        val base = serverInput.text.toString().trim(); val user = userInput.text.toString().trim(); val code = codeInput.text.toString().trim()
        if (!debug && !base.startsWith("https://")) { show("HTTPS 서버 주소를 사용하세요"); return }
        clearConnection(); val g = generation
        val pairedToken = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val api = VehicleFamilyClient(base, { user }, { pairedToken.get() })
        worker.execute {
            try {
                val result = api.pair(code)
                main.post {
                    if (g != generation || destroyed) return@post
                    client = api; vehicleId = result.getString("vehicleId"); vehicleToken = result.getString("vehicleToken")
                    pairedToken.set(vehicleToken)
                    userInput.setText(""); codeInput.setText(""); show("차량 연결 완료 · 목록을 새로고침하세요")
                }
            } catch (error: Exception) { failure(error, g) }
        }
    }
    private fun loadMessages() {
        if (!requireInteraction()) return
        val api = client ?: return show("차량을 먼저 연결하세요")
        val car = vehicleId ?: return
        endTrip("CANCELLED"); selected = null; choices.removeAllViews(); consent.isChecked = false
        val g = generation
        worker.execute {
            try {
                val items = api.candidates(car).getJSONArray("items")
                val messages = (0 until items.length()).map { index ->
                    val item = items.getJSONObject(index)
                    Message(item.getString("messageId"), item.getString("placeName"), item.optString("address", ""),
                        Destination(item.getDouble("latitude"), item.getDouble("longitude"), item.getInt("radiusMeters")))
                }
                main.post {
                    if (g != generation || destroyed) return@post
                    for (m in messages) choices.addView(RadioButton(this).apply {
                        id = View.generateViewId(); text = "${m.name}\n${m.address}"; minHeight = 64
                        setOnCheckedChangeListener { _, checked -> if (checked) {
                            endTrip("CANCELLED"); selected = m; consent.isChecked = false; startAttempt = null; applyMock(); show("선택됨 · 출발 전 수신을 선택하세요")
                        } }
                    })
                    stateChanged()
                    show(if (messages.isEmpty()) "유효한 새 메시지가 없습니다" else "메시지를 선택하세요")
                }
            } catch (error: Exception) { failure(error, g) }
        }
    }
    private fun startTrip() {
        if (!requireInteraction()) return
        if (!consent.isChecked) return show("이번 이동의 공동 청취·수신 동의가 필요합니다")
        val api = client ?: return show("차량을 먼저 연결하세요")
        val car = vehicleId ?: return
        val message = selected ?: return show("메시지를 선택하세요")
        if (trip != null) return show("이미 시작한 이동입니다. 재생 조건을 확인한 뒤 재생하세요")
        val navi = monitor.snapshot.navi
        if (navi?.hasDestination != true) { monitor.refreshNaviDestination(); return show("내비 목적지를 설정하고 목적지 수신 후 다시 선택하세요") }
        val source = if (monitor.snapshot.gear?.source == SignalSource.MOCK || navi.source == SignalSource.MOCK) "MOCK" else navi.source.name
        val attempt = startAttempt ?: StartAttempt(UUID.randomUUID().toString(), message.id, navi.destinationLatitude!!,
            navi.destinationLongitude!!, source).also { startAttempt = it }
        val g = generation
        worker.execute {
            try {
                val result = api.start(car, attempt.requestId, attempt.messageId, attempt.latitude, attempt.longitude, true, true, attempt.source)
                val active = Trip(api, result.getString("sessionId"), message.id)
                main.post {
                    if (g != generation || destroyed || !resumed) {
                        if (!worker.isShutdown) worker.execute { runCatching { api.end(active.sessionId, "CANCELLED") } }
                        return@post
                    }
                    trip = active; show("이동 연결 완료 · 도착 후 조건을 확인하고 재생하세요. 출처: ${attempt.source}")
                }
            } catch (error: Exception) { failure(error, g) }
        }
    }
    private fun play() {
        if (!requireInteraction()) return
        val allow = decision() as? PlaybackDecision.Allow ?: return show("재생 보류 · ${(decision() as PlaybackDecision.Hold).detail}")
        val active = trip ?: return show("이번 이동에서 듣기를 먼저 시작하세요")
        if (player != null || preparing) return
        if (active.reporter?.blocked == true) return show("전송이 확인되지 않은 결과가 있습니다. 보고 재시도를 먼저 진행하세요")
        preparing = true; val g = generation
        worker.execute {
            var file: File? = null
            try {
                check(active.reporter?.blocked != true) { "Retry pending playback reports first" }
                val detail = active.api.delivery(active.sessionId, active.messageId).getJSONObject("playback")
                if (detail.getString("status") in setOf("COMPLETED", "SKIPPED")) error("Terminal delivery")
                val reporter = PlaybackReporter(detail.getLong("version"), detail.getString("status")) { report ->
                    active.api.report(active.sessionId, active.messageId, VoiceMessageClient.PlaybackEvent(
                        report.eventId, report.status, report.expectedVersion, report.source, report.reason)).getLong("version")
                }
                active.reporter = reporter
                if (reporter.status == "PLAYING") reporter.record("HELD", allow.source.name, "NETWORK_ERROR")
                val bytes = active.api.download(active.sessionId, active.messageId)
                file = File.createTempFile("server-voice-", ".wav", cacheDir).apply { writeBytes(bytes) }
                val ready = requireNotNull(file)
                main.post {
                    preparing = false
                    if (g != generation || destroyed || trip !== active || !resumed || !uxAllowed() || decision() !is PlaybackDecision.Allow) {
                        ready.delete(); return@post
                    }
                    preparePlayer(ready, active)
                }
            } catch (error: Exception) { file?.delete(); main.post { if (g == generation) preparing = false }; failure(error, g) }
        }
    }
    private fun preparePlayer(file: File, active: Trip) {
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            file.delete(); show("오디오 포커스 미확보 · 보류"); return
        }
        hasFocus = true
        try {
            val media = MediaPlayer(); player = media
            media.setAudioAttributes(attributes); media.setDataSource(file.absolutePath)
            media.setOnPreparedListener {
                val allow = decision() as? PlaybackDecision.Allow
                if (player !== media || trip !== active || !resumed || !uxAllowed() || allow == null) {
                    stopVoice("준비 중 차량 상태 변경 · 보류", "STATE_UNKNOWN"); file.delete(); return@setOnPreparedListener
                }
                try {
                    media.start(); playbackStarted = true; playbackSource = allow.source.name
                    queueReport(active, "PLAYING", playbackSource, "USER_ACTION")
                    show("서버 음성 재생 중 · 출처: $playbackSource")
                } catch (error: Exception) { stopVoice("재생 실패", "PLAYBACK_ERROR") }
            }
            media.setOnCompletionListener {
                if (player === media) {
                    playbackStarted = false; releasePlayer(); file.delete()
                    queueReport(active, "COMPLETED", playbackSource, null); show("음성 재생 종료 · 결과 보고 중")
                }
            }
            media.setOnErrorListener { _, _, _ -> stopVoice("재생 실패", "PLAYBACK_ERROR"); file.delete(); true }
            media.prepareAsync()
        } catch (error: Exception) { file.delete(); stopVoice("파일 준비 실패", "PLAYBACK_ERROR") }
    }
    private fun queueReport(active: Trip, state: String, source: String, reason: String?) {
        worker.execute {
            try {
                active.reporter?.record(state, source, reason)
                if (state == "COMPLETED") main.post { if (trip === active) show("완료 보고 확인 · 주차 중 버튼 답장을 보낼 수 있습니다") }
            } catch (error: Exception) {
                main.post { if (trip === active) stopVoice("결과 보고 실패 · 재생 중단, 보고 재시도 필요", "NETWORK_ERROR", false) }
                failure(error, expectedTrip = active)
            }
        }
    }
    private fun stopVoice(message: String, reason: String, report: Boolean = true) {
        val wasStarted = playbackStarted; playbackStarted = false
        val active = trip; releasePlayer()
        if (wasStarted && active != null && report) queueReport(active, "HELD", playbackSource, reason)
        if (::status.isInitialized) show(message)
    }
    private fun releasePlayer() {
        player?.setOnPreparedListener(null); player?.setOnCompletionListener(null); player?.setOnErrorListener(null)
        runCatching { player?.release() }; player = null
        if (hasFocus) { audio.abandonAudioFocusRequest(focus); hasFocus = false }
        cacheDir.listFiles()?.filter { it.name.startsWith("server-voice-") && it.isFile }?.forEach { it.delete() }
    }
    private fun retryReports() {
        if (!requireInteraction()) return
        val active = trip ?: return show("이동을 먼저 시작하세요")
        worker.execute {
            try {
                val reporter = active.reporter ?: return@execute
                reporter.flush()
                if (reporter.status == "PLAYING" && !playbackStarted) reporter.record("HELD", playbackSource, "NETWORK_ERROR")
                main.post { if (trip === active) show("보고 재시도 확인 · 재생은 자동 재개하지 않습니다") }
            } catch (error: Exception) { failure(error, expectedTrip = active) }
        }
    }
    private fun reply(kind: String) {
        if (!requireInteraction()) return
        val active = trip ?: return show("이동이 없습니다")
        val replyId = active.replyIds.getOrPut(kind) { UUID.randomUUID().toString() }
        worker.execute {
            try {
                active.api.reply(active.sessionId, active.messageId, replyId, kind, true)
                main.post { if (trip === active) show("답장을 보냈습니다") }
            } catch (error: Exception) { failure(error, expectedTrip = active) }
        }
    }
    private fun endTrip(reason: String) {
        generation++; preparing = false; startAttempt = null
        if (::consent.isInitialized) consent.isChecked = false
        stopVoice("이동 종료", "USER_ACTION")
        val active = trip; trip = null
        if (active != null && !worker.isShutdown) worker.execute { runCatching { active.api.end(active.sessionId, reason) } }
    }
    private fun clearConnection() {
        endTrip("PROFILE_CHANGED"); client = null; vehicleId = null; vehicleToken = null; selected = null
        if (::choices.isInitialized) choices.removeAllViews()
        if (::consent.isInitialized) consent.isChecked = false
    }
    private fun failure(error: Exception, expectedGeneration: Int = generation, expectedTrip: Trip? = null) {
        main.post {
            if (destroyed || expectedGeneration != generation || (expectedTrip != null && trip !== expectedTrip)) return@post
            val apiError = error as? VoiceMessageClient.ApiFailure
            if (apiError?.status in setOf(401, 403, 404, 410)) clearConnection()
            show(if (apiError != null) "서버 응답 ${apiError.status} · ${apiError.code}. 권한/연결 또는 상태를 확인하세요"
                else "통신·처리에 실패했습니다. 연결과 결과 보고 상태를 확인하고 다시 시도하세요")
        }
    }
    override fun onResume() {
        super.onResume(); resumed = true; monitor.start(); ux?.start(); main.post(ticker)
    }
    override fun onPause() {
        resumed = false; main.removeCallbacks(ticker); endTrip("CANCELLED")
        monitor.stop(); ux?.stop(); super.onPause()
    }
    override fun onDestroy() {
        destroyed = true; main.removeCallbacks(ticker); releasePlayer(); monitor.stop(); ux?.stop()
        vehicleToken = null; client = null; worker.shutdown(); super.onDestroy()
    }
}
