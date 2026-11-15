import { useEffect, useState } from "react";
import { Car, Icon } from "../shared/Icons.jsx";
import PlaybackPage from "./pages/PlaybackPage.jsx";
const empty = { connected:false, interactive:false, allowed:false, status:"차량 앱 연결 중…", source:"미확인", gear:"UNKNOWN", door:"UNKNOWN", messages:[], playing:false, positionMs:0, durationMs:0 };
export default function NativeVehicleApp() {
  const [state,setState]=useState(empty), [page,setPage]=useState("home");
  const [account,setAccount]=useState(""),[password,setPassword]=useState(""),[server,setServer]=useState("http://10.0.2.2:8000");
  const [serverEditing,setServerEditing]=useState(false);
  function send(action,data={}) { window.VehicleNative?.send(JSON.stringify({ action,...data })); }
  useEffect(()=>{
    window.onVehicleState=value=>{ if(value.protocol===1) setState(value); };
    send("ready"); return ()=>{delete window.onVehicleState;};
  },[]);
  useEffect(()=>{ if(state.connected && page==="login") setPage("home"); },[state.connected]);
  const selected=state.selected;
  const completed=Boolean(selected?.completed);
  function navigate(next) {
    if(state.playing) send("stop");
    setPage(next);
    if(next==="inbox") send("list",{ archive:false });
    if(next==="archive") send("list",{ archive:true });
  }
  function choose(message) { send("select",{id:message.id});setPage("message"); }
  return <main className="vehicle-display native-shell">
    <section className="vehicle-panel native-panel">
      <header className="v-header native-header">
        <button className="v-brand" aria-label="홈" onClick={()=>navigate("home")}><Car surface="dark"/><strong>차마못한말</strong></button>
        <div className="native-header-actions"><span className={"native-source "+(state.source==="MOCK"?"is-mock":"")}>{state.source==="MOCK"?"MOCK · 모의 신호":state.source==="SDK"?"SDK 차량 신호":"차량 상태 대기"}</span>
          <button aria-label="설정" onClick={()=>navigate("settings")}><Icon name="settings"/></button>
        </div>
      </header>
      <div className="native-vehicle-state"><span>P단 {state.gear==="PARK"?"확인":"미확인 / P단 아님"}</span><span>문 {state.door==="CLOSED"?"닫힘":state.door==="OPEN"?"열림":"미확인"}</span><span>{state.allowed?"재생 조건 충족":"재생 보류"}</span></div>
      <p className="native-status" role="status">{state.status}</p>
      {!state.interactive && <p className="native-hold">주차 및 화면 사용 조건을 확인할 때까지 조작을 보류해요.</p>}
      <div className="native-content">
      {(!state.connected && page!=="settings") || page==="login" ? <form className="native-login" onSubmit={e=>{e.preventDefault();send("login",{server,account,password});setPassword("");setPage("login");}}>
        <div className="new-message"><Icon name="mail"/><strong>당신에게 도착한 마음을<br/>차 안에서 만나보세요</strong></div>
        <label>운전자 아이디 또는 전화번호<input value={account} autoComplete="off" onChange={e=>setAccount(e.target.value)} disabled={!state.interactive} required/></label>
        <label>비밀번호<input type="password" value={password} autoComplete="off" onChange={e=>setPassword(e.target.value)} disabled={!state.interactive} required/></label>
        <button className="v-pill" disabled={!state.interactive || !account || !password || state.busy}>로그인</button>
        <button type="button" className="native-link" onClick={()=>setServerEditing(!serverEditing)}>서버 주소 설정</button>
        {serverEditing && <input aria-label="서버 주소" value={server} onChange={e=>setServer(e.target.value)} disabled={!state.interactive}/>}
      </form> : page==="settings" ? <section className="native-settings">
        <h1>설정</h1>
        <label><input type="checkbox" checked={state.forceConfirm||false} disabled={!state.interactive} onChange={e=>send("confirmAlways",{enabled:e.target.checked})}/>항상 확인 후 재생</label>
        <p>발신자가 재생 전 확인을 선택하면 이 설정과 관계없이 먼저 확인해요.</p>
        <label>재생 음량<input type="range" min="0" max="100" value={state.volume??70} disabled={!state.interactive} onChange={e=>send("volume",{value:Number(e.target.value)})}/></label>
        <button className="v-pill" disabled={!state.interactive} onClick={()=>{send("logout");setPage("home");}}>로그아웃</button>
        {state.debug && <details className="native-debug"><summary>개발·시연용 조건 설정</summary>
          <p>모의 값을 사용하면 상단 출처가 MOCK으로 표시돼요.</p>
          <label><input type="checkbox" checked={state.fullMock||false} onChange={e=>send("mock",{enabled:e.target.checked})}/>도착·P단·문 닫힘·UX 전체 모의</label>
          <label>도착 시험<select value={state.arrivalOverride||"SDK"} onChange={e=>send("arrival",{value:e.target.value})}><option value="SDK">SDK 위치 사용</option><option value="BEFORE">도착 전 모의 · 기어/문은 SDK</option><option value="ARRIVED">도착 모의 · 기어/문은 SDK</option></select></label>
          <button onClick={()=>send("legacy")}>기존 검증 화면 열기</button>
        </details>}
      </section> : page==="inbox" || page==="archive" ? <section className="native-list">
        <div className="native-list-title"><h1>{page==="archive"?"마음 보관함":"전달받은 마음"}</h1><button disabled={!state.interactive} onClick={()=>send("list",{archive:page==="archive"})}>새로고침</button></div>
        {state.messages.length===0 ? <div className="new-message"><strong>아직 도착한 마음이 없어요</strong></div> : state.messages.map(message=><button className="native-message" key={message.id} disabled={!state.interactive} onClick={()=>choose(message)}><Icon name="mail"/><span><strong>{message.placeName}</strong><small>{message.completed?"보관된 마음":message.mode==="PRIVATE"?"재생 전 확인":"자동 재생"} · {Math.round(message.seconds)}초</small></span><span>›</span></button>)}
      </section> : page==="message" && selected ? <section className="native-message-detail">
        <h1>{selected.placeName}에 꽂아둔 마음</h1>
        {(state.playing || state.busy || completed) ? <PlaybackPage title="당신에게 도착한 목소리" home={()=>navigate("home")} playing={state.playing} progress={state.positionMs/1000} total={Math.max(.1,state.durationMs/1000||selected.seconds)} played={completed} isSaved={completed} onToggle={()=>send(state.playing?"stop":"play")} setScreen={()=>navigate("reaction")}/> : <>
          <div className="new-message"><Icon name="mail"/><strong>{selected.mode==="PRIVATE" || state.forceConfirm?"지금 들어도 괜찮을까요?":"목적지에서 만나는 마음"}</strong></div>
          <p className="native-detail-copy">{state.allowed?"차량 스피커로 재생돼요. 동승 상황을 확인해주세요.":state.holdReason||"도착·주차·문 닫힘 조건을 기다리고 있어요."}</p>
          <button className="v-pill" disabled={!state.allowed || state.busy || Boolean(state.pendingCompletion)} onClick={()=>send("play")}>듣기</button>
        </>}
        <div className="native-detail-actions"><button disabled={!state.interactive} onClick={()=>send("delete")}>이 마음 삭제</button><button onClick={()=>navigate("inbox")}>목록으로</button></div>
      </section> : page==="reaction" ? <section><h1>마음에 답해보세요</h1><div className="reaction-options">{["👍","🥹","♥️"].map(emoji=><button key={emoji} disabled={!state.interactive || !completed} onClick={()=>send("reaction",{emoji})}>{emoji}</button>)}</div><p className="reaction-note">답장은 선택이에요.</p></section> : <section className="native-home">
        <div className="new-message"><Icon name="mail"/><strong>차에 꽂아둔 마음,<br/>여기서 만나보세요</strong></div>
        <p>목적지에 도착해 주차하면<br/>당신에게 온 목소리를 들을 수 있어요.</p>
        <button className="v-pill" disabled={!state.interactive} onClick={()=>navigate("inbox")}>전달받은 마음 보기 <span>›</span></button>
      </section>}
      </div>
      {state.pendingCompletion && <button className="native-report" disabled={!state.interactive} onClick={()=>send("retry")}>완료 보고 재시도</button>}
      <nav className="native-nav" aria-label="차량 메뉴"><button onClick={()=>navigate("home")} aria-current={page==="home"?"page":undefined}><Icon name="home"/>홈</button><button disabled={!state.connected || !state.interactive} onClick={()=>navigate("inbox")}><Icon name="mail"/>받은 마음</button><button disabled={!state.connected || !state.interactive} onClick={()=>navigate("archive")}><Icon name="basket"/>보관함</button><button onClick={()=>navigate("settings")}><Icon name="settings"/>설정</button></nav>
    </section>
  </main>;
}
