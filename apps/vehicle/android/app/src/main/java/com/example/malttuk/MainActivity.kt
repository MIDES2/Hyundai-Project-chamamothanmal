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
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
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

    private var mockCondition = false
    private var mockCheckBox: CheckBox? = null
    private var playbackStatus: TextView? = null

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val isDebugApp: Boolean
        get() = (applicationInfo.flags and
            ApplicationInfo.FLAG_DEBUGGABLE) != 0

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
                            stopPlayback("다른 오디오 우선으로 중단")
                        }

                        AudioManager.AUDIOFOCUS_GAIN -> {
                            // 자동으로 다시 재생하지 않는다.
                        }
                    }
                },
                Handler(Looper.getMainLooper())
            )
            .build()

        val mainView = findViewById<View>(R.id.main)

        ViewCompat.setOnApplyWindowInsetsListener(mainView) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            view.setPadding(
                bars.left,
                bars.top,
                bars.right,
                bars.bottom
            )

            insets
        }

        findViewById<View>(R.id.messageCard).setOnClickListener {
            showTestMessageDetails()
        }

        Log.d("ChamamothanmalUi", "SCREEN_READY_AUDIO_V1")
    }

    override fun onResume() {
        super.onResume()
        screenResumed = true
    }

    override fun onPause() {
        screenResumed = false

        stopPlayback("화면 이탈로 중단")

        // 앱으로 돌아와도 모의 조건을 다시 선택해야 한다.
        mockCondition = false
        mockCheckBox?.isChecked = false

        super.onPause()
    }

    override fun onDestroy() {
        stopPlayback("재생 종료")
        playbackStatus = null
        mockCheckBox = null

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

        fun addButton(label: String, action: () -> Unit) {
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
        }

        addText("차마못한말 · 테스트 상세", 28f)
        addText("발표회에 도착하면 들어요", 24f)
        addText("목적지: 테스트 발표회장", 20f)

        addText(
            "실제 차량 상태는 아직 미확인입니다.\n" +
                "아래 기능은 에뮬레이터에서 파일 재생을 확인하는 모의 테스트입니다.",
            18f
        )

        playbackStatus = addText("재생 상태: 대기", 20f)

        mockCondition = false

        val conditionCheckBox = CheckBox(this).apply {
            text = "모의 도착·주차 조건 충족 (실제 신호 아님)"
            textSize = 18f
            minHeight = dp(64)
            isEnabled = isDebugApp

            setOnCheckedChangeListener { _, checked ->
                mockCondition = checked && isDebugApp

                if (!mockCondition) {
                    stopPlayback("모의 조건 해제 · 재생 보류")
                } else {
                    updateStatus("모의 조건 선택됨 · 재생 버튼을 눌러주세요")
                }
            }
        }

        mockCheckBox = conditionCheckBox
        content.addView(conditionCheckBox)

        addButton("테스트 음성 재생") {
            startTestPlayback()
        }

        addButton("멈춤") {
            stopPlayback("사용자가 중단")
        }

        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                stopPlayback("목록 복귀로 중단")

                mockCondition = false
                mockCheckBox = null
                playbackStatus = null

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

    private fun startTestPlayback() {
        if (!isDebugApp || !screenResumed || !mockCondition) {
            updateStatus("재생 보류 · 모의 조건을 먼저 선택해주세요")
            return
        }

        // 준비 중이거나 재생 중이면 새 플레이어를 만들지 않는다.
        if (player != null) {
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

            resources.openRawResourceFd(
                R.raw.chamamothanmal_test
            ).use { descriptor ->
                newPlayer.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length
                )
            }

            newPlayer.setOnPreparedListener { preparedPlayer ->
                if (player === preparedPlayer) {
                    if (
                        screenResumed &&
                        mockCondition &&
                        hasAudioFocus
                    ) {
                        try {
                            preparedPlayer.start()
                            updateStatus("테스트 음성 재생 중")
                        } catch (error: Exception) {
                            Log.e(
                                "ChamamothanmalUi",
                                "PLAY_START_FAILED",
                                error
                            )
                            stopPlayback("재생 시작 실패")
                        }
                    } else {
                        stopPlayback("재생 조건 변경으로 보류")
                    }
                }
            }

            newPlayer.setOnCompletionListener { completedPlayer ->
                if (player === completedPlayer) {
                    stopPlayback("테스트 음성 재생 완료")
                }
            }

            newPlayer.setOnErrorListener { failedPlayer, what, extra ->
                if (player === failedPlayer) {
                    Log.e(
                        "ChamamothanmalUi",
                        "PLAYER_ERROR what=$what extra=$extra"
                    )
                    stopPlayback("음성 파일 재생 오류")
                }
                true
            }

            updateStatus("음성 파일 준비 중")
            newPlayer.prepareAsync()

        } catch (error: Exception) {
            Log.e(
                "ChamamothanmalUi",
                "PLAYBACK_FAILED",
                error
            )
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
