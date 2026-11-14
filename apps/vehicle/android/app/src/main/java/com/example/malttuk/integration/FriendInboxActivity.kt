package com.example.malttuk.integration

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.*
import android.os.*
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.malttuk.MainActivity
import io.chamamothanmal.client.FriendMessageClient
import io.chamamothanmal.client.VoiceMessageClient
import io.chamamothanmal.vehiclestate.*
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.concurrent.Executors

/** Live friend messages. Only the explicit debug checkbox can supply mock vehicle conditions. */
class FriendInboxActivity : AppCompatActivity() {
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private lateinit var monitor: VehicleStateMonitor
    private var ux: CarUxGuard?=null
    private lateinit var content: LinearLayout
    private lateinit var legacyScreen: ScrollView
    private lateinit var webScreen: VehicleWebScreen
    private var loadedMessages=JSONArray()
    private var arrivalOverride="SDK"
    private var volume=70
    private var lastPositionMs=0
    private var lastDurationMs=0
    private var htmlMode=true
    private lateinit var status: TextView
    private lateinit var state: TextView
    private lateinit var choices: LinearLayout
    private lateinit var server: EditText
    private lateinit var login: EditText
    private lateinit var password: EditText
    private lateinit var mock: CheckBox
    private lateinit var confirmAlways: CheckBox
    private val guarded=mutableListOf<View>()
    private var client: FriendMessageClient?=null
    private var selected: JSONObject?=null
    private var archived=false
    private var resumed=false
    private var destroyed=false
    private var busy=false
    private var applyingMock=false
    @Volatile private var generation=0
    private var player: MediaPlayer?=null
    private var file: File?=null
    private var pendingCompletion: String?=null
    private var lastCompleted: String?=null
    private val attempted=mutableSetOf<String>()
    private var confirmation: AlertDialog?=null
    private lateinit var audio: AudioManager
    private lateinit var focus: AudioFocusRequest
    private var hasFocus=false
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val debug get()=applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE!=0
    private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { monitor.start() }
    private val ticker=object:Runnable { override fun run() { if(resumed) { updateState();main.postDelayed(this,1000) } } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monitor=VehicleStateMonitor(this)
        ux=try { Class.forName("android.car.Car");CarUxGuard(this) { if(::state.isInitialized) updateState() } } catch(e:Exception) { null } catch(e:LinkageError) { null }
        audio=getSystemService(AudioManager::class.java)
        focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false).setWillPauseWhenDucked(true).setOnAudioFocusChangeListener({ change ->
                if(change<=AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK || change==AudioManager.AUDIOFOCUS_LOSS || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop("다른 오디오 우선 · 중단, 자동 재개 안 함")
            },main).build()
        content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(28,28,28,28) }
        legacyScreen=ScrollView(this).apply { addView(content) }
        label("차마못한말 · 받은 마음",24f)
        label("웹에서 만든 운전자 계정으로 로그인하세요. 로그인 정보는 저장하지 않습니다. 차량 스피커의 소리는 동승자에게도 들릴 수 있어요.")
        state=label("차량 상태 수신 대기");status=label("로그인 필요")
        mock=CheckBox(this).apply { text="디버그: 모의 도착·P단·문 닫힘·UX (실제 신호 아님)";visibility=if(debug) View.VISIBLE else View.GONE }
        content.addView(mock)
        mock.setOnCheckedChangeListener { _,_->stop("시험 조건 변경");applyMock();updateState() }
        server=field("서버 주소","http://10.0.2.2:8000")
        login=field("등록한 운전자 아이디 또는 전화번호")
        password=field("비밀번호",secret=true)
        button("로그인") { signIn() }
        confirmAlways=CheckBox(this).apply { text="운전자 설정: 항상 확인 후 재생" }
        content.addView(confirmAlways);guarded+=confirmAlways
        confirmAlways.setOnCheckedChangeListener { _,_->stop("재생 방식 변경 · 수동으로 다시 시작하세요") }
        button("받은 메시지 새로고침") { load(false) }
        button("보관함 열기") { load(true) }
        choices=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL };content.addView(choices)
        button("듣기 / 중단한 음성 다시 듣기") { startPlayback() }
        button("멈춤") { stop("사용자 중단 · 완료로 기록하지 않음") }
        button("완료 보고 재시도") { pendingCompletion?.let { reportCompletion(it) } ?: show("대기 중인 완료 보고 없음") }
        for(emoji in listOf("👍","🥹","♥️")) button("답장 $emoji") { react(emoji) }
        button("선택한 메시지 삭제") { confirmDelete() }
        button("로그아웃 · 임시 음성 지우기") { logout() }
        if(debug) {
            button("디버그: 문 열림",false) { if(mock.isChecked) monitor.setMockDoor(DoorStatus.OPEN) }
            button("디버그: 출발",false) { if(mock.isChecked) monitor.setMockGear(GearPosition.NOT_PARK) }
            button("디버그: 모의 조건 복구",false) { if(mock.isChecked) applyMock() }
            button("기존 로컬 모의 화면",false) { startActivity(Intent(this,MainActivity::class.java)) }
        }
        button("차량 디자인 화면으로 돌아가기",false) { htmlMode=true;legacyScreen.visibility=View.GONE;webScreen.view.visibility=View.VISIBLE;publishState() }
        webScreen=VehicleWebScreen(this,::webCommand,::publishState)
        setContentView(FrameLayout(this).apply {
            addView(legacyScreen,FrameLayout.LayoutParams(-1,-1));legacyScreen.visibility=View.GONE
            addView(webScreen.view,FrameLayout.LayoutParams(-1,-1))
        })
        monitor.addListener { updateState() }
        monitor.addNaviEventListener { event -> if(event in setOf(NaviEvent.DESTINATION_CHANGED,NaviEvent.ROUTE_CANCELLED)) { stop("내비 목적지 변경·취소 · 보류");selected=null } }
        val missing=VehicleStateMonitor.REQUIRED_RUNTIME_PERMISSIONS.filter { checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED }
        if(missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
    }
    private fun label(text:String,size:Float=17f)=TextView(this).apply { this.text=text;textSize=size;setPadding(0,8,0,8);content.addView(this) }
    private fun field(hint:String,value:String="",secret:Boolean=false)=EditText(this).apply {
        this.hint=hint;setText(value);isSingleLine=true;isSaveEnabled=false
        inputType=InputType.TYPE_CLASS_TEXT or if(secret) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_NORMAL
        content.addView(this);guarded+=this
    }
    private fun button(text:String,guard:Boolean=true,action:()->Unit)=Button(this).apply {
        this.text=text;minHeight=64;setOnClickListener { action() };content.addView(this);if(guard) guarded+=this
    }
    private fun show(text:String) { if(!destroyed) { status.text=text;publishState() } }
    private fun parked():Boolean {
        val gear=monitor.snapshot.gear?:return false
        return gear.position==GearPosition.PARK && SystemClock.elapsedRealtime()-gear.elapsedRealtimeMs in 0..10000
    }
    private fun interaction()=resumed && parked() && (ux?.allowsInteraction==true || (debug && mock.isChecked))
    private fun safe():Boolean {
        if(!interaction()) return false
        if(!archived) return selected?.let { monitor.evaluate(Destination(it.getDouble("latitude"),it.getDouble("longitude"))) is PlaybackDecision.Allow }?:false
        val door=monitor.snapshot.door?:return false
        return door.status==DoorStatus.CLOSED && SystemClock.elapsedRealtime()-door.elapsedRealtimeMs in 0..10000
    }
    private fun updateState() {
        if(!::state.isInitialized || applyingMock) return
        state.text="기어: ${monitor.snapshot.gear?.position?:"미확인"} · 출처: ${signalSource()} · ${if(safe()) "재생 조건 충족" else "보류"}"
        guarded.forEach { it.isEnabled=interaction() }
        if(!safe()) { confirmation?.dismiss();confirmation=null;if(player!=null || busy) stop("차량·화면 조건 변경 · 중단, 자동 재개 안 함") }
        publishState()
        val message=selected?:return
        if(client==null || archived || !safe() || busy || player!=null || pendingCompletion!=null || !attempted.add(message.getString("id"))) return
        if(ListeningPolicy.requiresConfirmation(message.getString("listeningMode"),confirmAlways.isChecked)) {
            if(htmlMode) { show("도착·주차 조건 확인 · 듣기를 눌러 재생해주세요");return }
            confirmation=AlertDialog.Builder(this).setTitle("재생 전 확인").setMessage("차량 스피커로 재생합니다. 동승 상황을 확인해주세요.")
                .setPositiveButton("듣기") { _,_->confirmation=null;startPlayback() }.setNegativeButton("삭제") { _,_->confirmation=null;confirmDelete() }
                .setNeutralButton("나중에",null).create().also { it.show() }
        } else startPlayback()
    }
    private fun signalSource():String {
        val snapshot=monitor.snapshot
        val sources=listOfNotNull(snapshot.gear?.source,snapshot.door?.source,snapshot.location?.source,snapshot.navi?.source)
        return if(SignalSource.MOCK in sources) "MOCK" else if(sources.isEmpty()) "미확인" else "SDK"
    }
    private fun applyMock() {
        applyingMock=true
        try {
        val enabled=debug && mock.isChecked
        monitor.setMockGear(if(enabled) GearPosition.PARK else null);monitor.setMockDoor(if(enabled) DoorStatus.CLOSED else null)
        val message=selected
        val arrivalMock=debug && (enabled || arrivalOverride!="SDK")
        val lat=if(arrivalMock) message?.getDouble("latitude") else null
        val lon=if(arrivalMock) message?.getDouble("longitude") else null
        val before=arrivalOverride=="BEFORE"
        monitor.setMockLocation(if(before && lat!=null) lat+0.01 else lat,lon)
        monitor.setMockNavi(lat,lon,arrivalMock && !before && message!=null)
        } finally { applyingMock=false }
    }
    private fun runTask(task:()->Unit) { worker.execute(task) }
    private fun signIn() {
        if(!interaction()) return show("주차·화면 사용 조건을 확인해주세요")
        val base=server.text.toString().trim();val name=login.text.toString().trim();val secret=password.text.toString()
        if(!debug && !base.startsWith("https://")) return show("HTTPS 서버를 사용해주세요")
        logout();val current=generation
        runTask {
            try {
                val api=FriendMessageClient.login(base,name,secret)
                if(api.profile().getString("role")!="driver") { api.logout();error("Driver account required") }
                main.post { if(current==generation && !destroyed) { client=api;password.setText("");show("로그인 완료 · 받은 메시지를 새로고침하세요") } }
            } catch(e:Exception) { failure(e,current) }
        }
    }
    private fun load(archive:Boolean) {
        if(!interaction()) return show("주차 후 목록을 확인해주세요")
        val api=client?:return show("먼저 로그인해주세요")
        stop("목록 불러오는 중");selected=null;archived=archive;val current=generation
        for(index in 0 until choices.childCount) guarded.remove(choices.getChildAt(index))
        choices.removeAllViews();loadedMessages=JSONArray();publishState()
        runTask {
            try {
                val rows=if(archive) api.archive() else api.inbox()
                main.post {
                    if(current!=generation || destroyed) return@post
                    loadedMessages=rows
                    show(if(rows.length()==0) "메시지가 없습니다. 예약일 전 메시지는 표시하지 않습니다." else "메시지를 선택하세요")
                    for(index in 0 until rows.length()) {
                        val message=rows.getJSONObject(index)
                        val choice=Button(this).apply {
                            text="${message.getString("placeName")} · ${if(message.getString("listeningMode")=="PRIVATE") "재생 전 확인" else "자동 재생"}"
                            setOnClickListener {
                                if(!interaction()) return@setOnClickListener
                                selectMessage(message,archive)
                            }
                        };choices.addView(choice);guarded+=choice
                    }
                }
            } catch(e:Exception) { failure(e,current) }
        }
    }
    private fun startPlayback() {
        if(!safe()) return show("도착·P단·문 닫힘·화면 사용 조건을 확인해주세요")
        if(busy || player!=null) return
        if(pendingCompletion!=null) return show("완료 보고를 먼저 재시도해주세요")
        val api=client?:return;val message=selected?:return;val id=message.getString("id")
        attempted.add(id);busy=true;publishState();val current=generation
        runTask {
            var ready:File?=null
            try {
                val bytes=api.audio(id)
                ready=File.createTempFile("friend-voice-",".wav",cacheDir).apply { writeBytes(bytes) }
                val prepared=ready
                main.post {
                    if(current!=generation || destroyed || !safe()) { prepared.delete();return@post }
                    busy=false
                    if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { prepared.delete();show("오디오 포커스 미확보 · 보류");return@post }
                    hasFocus=true;file=prepared
                    try {
                        val media=MediaPlayer();player=media;media.setAudioAttributes(attributes);media.setVolume(volume/100f,volume/100f);media.setDataSource(prepared.absolutePath)
                        media.setOnPreparedListener {
                            if(player!==media || !safe()) stop("준비 중 조건 변경 · 보류")
                            else { media.start();lastDurationMs=media.duration;show("음성 재생 중 · ${signalSource()}") }
                        }
                        media.setOnCompletionListener {
                            if(player!==media) return@setOnCompletionListener
                            lastDurationMs=media.duration;lastPositionMs=lastDurationMs;releasePlayer()
                            if(!archived) { pendingCompletion=id;reportCompletion(id) } else show("보관 음성 재생 완료")
                        }
                        media.setOnErrorListener { _,_,_->stop("음성 재생 실패 · 완료로 기록하지 않음");true }
                        media.prepareAsync()
                    } catch(e:Exception) { stop("음성 준비 실패") }
                }
            } catch(e:Exception) { ready?.delete();main.post { if(current==generation) busy=false };failure(e,current) }
        }
    }
    private fun reportCompletion(id:String) {
        val api=client?:return;val current=generation
        runTask {
            try { api.complete(id);main.post { if(current==generation) { pendingCompletion=null;lastCompleted=id;archived=true;show("완료 보고 확인 · 기본 7일 보관, 주차 중 이모지 답장 가능") } } }
            catch(e:Exception) { failure(e,current) }
        }
    }
    private fun react(emoji:String) {
        if(!interaction()) return show("주차 후 답장해주세요")
        val api=client?:return
        val message=selected
        val id=if(message!=null && (message.optLong("completedAt",0)>0 || message.optString("id")==lastCompleted)) message.getString("id") else return show("재생 완료 보고 후 답장해주세요")
        val current=generation
        runTask { try { api.react(id,emoji);main.post { if(current==generation) show("답장을 보냈습니다") } } catch(e:Exception) { failure(e,current) } }
    }
    private fun confirmDelete() {
        if(!interaction()) return
        val id=selected?.getString("id")?:return;val api=client?:return
        confirmation=AlertDialog.Builder(this).setTitle("메시지 삭제").setMessage("음성을 더 이상 받을 수 없습니다. 삭제할까요?")
            .setNegativeButton("취소",null).setPositiveButton("삭제") { _,_->
                if(!interaction()) return@setPositiveButton
                stop("삭제 중");val current=generation
                runTask { try { api.delete(id);main.post { if(current==generation) { selected=null;load(archived) } } } catch(e:Exception) { failure(e,current) } }
            }.create().also { it.show() }
    }
    private fun releasePlayer() {
        runCatching { player?.let { lastPositionMs=it.currentPosition;lastDurationMs=it.duration } }
        player?.setOnPreparedListener(null);player?.setOnCompletionListener(null);player?.setOnErrorListener(null)
        runCatching { player?.release() };player=null;file?.delete();file=null
        if(hasFocus) { audio.abandonAudioFocusRequest(focus);hasFocus=false }
    }
    private fun stop(message:String) { generation++;busy=false;confirmation?.dismiss();confirmation=null;releasePlayer();show(message) }
    private fun logout() {
        val old=client;client=null;stop("로그아웃 완료");selected=null;lastCompleted=null;pendingCompletion=null;attempted.clear();loadedMessages=JSONArray();lastPositionMs=0;lastDurationMs=0
        if(::choices.isInitialized) choices.removeAllViews()
        if(::password.isInitialized) password.setText("")
        cacheDir.listFiles()?.filter { it.name.startsWith("friend-voice-") }?.forEach { it.delete() }
        if(old!=null) runTask { runCatching { old.logout() } }
        publishState()
    }
    private fun failure(error:Exception,current:Int) { main.post {
        if(current!=generation || destroyed) return@post
        val api=error as? VoiceMessageClient.ApiFailure
        if(api?.status==401 || api?.status==403) { logout();show("로그인이 만료됐거나 권한이 없습니다. 다시 로그인해주세요") }
        else show(if(api!=null) "서버 응답 ${api.status} · ${api.code}" else "연결 또는 응답 처리 실패 · 서버 주소와 실행 상태를 확인해주세요")
    } }
    private fun selectMessage(message:JSONObject,archive:Boolean) {
        if(!interaction()) return
        stop("메시지 선택");selected=message;archived=archive;lastPositionMs=0;lastDurationMs=0
        applyMock();updateState()
    }
    private fun displayMessage(message:JSONObject)=JSONObject().apply {
        put("id",message.getString("id"));put("placeName",message.optString("placeName"))
        put("mode",message.optString("listeningMode"));put("seconds",message.optDouble("audioSeconds",0.0))
        put("completed",message.optLong("completedAt",0)>0 || message.optString("id")==lastCompleted)
    }
    private fun publishState() {
        if(!::webScreen.isInitialized || !::mock.isInitialized || destroyed) return
        val items=JSONArray()
        for(index in 0 until loadedMessages.length()) items.put(displayMessage(loadedMessages.getJSONObject(index)))
        val playing=runCatching { player?.isPlaying==true }.getOrDefault(false)
        if(playing) runCatching { lastPositionMs=player!!.currentPosition;lastDurationMs=player!!.duration }
        val hold=if(selected!=null && !archived) (monitor.evaluate(Destination(selected!!.getDouble("latitude"),selected!!.getDouble("longitude"))) as? PlaybackDecision.Hold)?.detail else null
        webScreen.render(JSONObject().apply {
            put("protocol",1);put("connected",client!=null);put("interactive",interaction());put("allowed",safe())
            put("gear",monitor.snapshot.gear?.position?.name?:"UNKNOWN");put("door",monitor.snapshot.door?.status?.name?:"UNKNOWN")
            put("source",signalSource());put("status",status.text.toString());put("holdReason",hold?:"")
            put("messages",items);put("selected",selected?.let(::displayMessage)?:JSONObject.NULL)
            put("playing",playing);put("busy",busy);put("positionMs",lastPositionMs);put("durationMs",lastDurationMs)
            put("pendingCompletion",pendingCompletion?:JSONObject.NULL);put("forceConfirm",confirmAlways.isChecked)
            put("volume",volume);put("debug",debug);put("fullMock",mock.isChecked);put("arrivalOverride",arrivalOverride)
        })
    }
    private fun webCommand(body:JSONObject) {
        if(destroyed || !resumed) return
        when(body.optString("action")) {
            "ready"->publishState()
            "login"->{ if(!interaction()) return
                val base=body.optString("server");val account=body.optString("account");val secret=body.optString("password")
                if(base.length>2048 || account.length>100 || secret.length>128) return
                server.setText(base);login.setText(account);password.setText(secret);signIn()
            }
            "list"->load(body.optBoolean("archive"))
            "select"->{
                val id=body.optString("id")
                for(index in 0 until loadedMessages.length()) {
                    val message=loadedMessages.getJSONObject(index)
                    if(message.optString("id")==id) { selectMessage(message,archived);break }
                }
            }
            "play"->startPlayback()
            "stop"->stop("사용자 중단 · 자동 재개 안 함")
            "retry"->if(interaction()) pendingCompletion?.let(::reportCompletion)
            "reaction"->{ val emoji=body.optString("emoji");if(emoji in setOf("👍","🥹","♥️")) react(emoji) }
            "delete"->confirmDelete()
            "logout"->if(interaction()) logout()
            "confirmAlways"->if(interaction()) { confirmAlways.isChecked=body.optBoolean("enabled");publishState() }
            "volume"->if(interaction()) { volume=body.optInt("value",70).coerceIn(0,100);player?.setVolume(volume/100f,volume/100f);publishState() }
            "mock"->if(debug) { mock.isChecked=body.optBoolean("enabled");publishState() }
            "arrival"->if(debug && body.optString("value") in setOf("SDK","BEFORE","ARRIVED")) {
                stop("모의 도착 조건 변경 · 자동 재개 안 함");arrivalOverride=body.getString("value");applyMock();updateState()
            }
            "legacy"->if(debug) { stop("검증 화면으로 이동");htmlMode=false;webScreen.view.visibility=View.GONE;legacyScreen.visibility=View.VISIBLE }
        }
    }
    override fun onResume() { super.onResume();resumed=true;monitor.start();ux?.start();if(::webScreen.isInitialized) webScreen.resume();main.post(ticker) }
    override fun onPause() { resumed=false;main.removeCallbacks(ticker);stop("화면 이탈 · 재생 중단");monitor.stop();ux?.stop();if(::webScreen.isInitialized) webScreen.pause();super.onPause() }
    override fun onDestroy() { destroyed=true;client=null;releasePlayer();monitor.stop();worker.shutdown();if(::webScreen.isInitialized) webScreen.destroy();super.onDestroy() }
}
