package com.example.malttuk

import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var audioManager: AudioManager
    private lateinit var focusRequest: AudioFocusRequest

    private var player: MediaPlayer? = null
    private var hasAudioFocus = false
    private var screenResumed = false

    private data class VehiclePlaybackState(
        val arrived: Boolean?,
        val parked: Boolean?,
        val doorsClosed: Boolean?
    ) {
        fun blockingReason(): String? = when {
            parked == false -> "차량이 주차 상태가 아닙니다"
            doorsClosed == false -> "문이 열려 있습니다"
            arrived == false -> "목적지에 도착하지 않았습니다"
            parked == null -> "주차 상태를 확인할 수 없습니다"
            doorsClosed == null -> "문 상태를 확인할 수 없습니다"
            arrived == null -> "도착 여부를 확인할 수 없습니다"
            else -> null
        }
    }

    private data class MockVehicleControls(
        val arrival: Spinner,
        val parking: Spinner,
        val doors: Spinner,
        val replyInput: Spinner
    ) {
        fun state() = VehiclePlaybackState(
            arrived = readSelection(arrival),
            parked = readSelection(parking),
            doorsClosed = readSelection(doors)
        )

        fun isReplyInputAllowed(): Boolean = readSelection(replyInput) == true

        fun reset() {
            arrival.setSelection(0)
            parking.setSelection(0)
            doors.setSelection(0)
            replyInput.setSelection(0)
        }

        private fun readSelection(input: Spinner): Boolean? =
            when (input.selectedItemPosition) {
                1 -> true
                2 -> false
                else -> null
            }
    }

    private data class DeliveryKey(val messageId: String, val tripId: Int)

    private val testMessageId = "test-message-01"
    private var mockTripId = 1
    private val completedDeliveries = mutableSetOf<DeliveryKey>()
    private val mockReplies = mutableMapOf<DeliveryKey, String>()

    private var mockVehicleControls: MockVehicleControls? = null
    private var listenForThisTrip = false
    private var playbackStatus: TextView? = null
    private var refreshReplyUi: (() -> Unit)? = null

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val isDebugApp: Boolean
        get() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun currentDelivery() = DeliveryKey(testMessageId, mockTripId)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioManager = getSystemService(AudioManager::class.java)
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(
                { change ->
                    when (change) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            stopPlayback("다른 오디오 우선으로 중단")
                        }
                    }
                },
                Handler(Looper.getMainLooper())
            )
            .build()

        val mainView = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val listContent = (findViewById<LinearLayout>(R.id.main).getChildAt(0) as ScrollView).getChildAt(0) as LinearLayout
        listContent.addView(Button(this).apply {
            text = "서버 메시지 연결"
            minHeight = dp(64)
            setOnClickListener {
                startActivity(android.content.Intent(this@MainActivity,
                    com.example.malttuk.integration.ServerIntegrationActivity::class.java))
            }
        }, 1)

        findViewById<View>(R.id.messageCard).setOnClickListener {
            showTestMessageDetails()
        }

        Log.d("ChamamothanmalUi", "SCREEN_READY_AUDIO_V1")
    }

    override fun onResume() {
        super.onResume()
        screenResumed = true
        refreshReplyUi?.invoke()
    }

    override fun onPause() {
        screenResumed = false
        if (player != null) {
            stopPlayback("화면 이탈로 중단")
        }
        mockVehicleControls?.reset()
        refreshReplyUi?.invoke()
        super.onPause()
    }

    override fun onDestroy() {
        stopPlayback("재생 종료")
        playbackStatus = null
        mockVehicleControls = null
        refreshReplyUi = null
        super.onDestroy()
    }

    private fun showTestMessageDetails() {
        val root = findViewById<LinearLayout>(R.id.main)
        val listScreen = root.getChildAt(0)

        val detailScreen = ScrollView(this).apply {
            isFillViewport = true
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }

        detailScreen.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        fun addText(value: String, size: Float): TextView {
            val textView = TextView(this).apply {
                text = value
                textSize = size
                setTextColor(Color.parseColor("#182230"))
            }

            content.addView(
                textView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(20)
                }
            )
            return textView
        }

        fun addButton(label: String, action: () -> Unit): Button {
            val button = Button(this).apply {
                text = label
                textSize = 20f
                minHeight = dp(64)
                setOnClickListener { action() }
            }

            content.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(12)
                }
            )
            return button
        }

        addText("차마못한말 · 테스트 상세", 28f)
        addText("발표회에 도착하면 들어요", 24f)
        addText("목적지: 테스트 발표회장", 20f)
        addText(
            "실제 차량 상태는 아직 미확인입니다.\n" +
                "아래 기능은 에뮬레이터에서 파일 재생을 확인하는 모의 테스트입니다.",
            18f
        )

        val initialStatus = if (currentDelivery() in completedDeliveries) {
            "테스트 음성 재생 완료"
        } else {
            "대기"
        }
        playbackStatus = addText("재생 상태: $initialStatus", 20f)

        if (isDebugApp) {
            val tripLabel = addText("모의 이동 $mockTripId", 18f)

            fun addMockSelector(label: String, allowed: String, blocked: String): Spinner {
                val selector = Spinner(this).apply {
                    id = View.generateViewId()
                    minimumHeight = dp(56)
                    adapter = ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_spinner_item,
                        listOf("미확인", allowed, blocked)
                    ).apply {
                        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    }

                    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: AdapterView<*>,
                            view: View?,
                            position: Int,
                            id: Long
                        ) {
                            stopIfVehicleStateBlocksPlayback()
                            refreshReplyUi?.invoke()
                        }

                        override fun onNothingSelected(parent: AdapterView<*>) {
                            stopIfVehicleStateBlocksPlayback()
                            refreshReplyUi?.invoke()
                        }
                    }
                }

                addText(label, 18f).labelFor = selector.id
                content.addView(
                    selector,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
                return selector
            }

            mockVehicleControls = MockVehicleControls(
                arrival = addMockSelector("모의 도착 상태", "도착", "도착 전"),
                parking = addMockSelector("모의 주차 상태", "주차", "주행"),
                doors = addMockSelector("모의 문 상태", "문 닫힘", "문 열림"),
                replyInput = addMockSelector("모의 답장 입력 상태", "허용", "제한")
            )

            val listenCheckBox = CheckBox(this).apply {
                text = "이번 이동에서 듣기"
                textSize = 18f
                minHeight = dp(64)
                isChecked = listenForThisTrip

                setOnCheckedChangeListener { checkbox, checked ->
                    if (checked && mockVehicleControls?.state()?.parked != true) {
                        checkbox.isChecked = false
                        updateStatus("주차 상태에서 이번 이동의 듣기를 선택해주세요")
                    } else {
                        listenForThisTrip = checked

                        if (checked) {
                            updateStatus("이번 이동에서 듣기 선택됨")
                        } else if (player != null) {
                            stopPlayback("재생 중단 · 이번 이동의 듣기 선택을 해제했습니다")
                        } else {
                            updateStatus("이번 이동의 듣기 선택을 해제했습니다")
                        }
                    }
                }
            }

            content.addView(listenCheckBox)

            addButton("새 모의 이동 시작") {
                if (player != null) {
                    stopPlayback("재생 중단 · 새 모의 이동 시작")
                }
                mockTripId += 1
                listenForThisTrip = false
                listenCheckBox.isChecked = false
                mockVehicleControls?.reset()
                tripLabel.text = "모의 이동 $mockTripId"
                updateStatus("새 모의 이동 · 차량 상태와 듣기 선택을 설정해주세요")
                refreshReplyUi?.invoke()
            }
        }

        addButton("테스트 음성 재생") {
            startTestPlayback()
        }

        addButton("멈춤") {
            if (player != null) {
                stopPlayback("사용자가 중단")
            }
        }

        if (isDebugApp) {
            addText("짧은 답장 · 모의 테스트", 20f)
            val replyAvailability = addText("", 18f)
            val replySelection = addText("", 18f)

            fun selectReply(reply: String) {
                if (replyBlockingReason() == null) {
                    mockReplies[currentDelivery()] = reply
                }
                refreshReplyUi?.invoke()
            }

            val thanksButton = addButton("고마워요") {
                selectReply("고마워요")
            }
            val arrivedButton = addButton("잘 도착했어요") {
                selectReply("잘 도착했어요")
            }

            refreshReplyUi = {
                val reason = replyBlockingReason()
                val selected = mockReplies[currentDelivery()]

                thanksButton.isEnabled = reason == null
                arrivedButton.isEnabled = reason == null
                replyAvailability.text = if (reason == null) {
                    "답장을 선택할 수 있어요"
                } else {
                    "답장 보류 · $reason"
                }
                replySelection.text = if (selected == null) {
                    "모의 답장 선택 없음"
                } else {
                    "모의 답장 선택 · $selected"
                }
            }
            refreshReplyUi?.invoke()
        }

        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                stopPlayback("목록 복귀로 중단")
                mockVehicleControls = null
                playbackStatus = null
                refreshReplyUi = null

                root.removeView(detailScreen)
                listScreen.visibility = View.VISIBLE
                remove()

                Log.d("ChamamothanmalUi", "LIST_SHOWN")
            }
        }

        addButton("목록으로 돌아가기") {
            backCallback.handleOnBackPressed()
        }

        listScreen.visibility = View.GONE
        root.addView(
            detailScreen,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )

        onBackPressedDispatcher.addCallback(this, backCallback)
        Log.d("ChamamothanmalUi", "DETAIL_SHOWN")
    }

    private fun replyBlockingReason(): String? {
        if (!isDebugApp || !screenResumed) {
            return "테스트 화면에서만 답장을 선택할 수 있습니다"
        }

        val controls = mockVehicleControls
            ?: return "모의 차량 상태를 확인할 수 없습니다"

        return when {
            controls.state().parked != true -> "주차 상태를 확인해주세요"
            !controls.isReplyInputAllowed() -> "답장 입력이 허용된 상태여야 합니다"
            currentDelivery() !in completedDeliveries -> "음성을 끝까지 들은 뒤 답장할 수 있습니다"
            else -> null
        }
    }

    private fun vehicleBlockingReason(): String? {
        val controls = mockVehicleControls
            ?: return "모의 차량 상태를 확인할 수 없습니다"

        val vehicleReason = controls.state().blockingReason()
        if (vehicleReason != null) {
            return vehicleReason
        }

        return if (listenForThisTrip) {
            null
        } else {
            "이번 이동에서 듣기를 먼저 선택해주세요"
        }
    }

    private fun stopIfVehicleStateBlocksPlayback() {
        if (!screenResumed || player == null) {
            return
        }

        val reason = vehicleBlockingReason() ?: return
        stopPlayback("재생 중단 · $reason")
    }

    private fun startTestPlayback() {
        if (!isDebugApp || !screenResumed) {
            updateStatus("재생 보류 · 테스트 화면에서만 재생할 수 있습니다")
            return
        }

        if (player != null) {
            return
        }

        val delivery = currentDelivery()
        if (delivery in completedDeliveries) {
            updateStatus("이미 재생 완료 · 이번 이동에서 이 메시지를 들었습니다")
            return
        }

        val reason = vehicleBlockingReason()
        if (reason != null) {
            updateStatus("재생 보류 · $reason")
            return
        }

        try {
            val result = audioManager.requestAudioFocus(focusRequest)
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                audioManager.abandonAudioFocusRequest(focusRequest)
                updateStatus("재생 보류 · 오디오 포커스를 얻지 못했습니다")
                return
            }

            hasAudioFocus = true

            val newPlayer = MediaPlayer()
            player = newPlayer
            newPlayer.setAudioAttributes(audioAttributes)

            @android.annotation.SuppressLint("DiscouragedApi")
            val sampleId = resources.getIdentifier("chamamothanmal_test", "raw", packageName)
            if (sampleId == 0) {
                stopPlayback("로컬 시험 음성이 없습니다. 서버 메시지 연결 화면을 사용하세요")
                return
            }
            resources.openRawResourceFd(sampleId).use { descriptor ->
                newPlayer.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length
                )
            }

            newPlayer.setOnPreparedListener { preparedPlayer ->
                if (player === preparedPlayer) {
                    val blockingReason = vehicleBlockingReason()

                    if (screenResumed && blockingReason == null && hasAudioFocus) {
                        try {
                            preparedPlayer.start()
                            updateStatus("테스트 음성 재생 중")
                        } catch (error: Exception) {
                            Log.e("ChamamothanmalUi", "PLAY_START_FAILED", error)
                            stopPlayback("재생 시작 실패")
                        }
                    } else {
                        stopPlayback(
                            "재생 중단 · ${blockingReason ?: "재생 조건이 바뀌었습니다"}"
                        )
                    }
                }
            }

            newPlayer.setOnCompletionListener { completedPlayer ->
                if (player === completedPlayer) {
                    val blockingReason = vehicleBlockingReason()
                    if (blockingReason != null) {
                        stopPlayback("재생 중단 · $blockingReason")
                    } else {
                        completedDeliveries.add(delivery)
                        stopPlayback("테스트 음성 재생 완료")
                        refreshReplyUi?.invoke()
                    }
                }
            }

            newPlayer.setOnErrorListener { failedPlayer, what, extra ->
                if (player === failedPlayer) {
                    Log.e("ChamamothanmalUi", "PLAYER_ERROR what=$what extra=$extra")
                    stopPlayback("음성 파일 재생 오류")
                }
                true
            }

            updateStatus("음성 파일 준비 중")
            newPlayer.prepareAsync()
        } catch (error: Exception) {
            Log.e("ChamamothanmalUi", "PLAYBACK_FAILED", error)
            stopPlayback("음성 파일 준비 실패")
        }
    }

    private fun stopPlayback(reason: String) {
        val oldPlayer = player
        player = null
        oldPlayer?.release()

        if (hasAudioFocus) {
            hasAudioFocus = false
            audioManager.abandonAudioFocusRequest(focusRequest)
        }

        updateStatus(reason)
    }

    private fun updateStatus(message: String) {
        playbackStatus?.text = "재생 상태: $message"
        Log.d("ChamamothanmalUi", message)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
