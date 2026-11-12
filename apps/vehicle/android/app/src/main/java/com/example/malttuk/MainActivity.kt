package com.example.malttuk

import android.annotation.SuppressLint
import android.content.Intent
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.malttuk.integration.ServerIntegrationActivity

class MainActivity : AppCompatActivity() {

    private enum class ListeningMode {
        AUTOMATIC,
        CONFIRM_FIRST
    }

    private data class DeliveryKey(
        val messageId: String,
        val tripId: Int
    )

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

        fun isReplyInputAllowed(): Boolean =
            readSelection(replyInput) == true

        fun reset() {
            arrival.setSelection(0)
            parking.setSelection(0)
            doors.setSelection(0)
            replyInput.setSelection(0)
        }

        private fun readSelection(selector: Spinner): Boolean? =
            when (selector.selectedItemPosition) {
                1 -> true
                2 -> false
                else -> null
            }
    }

    private lateinit var audioManager: AudioManager
    private lateinit var focusRequest: AudioFocusRequest

    private val mainHandler = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var hasAudioFocus = false
    private var screenResumed = false
    private var detailReady = false

    private var mockMessageNumber = 1
    private var mockTripId = 1
    private var listeningMode: ListeningMode? = null

    private val completedDeliveries = mutableSetOf<DeliveryKey>()
    private val automaticallyHandledDeliveries = mutableSetOf<DeliveryKey>()
    private val deletedMessages = mutableSetOf<String>()
    private val mockReplies = mutableMapOf<DeliveryKey, String>()

    private var mockVehicleControls: MockVehicleControls? = null
    private var confirmationDialog: AlertDialog? = null
    private var playbackStatus: TextView? = null
    private var refreshDetailUi: (() -> Unit)? = null

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val isDebugApp: Boolean
        get() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun currentMessageId(): String =
        "test-message-$mockMessageNumber"

    private fun currentDelivery(): DeliveryKey =
        DeliveryKey(currentMessageId(), mockTripId)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioManager = getSystemService(AudioManager::class.java)

        focusRequest = AudioFocusRequest.Builder(
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(
                { change ->
                    when (change) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            if (player != null) {
                                stopPlayback("다른 오디오 우선으로 중단")
                            }
                        }
                    }
                },
                mainHandler
            )
            .build()

        val root = findViewById<LinearLayout>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val listScroll = root.getChildAt(0) as ScrollView
        val listContent = listScroll.getChildAt(0) as LinearLayout

        listContent.addView(
            Button(this).apply {
                text = "서버 메시지 연결"
                minimumHeight = dp(64)
                setOnClickListener {
                    startActivity(
                        Intent(
                            this@MainActivity,
                            ServerIntegrationActivity::class.java
                        )
                    )
                }
            },
            1
        )

        findViewById<View>(R.id.messageCard).setOnClickListener {
            showTestMessageDetails()
        }

        Log.d("chamamothanmal_ui", "SCREEN_READY_LISTENING_MODES")
    }

    override fun onResume() {
        super.onResume()
        screenResumed = true
        refreshDetailUi?.invoke()
    }

    override fun onPause() {
        screenResumed = false

        if (detailReady) {
            automaticallyHandledDeliveries.add(currentDelivery())
        }

        dismissConfirmation()

        if (player != null) {
            stopPlayback("화면 이탈로 중단")
        }

        mockVehicleControls?.reset()
        refreshDetailUi?.invoke()
        super.onPause()
    }

    override fun onDestroy() {
        detailReady = false
        dismissConfirmation()
        stopPlayback("재생 종료")
        playbackStatus = null
        mockVehicleControls = null
        refreshDetailUi = null
        super.onDestroy()
    }

    private fun showTestMessageDetails() {
        if (detailReady) return

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

        fun addText(value: String, size: Float = 18f): TextView {
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
                    bottomMargin = dp(16)
                }
            )
            return textView
        }

        fun addButton(label: String, action: () -> Unit): Button {
            val button = Button(this).apply {
                text = label
                textSize = 20f
                minimumHeight = dp(64)
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

        fun addSelector(
            label: String,
            choices: List<String>,
            onSelected: (Int) -> Unit
        ): Spinner {
            val selector = Spinner(this).apply {
                id = View.generateViewId()
                minimumHeight = dp(56)
                adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_spinner_item,
                    choices
                ).apply {
                    setDropDownViewResource(
                        android.R.layout.simple_spinner_dropdown_item
                    )
                }
            }

            addText(label).labelFor = selector.id
            content.addView(
                selector,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(12)
                }
            )

            selector.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        if (detailReady) {
                            onSelected(position)
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>) {
                        if (detailReady) {
                            onSelected(0)
                        }
                    }
                }

            return selector
        }

        addText("차마못한말 · 청취 모드 테스트", 28f)
        addText("발표회에 도착하면 들어요", 24f)
        addText("목적지 · 테스트 발표회장")
        addText(
            "개발용 모의 메시지입니다. 차량 상태와 발신자의 청취 모드를 " +
                "이 화면에서 설정해 동작을 확인합니다. 삭제와 이모지 선택은 " +
                "앱 메모리에만 기록됩니다."
        )

        playbackStatus = addText("재생 상태 · 대기", 20f)

        if (isDebugApp) {
            val messageLabel = addText("")
            val tripLabel = addText("")

            val modeSelector = addSelector(
                "모의 발신자가 지정한 청취 모드",
                listOf("미선택", "자동 재생", "재생 전 확인")
            ) { position ->
                listeningMode = when (position) {
                    1 -> ListeningMode.AUTOMATIC
                    2 -> ListeningMode.CONFIRM_FIRST
                    else -> null
                }
                onMockStateChanged()
            }

            modeSelector.setSelection(
                when (listeningMode) {
                    ListeningMode.AUTOMATIC -> 1
                    ListeningMode.CONFIRM_FIRST -> 2
                    null -> 0
                }
            )

            fun addVehicleSelector(
                label: String,
                allowed: String,
                blocked: String
            ): Spinner = addSelector(
                label,
                listOf("미확인", allowed, blocked)
            ) {
                onMockStateChanged()
            }

            mockVehicleControls = MockVehicleControls(
                arrival = addVehicleSelector(
                    "모의 도착 상태", "도착", "도착 전"
                ),
                parking = addVehicleSelector(
                    "모의 주차 상태", "주차", "주행"
                ),
                doors = addVehicleSelector(
                    "모의 문 상태", "문 닫힘", "문 열림"
                ),
                replyInput = addVehicleSelector(
                    "모의 답장 입력 상태", "허용", "제한"
                )
            )

            addButton("새 모의 이동 시작") {
                dismissConfirmation()
                stopPlayback("새 모의 이동 시작")
                mockTripId += 1
                mockVehicleControls?.reset()
                updateStatus("새 이동 · 모의 차량 상태를 다시 설정해주세요")
            }

            addButton("새 모의 메시지 수신") {
                dismissConfirmation()
                stopPlayback("새 모의 메시지 수신")
                mockMessageNumber += 1
                listeningMode = null
                modeSelector.setSelection(0)
                mockVehicleControls?.reset()
                updateStatus("새 메시지 · 청취 모드와 차량 상태를 설정해주세요")
            }

            val listenButton = addButton("듣기 / 중단 후 다시 듣기") {
                requestListening()
            }

            addButton("멈춤") {
                if (player != null) {
                    stopPlayback("사용자가 중단 · 다시 듣기는 수동으로 선택해주세요")
                }
            }

            addText("이모지 답장 · 모의 테스트", 20f)
            val replyAvailability = addText("")
            val replySelection = addText("")

            val reactions = listOf(
                "❤️" to "하트",
                "👍" to "좋아요",
                "😂" to "웃겨요",
                "😡" to "화나요",
                "😢" to "슬퍼요"
            )

            val reactionButtons = reactions.map { (emoji, description) ->
                addButton(emoji) {
                    if (replyBlockingReason() == null) {
                        mockReplies[currentDelivery()] = emoji
                        refreshDetailUi?.invoke()
                    }
                }.apply {
                    textSize = 32f
                    contentDescription = description
                }
            }

            refreshDetailUi = {
                val delivery = currentDelivery()
                val deleted = delivery.messageId in deletedMessages
                val completed = delivery in completedDeliveries

                messageLabel.text = "모의 메시지 $mockMessageNumber"
                tripLabel.text = "모의 이동 $mockTripId"

                modeSelector.isEnabled =
                    delivery !in automaticallyHandledDeliveries &&
                        !deleted && !completed && player == null

                listenButton.isEnabled =
                    screenResumed && !deleted && !completed &&
                        player == null && confirmationDialog == null

                val replyReason = replyBlockingReason()
                reactionButtons.forEach { button ->
                    button.isEnabled = replyReason == null
                }

                replyAvailability.text = if (replyReason == null) {
                    "이모지를 선택할 수 있어요"
                } else {
                    "답장 보류 · $replyReason"
                }

                val selected = mockReplies[delivery]
                replySelection.text = if (selected == null) {
                    "모의 이모지 선택 없음"
                } else {
                    "모의 이모지 선택 · $selected"
                }
            }
        } else {
            addText("로컬 모의 테스트는 디버그 앱에서 사용할 수 있습니다.")
        }

        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                automaticallyHandledDeliveries.add(currentDelivery())
                detailReady = false
                dismissConfirmation()
                stopPlayback("목록 복귀로 중단")
                mockVehicleControls = null
                playbackStatus = null
                refreshDetailUi = null
                root.removeView(detailScreen)
                listScreen.visibility = View.VISIBLE
                remove()
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
        detailReady = true

        val status = when {
            currentMessageId() in deletedMessages -> "모의 메시지가 삭제됐습니다"
            currentDelivery() in completedDeliveries -> "테스트 음성 재생 완료"
            else -> "청취 모드와 모의 차량 상태를 설정해주세요"
        }
        updateStatus(status)
    }

    private fun playbackBlockingReason(): String? {
        if (!isDebugApp || !screenResumed || !detailReady) {
            return "활성화된 모의 테스트 화면에서만 재생할 수 있습니다"
        }

        if (currentMessageId() in deletedMessages) {
            return "삭제된 메시지입니다"
        }

        if (listeningMode == null) {
            return "모의 발신자의 청취 모드를 설정해주세요"
        }

        val controls = mockVehicleControls
            ?: return "모의 차량 상태를 확인할 수 없습니다"

        return controls.state().blockingReason()
    }

    private fun replyBlockingReason(): String? {
        if (!isDebugApp || !screenResumed || !detailReady) {
            return "활성화된 모의 테스트 화면에서 선택해주세요"
        }

        if (currentMessageId() in deletedMessages) {
            return "삭제된 메시지입니다"
        }

        val controls = mockVehicleControls
            ?: return "모의 차량 상태를 확인할 수 없습니다"

        return when {
            controls.state().parked != true -> "주차 상태를 확인해주세요"
            !controls.isReplyInputAllowed() -> "답장 입력이 허용돼야 합니다"
            currentDelivery() !in completedDeliveries ->
                "음성을 끝까지 들은 뒤 선택해주세요"
            else -> null
        }
    }

    private fun onMockStateChanged() {
        val reason = playbackBlockingReason()

        if (reason != null) {
            dismissConfirmation()
            if (player != null) {
                stopPlayback("재생 중단 · $reason")
            }
        }

        refreshDetailUi?.invoke()
        deliverAutomaticallyIfReady()
    }

    private fun deliverAutomaticallyIfReady() {
        val delivery = currentDelivery()

        if (playbackBlockingReason() != null ||
            player != null ||
            confirmationDialog != null ||
            delivery in completedDeliveries ||
            delivery in automaticallyHandledDeliveries
        ) {
            return
        }

        automaticallyHandledDeliveries.add(delivery)

        when (listeningMode) {
            ListeningMode.AUTOMATIC -> startTestPlayback()
            ListeningMode.CONFIRM_FIRST -> showListeningConfirmation()
            null -> return
        }
    }

    private fun requestListening() {
        if (player != null || confirmationDialog != null) return

        if (currentDelivery() in completedDeliveries) {
            updateStatus("같은 이동에서 이미 끝까지 들은 메시지입니다")
            return
        }

        val reason = playbackBlockingReason()
        if (reason != null) {
            updateStatus("재생 보류 · $reason")
            return
        }

        automaticallyHandledDeliveries.add(currentDelivery())

        when (listeningMode) {
            ListeningMode.AUTOMATIC -> startTestPlayback()
            ListeningMode.CONFIRM_FIRST -> showListeningConfirmation()
            null -> return
        }
    }

    private fun showListeningConfirmation() {
        if (confirmationDialog != null || playbackBlockingReason() != null) return

        val delivery = currentDelivery()

        val dialog = AlertDialog.Builder(this)
            .setTitle("메시지를 들을까요?")
            .setMessage(
                "발신자가 재생 전 확인을 선택했습니다.\n\n" +
                    "차량 스피커로 함께 들을 수 있는 상황인지 확인해주세요."
            )
            .setPositiveButton("듣기") { _, _ ->
                if (delivery == currentDelivery()) {
                    startTestPlayback()
                }
            }
            .setNegativeButton("삭제") { _, _ ->
                if (delivery == currentDelivery() &&
                    playbackBlockingReason() == null
                ) {
                    deletedMessages.add(delivery.messageId)
                    updateStatus("모의 메시지 삭제 완료")
                }
            }
            .create()

        dialog.setOnCancelListener {
            updateStatus("재생 보류 · 듣기를 다시 선택할 수 있습니다")
        }

        dialog.setOnDismissListener {
            if (confirmationDialog === dialog) {
                confirmationDialog = null
                refreshDetailUi?.invoke()
            }
        }

        confirmationDialog = dialog
        dialog.show()
        refreshDetailUi?.invoke()
    }

    private fun dismissConfirmation() {
        val dialog = confirmationDialog
        confirmationDialog = null
        dialog?.dismiss()
    }

    @SuppressLint("DiscouragedApi")
    private fun startTestPlayback() {
        if (player != null) return

        val delivery = currentDelivery()
        if (delivery in completedDeliveries) return

        val reason = playbackBlockingReason()
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

            val sampleId = resources.getIdentifier(
                "chamamothanmal_test",
                "raw",
                packageName
            )

            if (sampleId == 0) {
                stopPlayback(
                    "로컬 시험 음성이 없습니다. 서버 메시지 연결 화면을 사용하세요"
                )
                return
            }

            val newPlayer = MediaPlayer()
            player = newPlayer
            newPlayer.setAudioAttributes(audioAttributes)

            resources.openRawResourceFd(sampleId).use { descriptor ->
                newPlayer.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length
                )
            }

            newPlayer.setOnPreparedListener { preparedPlayer ->
                if (player === preparedPlayer) {
                    val blockingReason = playbackBlockingReason()

                    if (delivery == currentDelivery() &&
                        blockingReason == null &&
                        hasAudioFocus
                    ) {
                        try {
                            preparedPlayer.start()
                            updateStatus("테스트 음성 재생 중")
                        } catch (error: Exception) {
                            Log.e("chamamothanmal_ui", "PLAY_START_FAILED", error)
                            stopPlayback("재생 시작 실패")
                        }
                    } else {
                        stopPlayback("재생 중단 · 조건이 변경됐습니다")
                    }
                }
            }

            newPlayer.setOnCompletionListener { completedPlayer ->
                if (player === completedPlayer) {
                    if (delivery == currentDelivery() &&
                        playbackBlockingReason() == null &&
                        hasAudioFocus
                    ) {
                        completedDeliveries.add(delivery)
                        stopPlayback("테스트 음성 재생 완료")
                    } else {
                        stopPlayback("재생 중단 · 완료 조건을 확인할 수 없습니다")
                    }
                }
            }

            newPlayer.setOnErrorListener { failedPlayer, what, extra ->
                if (player === failedPlayer) {
                    Log.e(
                        "chamamothanmal_ui",
                        "PLAYER_ERROR what=$what extra=$extra"
                    )
                    stopPlayback("음성 파일 재생 오류")
                }
                true
            }

            updateStatus("음성 파일 준비 중")
            newPlayer.prepareAsync()
        } catch (error: Exception) {
            Log.e("chamamothanmal_ui", "PLAYBACK_FAILED", error)
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
        playbackStatus?.text = "재생 상태 · $message"
        Log.d("chamamothanmal_ui", message)
        refreshDetailUi?.invoke()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
