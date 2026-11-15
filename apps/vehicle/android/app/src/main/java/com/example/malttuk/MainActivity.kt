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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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

    private val messageArchive by lazy {
        MockMessageArchive(applicationContext)
    }

    private var player: MediaPlayer? = null
    private var hasAudioFocus = false
    private var activeArchiveEntry: MockMessageArchive.Entry? = null

    private var screenResumed = false
    private var detailReady = false

    private var mockMessageNumber = 1
    private var mockMessageId = UUID.randomUUID().toString()
    private var mockTripId = 1
    private var listeningMode: ListeningMode? = null

    private val completedDeliveries = mutableSetOf<DeliveryKey>()
    private val automaticallyHandledDeliveries = mutableSetOf<DeliveryKey>()
    private val deletedMessages = mutableSetOf<String>()
    private val mockReplies = mutableMapOf<DeliveryKey, String>()

    private var mockVehicleControls: MockVehicleControls? = null
    private var confirmationDialog: AlertDialog? = null

    private var archiveDialog: AlertDialog? = null
    private var archiveDeleteDialog: AlertDialog? = null
    private var archiveDeleteInProgress = false

    private var archiveRequested = false
    private var archiveGeneration = 0
    private var archiveStatus: TextView? = null
    private var refreshArchiveUi: (() -> Unit)? = null

    private var playbackStatus: TextView? = null
    private var refreshDetailUi: (() -> Unit)? = null

    private val archiveExpiryTask = Runnable {
        val entry = activeArchiveEntry

        if (entry != null && entry.isExpired(System.currentTimeMillis())) {
            stopPlayback("보관 기간이 지나 재생을 중단했습니다")
        }

        if (screenResumed && detailReady && archiveDialog != null) {
            showMockArchive()
        }
    }

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val isDebugApp: Boolean
        get() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun currentMessageId(): String = mockMessageId

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

        Log.d("chamamothanmal_ui", "SCREEN_READY_ARCHIVE_PLAYBACK")
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
        dismissArchive()

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
        dismissArchive()
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

        addText(content, "차마못한말 · 청취 모드 테스트", 28f)
        addText(content, "발표회에 도착하면 들어요", 24f)
        addText(content, "목적지 · 테스트 발표회장")

        addText(
            content,
            "개발용 모의 메시지입니다. 차량 상태와 발신자의 청취 모드를 " +
                "이 화면에서 설정합니다. 보관함과 이모지 선택은 로컬에 저장됩니다."
        )

        playbackStatus = addText(content, "재생 상태 · 대기", 20f)

        if (isDebugApp) {
            addButton(content, "모의 보관함") {
                showMockArchive()
            }

            val messageLabel = addText(content, "")
            val tripLabel = addText(content, "")

            val modeSelector = addSelector(
                content,
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

            mockVehicleControls = MockVehicleControls(
                arrival = addVehicleSelector(
                    content, "모의 도착 상태", "도착", "도착 전"
                ),
                parking = addVehicleSelector(
                    content, "모의 주차 상태", "주차", "주행"
                ),
                doors = addVehicleSelector(
                    content, "모의 문 상태", "문 닫힘", "문 열림"
                ),
                replyInput = addVehicleSelector(
                    content, "모의 답장 입력 상태", "허용", "제한"
                )
            )

            addButton(content, "새 모의 이동 시작") {
                dismissConfirmation()
                dismissArchive()
                stopPlayback("새 모의 이동 시작")

                mockTripId += 1
                mockVehicleControls?.reset()

                updateStatus("새 이동 · 모의 차량 상태를 다시 설정해주세요")
            }

            addButton(content, "새 모의 메시지 수신") {
                dismissConfirmation()
                dismissArchive()
                stopPlayback("새 모의 메시지 수신")

                mockMessageNumber += 1
                mockMessageId = UUID.randomUUID().toString()
                listeningMode = null

                modeSelector.setSelection(0)
                mockVehicleControls?.reset()

                updateStatus("새 메시지 · 청취 모드와 차량 상태를 설정해주세요")
            }

            val listenButton = addButton(
                content,
                "듣기 / 중단 후 다시 듣기"
            ) {
                requestListening()
            }

            addButton(content, "멈춤") {
                if (player != null) {
                    stopPlayback("사용자가 중단 · 다시 듣기는 수동으로 선택해주세요")
                }
            }

            addText(content, "이모지 답장 · 모의 테스트", 20f)

            val replyAvailability = addText(content, "")
            val replySelection = addText(content, "")

            val reactions = listOf(
                "❤️" to "하트",
                "👍" to "좋아요",
                "😂" to "웃겨요",
                "😡" to "화나요",
                "😢" to "슬퍼요"
            )

            val reactionButtons = reactions.map { (emoji, description) ->
                addButton(content, emoji) {
                    if (replyBlockingReason() == null) {
                        val delivery = currentDelivery()
                        mockReplies[delivery] = emoji
                        refreshDetailUi?.invoke()

                        messageArchive.setReaction(
                            messageId = delivery.messageId,
                            reaction = emoji
                        ) { result ->
                            if (result.isFailure &&
                                !isDestroyed &&
                                !isFinishing &&
                                delivery == currentDelivery()
                            ) {
                                Log.e(
                                    "chamamothanmal_ui",
                                    "ARCHIVE_REACTION_FAILED",
                                    result.exceptionOrNull()
                                )
                                updateStatus("이모지 선택됨 · 보관함 반영 실패")
                            }
                        }
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
                        player == null &&
                        confirmationDialog == null &&
                        !archiveRequested

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
            addText(
                content,
                "로컬 모의 테스트는 디버그 앱에서 사용할 수 있습니다."
            )
        }

        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                automaticallyHandledDeliveries.add(currentDelivery())
                detailReady = false

                dismissConfirmation()
                dismissArchive()
                stopPlayback("목록 복귀로 중단")

                mockVehicleControls = null
                playbackStatus = null
                refreshDetailUi = null

                root.removeView(detailScreen)
                listScreen.visibility = View.VISIBLE
                remove()
            }
        }

        addButton(content, "목록으로 돌아가기") {
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

        updateStatus(
            when {
                currentMessageId() in deletedMessages ->
                    "모의 메시지가 삭제됐습니다"

                currentDelivery() in completedDeliveries ->
                    "테스트 음성 재생 완료"

                else ->
                    "청취 모드와 모의 차량 상태를 설정해주세요"
            }
        )
    }

    private fun addText(
        parent: LinearLayout,
        value: String,
        size: Float = 18f
    ): TextView {
        val textView = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.parseColor("#182230"))
        }

        parent.addView(
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

    private fun addButton(
        parent: LinearLayout,
        label: String,
        action: () -> Unit
    ): Button {
        val button = Button(this).apply {
            text = label
            textSize = 20f
            minimumHeight = dp(64)
            setOnClickListener { action() }
        }

        parent.addView(
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

    private fun addSelector(
        parent: LinearLayout,
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

        addText(parent, label).labelFor = selector.id

        parent.addView(
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

    private fun addVehicleSelector(
        parent: LinearLayout,
        label: String,
        allowed: String,
        blocked: String
    ): Spinner = addSelector(
        parent,
        label,
        listOf("미확인", allowed, blocked)
    ) {
        onMockStateChanged()
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
            controls.state().parked != true ->
                "주차 상태를 확인해주세요"

            !controls.isReplyInputAllowed() ->
                "답장 입력이 허용돼야 합니다"

            currentDelivery() !in completedDeliveries ->
                "음성을 끝까지 들은 뒤 선택해주세요"

            else -> null
        }
    }

    private fun archivePlaybackBlockingReason(
        entry: MockMessageArchive.Entry
    ): String? {
        if (!isDebugApp || !screenResumed ||
            !detailReady || !archiveRequested
        ) {
            return "활성화된 모의 보관함에서 재생해주세요"
        }

        if (archiveDeleteInProgress) {
            return "보관 메시지를 삭제하고 있습니다"
        }

        if (archiveDeleteDialog != null) {
            return "삭제 확인창을 먼저 닫아주세요"
        }

        if (entry.messageId in deletedMessages) {
            return "삭제된 메시지입니다"
        }

        if (entry.isExpired(System.currentTimeMillis())) {
            return "보관 기간이 지난 메시지입니다"
        }

        val controls = mockVehicleControls
            ?: return "모의 차량 상태를 확인할 수 없습니다"

        return when {
            controls.state().parked != true ->
                "주차 상태를 확인해주세요"

            controls.state().doorsClosed != true ->
                "문 닫힘 상태를 확인해주세요"

            !controls.isReplyInputAllowed() ->
                "화면 입력이 허용돼야 합니다"

            else -> null
        }
    }

    private fun activePlaybackBlockingReason(): String? {
        val archived = activeArchiveEntry

        return if (archived == null) {
            playbackBlockingReason()
        } else {
            archivePlaybackBlockingReason(archived)
        }
    }

    private fun onMockStateChanged() {
        if (!archiveActionsAllowed()) {
            dismissArchiveDeleteConfirmation()
        }

        val reason = activePlaybackBlockingReason()

        if (reason != null) {
            dismissConfirmation()

            if (player != null) {
                stopPlayback("재생 중단 · $reason")
            }
        }

        refreshDetailUi?.invoke()
        refreshArchiveUi?.invoke()
        deliverAutomaticallyIfReady()
    }

    private fun deliverAutomaticallyIfReady() {
        val delivery = currentDelivery()

        if (playbackBlockingReason() != null ||
            player != null ||
            confirmationDialog != null ||
            archiveRequested ||
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
        if (player != null ||
            confirmationDialog != null ||
            archiveRequested
        ) {
            return
        }

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
        if (confirmationDialog != null ||
            playbackBlockingReason() != null ||
            archiveRequested
        ) {
            return
        }

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

    private fun createPlayer(): MediaPlayer? {
        val result = audioManager.requestAudioFocus(focusRequest)

        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            updateStatus("재생 보류 · 오디오 포커스를 얻지 못했습니다")
            return null
        }

        hasAudioFocus = true

        val newPlayer = MediaPlayer()
        player = newPlayer
        newPlayer.setAudioAttributes(audioAttributes)

        return newPlayer
    }

    private fun preparePlayer(
        newPlayer: MediaPlayer,
        onCompleted: () -> Unit
    ) {
        newPlayer.setOnPreparedListener { preparedPlayer ->
            if (player === preparedPlayer) {
                val reason = activePlaybackBlockingReason()

                if (reason == null && hasAudioFocus) {
                    try {
                        preparedPlayer.start()

                        updateStatus(
                            if (activeArchiveEntry == null) {
                                "테스트 음성 재생 중"
                            } else {
                                "보관 음성 재생 중"
                            }
                        )
                    } catch (error: Exception) {
                        Log.e(
                            "chamamothanmal_ui",
                            "PLAY_START_FAILED",
                            error
                        )
                        stopPlayback("재생 시작 실패")
                    }
                } else {
                    stopPlayback(
                        "재생 중단 · ${reason ?: "오디오 포커스를 잃었습니다"}"
                    )
                }
            }
        }

        newPlayer.setOnCompletionListener { completedPlayer ->
            if (player === completedPlayer) {
                val reason = activePlaybackBlockingReason()

                if (reason == null && hasAudioFocus) {
                    onCompleted()
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
    }

    @SuppressLint("DiscouragedApi")
    private fun startTestPlayback() {
        if (player != null || archiveRequested) return

        val delivery = currentDelivery()
        if (delivery in completedDeliveries) return

        val reason = playbackBlockingReason()
        if (reason != null) {
            updateStatus("재생 보류 · $reason")
            return
        }

        try {
            val sampleId = resources.getIdentifier(
                "chamamothanmal_test",
                "raw",
                packageName
            )

            if (sampleId == 0) {
                updateStatus(
                    "로컬 시험 음성이 없습니다. 서버 메시지 연결 화면을 사용하세요"
                )
                return
            }

            activeArchiveEntry = null
            val newPlayer = createPlayer() ?: return
            val messageTitle =
                "발표회에 도착하면 들어요 · 모의 $mockMessageNumber"

            resources.openRawResourceFd(sampleId).use { descriptor ->
                newPlayer.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length
                )
            }

            preparePlayer(newPlayer) {
                if (delivery == currentDelivery()) {
                    completedDeliveries.add(delivery)
                    stopPlayback("테스트 음성 재생 완료 · 보관 중")

                    messageArchive.saveCompleted(
                        messageId = delivery.messageId,
                        title = messageTitle,
                        rawResourceId = sampleId
                    ) { saved ->
                        if (!isDestroyed &&
                            !isFinishing &&
                            delivery == currentDelivery() &&
                            player == null
                        ) {
                            if (saved.isSuccess) {
                                updateStatus(
                                    "테스트 음성 재생 완료 · 보관함 저장 완료"
                                )
                            } else {
                                Log.e(
                                    "chamamothanmal_ui",
                                    "ARCHIVE_SAVE_FAILED",
                                    saved.exceptionOrNull()
                                )
                                updateStatus("재생 완료 · 보관함 저장 실패")
                            }
                        }
                    }
                } else {
                    stopPlayback("재생 중단 · 메시지가 변경됐습니다")
                }
            }
        } catch (error: Exception) {
            Log.e("chamamothanmal_ui", "PLAYBACK_FAILED", error)
            stopPlayback("음성 파일 준비 실패")
        }
    }

    private fun startArchivedPlayback(entry: MockMessageArchive.Entry) {
        if (player != null) return

        val reason = archivePlaybackBlockingReason(entry)
        if (reason != null) {
            updateStatus("보관 음성 재생 보류 · $reason")
            return
        }

        try {
            val newPlayer = createPlayer() ?: return
            activeArchiveEntry = entry
            updateStatus("보관 음성 파일 준비 중")

            messageArchive.openAudio(entry.messageId) { opened ->
                val descriptor = opened.getOrNull()

                if (descriptor == null) {
                    if (player === newPlayer) {
                        Log.e(
                            "chamamothanmal_ui",
                            "ARCHIVE_OPEN_FAILED",
                            opened.exceptionOrNull()
                        )
                        stopPlayback("보관 음성 파일을 열지 못했습니다")
                    }
                    return@openAudio
                }

                descriptor.use {
                    if (player === newPlayer) {
                        val currentReason = archivePlaybackBlockingReason(entry)

                        if (currentReason != null || !hasAudioFocus) {
                            stopPlayback("보관 음성 재생 조건이 변경됐습니다")
                        } else {
                            try {
                                newPlayer.setDataSource(it.fileDescriptor)

                                preparePlayer(newPlayer) {
                                    stopPlayback("보관 음성 재생 완료")
                                }
                            } catch (error: Exception) {
                                Log.e(
                                    "chamamothanmal_ui",
                                    "ARCHIVE_PLAYBACK_FAILED",
                                    error
                                )
                                stopPlayback("보관 음성 준비 실패")
                            }
                        }
                    }
                }
            }
        } catch (error: Exception) {
            Log.e("chamamothanmal_ui", "ARCHIVE_START_FAILED", error)
            stopPlayback("보관 음성 재생 시작 실패")
        }
    }

    private fun stopPlayback(reason: String) {
        val oldPlayer = player
        player = null
        activeArchiveEntry = null

        oldPlayer?.release()

        if (hasAudioFocus) {
            hasAudioFocus = false
            audioManager.abandonAudioFocusRequest(focusRequest)
        }

        updateStatus(reason)
    }

    private fun archiveActionsAllowed(): Boolean {
        val controls = mockVehicleControls ?: return false

        return isDebugApp &&
            screenResumed &&
            detailReady &&
            archiveRequested &&
            archiveDialog?.isShowing == true &&
            !archiveDeleteInProgress &&
            controls.state().parked == true &&
            controls.isReplyInputAllowed()
    }

    private fun showArchiveDeleteConfirmation(
        entry: MockMessageArchive.Entry
    ) {
        if (archiveDeleteDialog != null ||
            !archiveActionsAllowed() ||
            player != null
        ) {
            return
        }

        val generation = archiveGeneration
        val parentDialog = archiveDialog ?: return

        if (entry.isExpired(System.currentTimeMillis())) {
            updateStatus("보관 기간이 지난 메시지입니다")
            showMockArchive()
            return
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("메시지를 영구 삭제할까요?")
            .setMessage(
                "${entry.title}\n\n" +
                    "삭제한 음성은 복구할 수 없습니다."
            )
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ ->
                val canDelete =
                    generation == archiveGeneration &&
                        archiveDialog === parentDialog &&
                        parentDialog.isShowing &&
                        archiveActionsAllowed() &&
                        player == null

                if (!canDelete) {
                    updateStatus(
                        "삭제 보류 · 주차 상태와 화면 입력 허용 여부를 확인해주세요"
                    )
                } else if (entry.isExpired(System.currentTimeMillis())) {
                    updateStatus("보관 기간이 지난 메시지입니다")
                    showMockArchive()
                } else {
                    archiveDeleteInProgress = true
                    updateStatus("보관 메시지를 삭제하고 있습니다")

                    messageArchive.delete(entry.messageId) { deleted ->
                        archiveDeleteInProgress = false

                        if (deleted.isSuccess) {
                            deletedMessages.add(entry.messageId)
                        }

                        onArchiveActionFinished(
                            deleted,
                            "보관 메시지 삭제 완료"
                        )
                    }
                }
            }
            .create()

        dialog.setOnDismissListener {
            if (archiveDeleteDialog === dialog) {
                archiveDeleteDialog = null
                refreshArchiveUi?.invoke()
            }
        }

        archiveDeleteDialog = dialog
        dialog.show()
        refreshArchiveUi?.invoke()
    }

    private fun dismissArchiveDeleteConfirmation() {
        val dialog = archiveDeleteDialog
        archiveDeleteDialog = null
        dialog?.dismiss()
        refreshArchiveUi?.invoke()
    }

    private fun showMockArchive() {
        if (!isDebugApp || !screenResumed || !detailReady) return

        automaticallyHandledDeliveries.add(currentDelivery())
        dismissConfirmation()
        dismissArchiveDeleteConfirmation()

        if (player != null) {
            stopPlayback("보관함 목록 갱신으로 재생 중단")
        }

        mainHandler.removeCallbacks(archiveExpiryTask)
        archiveRequested = true
        archiveGeneration += 1
        val generation = archiveGeneration

        refreshDetailUi?.invoke()

        messageArchive.listActive { result ->
            if (isDestroyed ||
                isFinishing ||
                !screenResumed ||
                !detailReady ||
                generation != archiveGeneration
            ) {
                return@listActive
            }

            val entries = result.getOrNull()

            if (entries == null) {
                dismissArchive()

                Log.e(
                    "chamamothanmal_ui",
                    "ARCHIVE_LOAD_FAILED",
                    result.exceptionOrNull()
                )
                updateStatus("보관함을 불러오지 못했습니다")
                return@listActive
            }

            archiveDialog?.dismiss()

            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(16), dp(24), dp(16))
            }

            val scroll = ScrollView(this).apply {
                addView(content)
            }

            addText(
                content,
                "기본 보관 기간은 완료 후 7일입니다.\n" +
                    "다시 듣기는 모의 주차·문 닫힘·입력 허용 상태에서 가능합니다."
            )

            archiveStatus = addText(content, "보관함 · 대기", 20f)

            val stopButton = addButton(content, "멈춤") {
                if (activeArchiveEntry != null) {
                    stopPlayback("사용자가 보관 음성 재생을 중단했습니다")
                }
            }

            if (entries.isEmpty()) {
                addText(content, "보관된 메시지가 없습니다.")
            }

            val dateFormat = SimpleDateFormat(
                "yyyy-MM-dd HH:mm",
                Locale.KOREA
            )

            val replayButtons =
                mutableListOf<Pair<MockMessageArchive.Entry, Button>>()

            val keepButtons =
                mutableListOf<Pair<MockMessageArchive.Entry, Button>>()

            val deleteButtons = mutableListOf<Button>()

            for (entry in entries) {
                val completedAt = dateFormat.format(Date(entry.completedAt))

                val retention = if (entry.permanent) {
                    "영구 보관"
                } else {
                    "만료 예정 · ${
                        dateFormat.format(Date(entry.expiresAt()))
                    }"
                }

                addText(
                    content,
                    "${entry.title}\n" +
                        "완료 · $completedAt\n" +
                        "$retention\n" +
                        "이모지 · ${entry.reaction ?: "선택 없음"}"
                )

                val replayButton = addButton(content, "다시 듣기") {
                    startArchivedPlayback(entry)
                }
                replayButtons.add(entry to replayButton)

                val keepButton = addButton(
                    content,
                    if (entry.permanent) "영구 보관 중" else "영구 보관"
                ) {
                    if (archiveActionsAllowed() &&
                        player == null &&
                        archiveDeleteDialog == null
                    ) {
                        messageArchive.keepPermanently(entry.messageId) { saved ->
                            onArchiveActionFinished(saved, "영구 보관 완료")
                        }
                    }
                }
                keepButtons.add(entry to keepButton)

                val deleteButton = addButton(content, "영구 삭제") {
                    showArchiveDeleteConfirmation(entry)
                }
                deleteButtons.add(deleteButton)
            }

            refreshArchiveUi = {
                stopButton.isEnabled =
                    player != null && activeArchiveEntry != null

                replayButtons.forEach { (entry, button) ->
                    button.isEnabled =
                        player == null &&
                            archivePlaybackBlockingReason(entry) == null
                }

                keepButtons.forEach { (entry, button) ->
                    button.isEnabled =
                        player == null &&
                            archiveActionsAllowed() &&
                            archiveDeleteDialog == null &&
                            !entry.permanent &&
                            !entry.isExpired(System.currentTimeMillis())
                }

                deleteButtons.forEach { button ->
                    button.isEnabled =
                        player == null &&
                            archiveActionsAllowed() &&
                            archiveDeleteDialog == null
                }
            }

            val dialog = AlertDialog.Builder(this)
                .setTitle("모의 보관함")
                .setView(scroll)
                .setPositiveButton("닫기", null)
                .create()

            dialog.setOnDismissListener {
                if (archiveDialog === dialog &&
                    generation == archiveGeneration
                ) {
                    archiveDialog = null
                    archiveRequested = false

                    dismissArchiveDeleteConfirmation()

                    archiveStatus = null
                    refreshArchiveUi = null
                    mainHandler.removeCallbacks(archiveExpiryTask)

                    if (activeArchiveEntry != null) {
                        stopPlayback("보관함 닫기로 재생 중단")
                    }

                    refreshDetailUi?.invoke()
                }
            }

            archiveDialog = dialog
            dialog.show()
            refreshArchiveUi?.invoke()
            scheduleArchiveExpiry(entries)
        }
    }

    private fun scheduleArchiveExpiry(
        entries: List<MockMessageArchive.Entry>
    ) {
        mainHandler.removeCallbacks(archiveExpiryTask)

        val nextExpiry = entries
            .filter { !it.permanent }
            .minOfOrNull { it.expiresAt() }
            ?: return

        val delay = maxOf(
            1L,
            nextExpiry - System.currentTimeMillis() + 1L
        )

        mainHandler.postDelayed(archiveExpiryTask, delay)
    }

    private fun onArchiveActionFinished(
        result: Result<Unit>,
        successMessage: String
    ) {
        if (isDestroyed || isFinishing) return

        if (result.isSuccess) {
            updateStatus(successMessage)
        } else {
            Log.e(
                "chamamothanmal_ui",
                "ARCHIVE_ACTION_FAILED",
                result.exceptionOrNull()
            )
            updateStatus("보관함 변경 실패")
        }

        if (screenResumed &&
            detailReady &&
            archiveRequested &&
            archiveDialog != null
        ) {
            showMockArchive()
        }
    }

    private fun dismissArchive() {
        archiveGeneration += 1
        archiveRequested = false
        mainHandler.removeCallbacks(archiveExpiryTask)

        dismissArchiveDeleteConfirmation()

        val dialog = archiveDialog
        archiveDialog = null
        archiveStatus = null
        refreshArchiveUi = null
        dialog?.dismiss()

        if (activeArchiveEntry != null) {
            stopPlayback("보관함 이탈로 재생 중단")
        }
    }

    private fun updateStatus(message: String) {
        playbackStatus?.text = "재생 상태 · $message"
        archiveStatus?.text = "재생 상태 · $message"
        Log.d("chamamothanmal_ui", message)

        refreshDetailUi?.invoke()
        refreshArchiveUi?.invoke()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
