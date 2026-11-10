package io.chamamothanmal.api;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static io.chamamothanmal.api.FamilyDtos.*;

@Service
@Transactional
public class FamilyService {
    private final FamilyRepository db;
    private final AudioStorage validator;
    private final VoiceBlobStore blobs;
    private final Clock clock;
    private final SupabaseGateway cloud;
    private final CloudSettings settings;
    public FamilyService(FamilyRepository db, AudioStorage validator, VoiceBlobStore blobs, Clock clock, SupabaseGateway cloud, CloudSettings settings) {
        this.db=db; this.validator=validator; this.blobs=blobs; this.clock=clock; this.cloud=cloud; this.settings=settings;
    }
    @Configuration
    static class TimeConfig { @Bean Clock apiClock() { return Clock.systemUTC(); } }
    private long now() { return clock.millis(); }
    private String uuid() { return UUID.randomUUID().toString(); }
    private String validId(String id) {
        try { return UUID.fromString(id).toString(); }
        catch (Exception ex) { throw ApiException.invalid("Use a UUID identifier."); }
    }
    private String text(Map<String,Object> row,String key) { return String.valueOf(row.get(key)); }
    private long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    private boolean flag(Map<String,Object> row,String key) { return Boolean.TRUE.equals(row.get(key)); }
    private ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN,"NOT_PERMITTED","This action is not permitted."); }
    private ApiException conflict(String code) { return new ApiException(HttpStatus.CONFLICT,code,"Refresh state before continuing."); }
    private String hash(String value) { return AudioStorage.digest(value.getBytes(StandardCharsets.UTF_8)); }
    private String secret() {
        byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private void actor(String id) {
        db.ensureUser(id);
        var u=db.one("SELECT * FROM cm_users WHERE id=? FOR UPDATE",id);
        if (!"ACTIVE".equals(u.get("state"))) throw forbidden();
    }
    private Map<String,Object> member(String user,String familyId) {
        var family=db.one("SELECT * FROM cm_families WHERE id=? AND active=TRUE FOR UPDATE",familyId);
        db.one("SELECT * FROM cm_members WHERE family_id=? AND user_id=? AND active=TRUE",familyId,user);
        return family;
    }
    private void owner(String user,String familyId) {
        if (!user.equals(member(user,familyId).get("ownerId"))) throw forbidden();
    }
    private Map<String,Object> vehicle(String user,String vehicleId) {
        var car=db.one("SELECT * FROM cm_vehicles WHERE id=? AND active=TRUE",validId(vehicleId));
        member(user,text(car,"familyId")); return car;
    }
    private Map<String,Object> device(String user,String vehicleId,String token) {
        vehicle(user,vehicleId);
        if (token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw forbidden();
        return db.one("SELECT * FROM cm_devices WHERE vehicle_id=? AND user_id=? AND token_hash=? AND active=TRUE",
            vehicleId,user,hash(token));
    }
    private Map<String,Object> session(String user,String sessionId,String token,boolean active) {
        var s=db.one("SELECT * FROM cm_sessions WHERE id=?",validId(sessionId));
        var d=device(user,text(s,"vehicleId"),token);
        if (!user.equals(s.get("driverId")) || !d.get("id").equals(s.get("deviceId"))) throw forbidden();
        if (active && (!flag(s,"active") || number(s,"createdAt")+24L*3_600_000<=now())) throw conflict("SESSION_ENDED");
        return s;
    }
    private Map<String,Object> liveMessage(String id) {
        var message=db.one("SELECT * FROM cm_messages WHERE id=? AND deleted_at IS NULL",validId(id));
        if (number(message,"expiresAt") <= now()) throw new ApiException(HttpStatus.GONE,"MESSAGE_EXPIRED","Message has expired.");
        return message;
    }
    private Map<String,Object> messageView(Map<String,Object> row) {
        Map<String,Object> view=new LinkedHashMap<>();
        for(String key:List.of("id","senderId","vehicleId","placeName","latitude","longitude","radiusMeters",
            "audioBytes","audioSeconds","createdAt","expiresAt","completedAt","deletedAt")) view.put(key,row.get(key));
        view.put("messageId",view.remove("id")); view.put("requireParked",true);
        return view;
    }
    public Map<String,Object> me(String user) {
        actor(user); return Map.of("userId",user,"families",db.list(
            "SELECT f.id AS family_id,f.name,m.can_send,f.owner_id FROM cm_members m JOIN cm_families f ON f.id=m.family_id WHERE m.user_id=? AND m.active=TRUE AND f.active=TRUE",user));
    }
    public Map<String,Object> createFamily(String user,String name) {
        actor(user); String id=uuid();
        db.update("INSERT INTO cm_families(id,name,owner_id) VALUES(?,?,?)",id,name,user);
        db.update("INSERT INTO cm_members(family_id,user_id,can_send) VALUES(?,?,TRUE)",id,user);
        return Map.of("familyId",id,"name",name);
    }
    public List<Map<String,Object>> members(String user,String familyId) {
        actor(user); owner(user,validId(familyId));
        return db.list("SELECT user_id,can_send,active FROM cm_members WHERE family_id=?",familyId);
    }
    public String vehiclesFamily(String user,String id) { actor(user); return text(vehicle(user,id),"familyId"); }
    public Map<String,Object> code(String user,String familyId,String vehicleId) {
        actor(user); owner(user,validId(familyId));
        if (vehicleId != null && !familyId.equals(vehicle(user,vehicleId).get("familyId"))) throw forbidden();
        String plain=secret(); long expiry=now()+600_000;
        db.update("INSERT INTO cm_codes(code_hash,family_id,vehicle_id,kind,expires_at) VALUES(?,?,?,?,?)",
            hash(plain),familyId,vehicleId,vehicleId==null?"FAMILY":"VEHICLE",expiry);
        return Map.of("code",plain,"expiresAt",expiry);
    }
    private Map<String,Object> consume(String code,String kind) {
        if (code==null || !code.matches("[A-Za-z0-9_-]{43}")) throw forbidden();
        var c=db.one("SELECT * FROM cm_codes WHERE code_hash=? AND kind=? FOR UPDATE",hash(code),kind);
        if (flag(c,"consumed") || number(c,"expiresAt")<=now()) throw conflict("CODE_UNAVAILABLE");
        db.update("UPDATE cm_codes SET consumed=TRUE WHERE code_hash=?",hash(code)); return c;
    }
    public Map<String,Object> join(String user,String code) {
        actor(user); var c=consume(code,"FAMILY"); String f=text(c,"familyId");
        db.one("SELECT id FROM cm_families WHERE id=? AND active=TRUE FOR UPDATE",f);
        var prior=db.list("SELECT * FROM cm_members WHERE family_id=? AND user_id=?",f,user);
        if (prior.isEmpty()) db.update("INSERT INTO cm_members(family_id,user_id,can_send) VALUES(?,?,TRUE)",f,user);
        else if(!flag(prior.get(0),"active")) db.update("UPDATE cm_members SET active=TRUE,can_send=TRUE WHERE family_id=? AND user_id=?",f,user);
        return Map.of("familyId",f);
    }
    public void permission(String user,String f,String target,Boolean canSend) {
        actor(user); owner(user,validId(f));
        if(user.equals(target)) throw conflict("OWNER_PROTECTED");
        if(db.update("UPDATE cm_members SET can_send=? WHERE family_id=? AND user_id=? AND active=TRUE",canSend,f,target)!=1) throw forbidden();
    }
    public void revokeMember(String user,String f,String target) {
        actor(user); owner(user,validId(f)); if(user.equals(target)) throw conflict("OWNER_PROTECTED");
        db.update("UPDATE cm_members SET active=FALSE,can_send=FALSE WHERE family_id=? AND user_id=?",f,target);
        db.update("UPDATE cm_codes SET consumed=TRUE WHERE family_id=?",f);
        db.update("UPDATE cm_messages SET deleted_at=? WHERE sender_id=? AND vehicle_id IN (SELECT id FROM cm_vehicles WHERE family_id=?) AND deleted_at IS NULL",now(),target,f);
        db.update("UPDATE cm_devices SET active=FALSE WHERE user_id=? AND vehicle_id IN (SELECT id FROM cm_vehicles WHERE family_id=?)",target,f);
        db.update("UPDATE cm_sessions SET active=FALSE,ended_at=?,end_reason='DISCONNECTED' WHERE driver_id=? AND vehicle_id IN (SELECT id FROM cm_vehicles WHERE family_id=?)",now(),target,f);
    }
    public Map<String,Object> createVehicle(String user,String familyId,String name) {
        actor(user); owner(user,validId(familyId)); String id=uuid();
        db.update("INSERT INTO cm_vehicles(id,family_id,name) VALUES(?,?,?)",id,familyId,name);
        return Map.of("vehicleId",id,"familyId",familyId,"name",name);
    }
    public List<Map<String,Object>> vehicles(String user,String f) {
        actor(user); member(user,validId(f));
        return db.list("SELECT id AS vehicle_id,name,revision FROM cm_vehicles WHERE family_id=? AND active=TRUE",f);
    }
    public Map<String,Object> pair(String user,String code) {
        actor(user); var c=consume(code,"VEHICLE"); String v=text(c,"vehicleId"); vehicle(user,v);
        // Re-pairing invalidates previous installations, sessions and cache revision.
        db.update("UPDATE cm_devices SET active=FALSE WHERE vehicle_id=?",v);
        db.update("UPDATE cm_sessions SET active=FALSE,ended_at=?,end_reason='DISCONNECTED' WHERE vehicle_id=? AND active=TRUE",now(),v);
        db.update("UPDATE cm_vehicles SET revision=revision+1 WHERE id=?",v);
        String id=uuid(), token=secret();
        db.update("INSERT INTO cm_devices(id,vehicle_id,user_id,token_hash) VALUES(?,?,?,?)",id,v,user,hash(token));
        return Map.of("vehicleId",v,"deviceId",id,"vehicleToken",token);
    }
    public Map<String,Object> access(String user,String v,String token) {
        actor(user); device(user,v,token); var car=vehicle(user,v);
        return Map.of("vehicleId",v,"revision",car.get("revision"),"checkedAt",now(),"cacheAction","REVALIDATE_BEFORE_PLAYBACK");
    }
    public void disconnect(String user,String v) {
        actor(user); var car=vehicle(user,v); owner(user,text(car,"familyId"));
        db.update("UPDATE cm_vehicles SET active=FALSE,revision=revision+1 WHERE id=?",v);
        db.update("UPDATE cm_devices SET active=FALSE WHERE vehicle_id=?",v);
        db.update("UPDATE cm_sessions SET active=FALSE,ended_at=?,end_reason='DISCONNECTED' WHERE vehicle_id=? AND active=TRUE",now(),v);
        db.update("UPDATE cm_codes SET consumed=TRUE WHERE vehicle_id=?",v);
        db.update("UPDATE cm_messages SET deleted_at=? WHERE vehicle_id=? AND deleted_at IS NULL",now(),v);
    }
    public Map<String,Object> register(String user,String key,Message form) {
        actor(user); validId(key); var car=vehicle(user,form.vehicleId());
        var m=db.one("SELECT can_send FROM cm_members WHERE family_id=? AND user_id=? AND active=TRUE",car.get("familyId"),user);
        if(!flag(m,"canSend")) throw forbidden();
        if(!Double.isFinite(form.latitude()) || !Double.isFinite(form.longitude())) throw ApiException.invalid("Finite coordinates required.");
        int radius=form.radiusMeters()==null?100:form.radiusMeters(); int hours=form.expiresInHours()==null?24:form.expiresInHours();
        var audio=validator.validate(form.audio());
        String fingerprint=hash(String.join("|",form.vehicleId(),form.placeName(),form.latitude().toString(),form.longitude().toString(),
            String.valueOf(radius),String.valueOf(hours),audio.sha256()));
        var previous=db.list("SELECT * FROM cm_messages WHERE sender_id=? AND request_id=?",user,key);
        if(!previous.isEmpty()) {
            var old=previous.get(0);
            if(!fingerprint.equals(old.get("requestHash"))) throw conflict("IDEMPOTENCY_CONFLICT");
            if(old.get("deletedAt")!=null) throw conflict("MESSAGE_DELETED");
            return Map.of("messageId",old.get("id"),"replayed",true,"expiresAt",old.get("expiresAt"));
        }
        String id=uuid(), name=blobs.save(audio);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if(status!=STATUS_COMMITTED) { try { blobs.delete(name); } catch(Exception ignored) { /* reconcile orphans */ } }
            }
        });
        long created=now(), expiry=created+hours*3_600_000L;
        db.update("INSERT INTO cm_messages(id,sender_id,vehicle_id,request_id,request_hash,place_name,latitude,longitude,radius_meters,audio_name,audio_bytes,audio_seconds,created_at,expires_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            id,user,form.vehicleId(),key,fingerprint,form.placeName(),form.latitude(),form.longitude(),radius,name,audio.bytes().length,audio.seconds(),created,expiry);
        return Map.of("messageId",id,"replayed",false,"expiresAt",expiry);
    }
    public List<Map<String,Object>> sent(String user) {
        actor(user); return db.list("SELECT * FROM cm_messages WHERE sender_id=? AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 100",user)
            .stream().map(this::messageView).toList();
    }
    public Map<String,Object> sentDetail(String user,String id) {
        actor(user); var m=db.one("SELECT * FROM cm_messages WHERE id=? AND sender_id=? AND deleted_at IS NULL",validId(id),user);
        Map<String,Object> view=messageView(m);
        view.put("deliveries",db.list("SELECT session_id,status,version FROM cm_deliveries WHERE message_id=?",id));
        view.put("replies",db.list("SELECT id AS reply_id,kind,created_at FROM cm_replies WHERE message_id=? ORDER BY created_at",id));
        return view;
    }
    public void deleteMessage(String user,String id) {
        actor(user); var m=db.one("SELECT * FROM cm_messages WHERE id=? AND sender_id=?",validId(id),user);
        member(user,text(db.one("SELECT family_id FROM cm_vehicles WHERE id=?",m.get("vehicleId")),"familyId"));
        db.update("UPDATE cm_messages SET deleted_at=? WHERE id=? AND deleted_at IS NULL",now(),id);
    }
    public List<Map<String,Object>> candidates(String user,String v,String token) {
        actor(user); device(user,v,token);
        return db.list("SELECT * FROM cm_messages WHERE vehicle_id=? AND expires_at>? AND deleted_at IS NULL AND completed_at IS NULL ORDER BY created_at LIMIT 100",v,now())
            .stream().map(this::messageView).toList();
    }
    private double distance(double a,double b,double c,double d) {
        double x=Math.toRadians(c-a), y=Math.toRadians(d-b);
        double h=Math.sin(x/2)*Math.sin(x/2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*Math.sin(y/2)*Math.sin(y/2);
        return 6_371_000*2*Math.asin(Math.sqrt(Math.min(1,h)));
    }
    public Map<String,Object> start(String user,String v,String token,Start form) {
        actor(user); var d=device(user,v,token); validId(form.requestId());
        if(!Boolean.TRUE.equals(form.consent()) || !Boolean.TRUE.equals(form.parkedConfirmed())) throw conflict("CONSENT_AND_PARKING_REQUIRED");
        if(!Double.isFinite(form.destinationLatitude()) || !Double.isFinite(form.destinationLongitude())) throw ApiException.invalid("Finite destination required.");
        String fingerprint=hash(form.toString());
        var existing=db.list("SELECT * FROM cm_sessions WHERE vehicle_id=? AND request_id=?",v,form.requestId());
        if(!existing.isEmpty()) {
            var old=existing.get(0);
            if(!fingerprint.equals(old.get("requestHash")) || !user.equals(old.get("driverId")) || !d.get("id").equals(old.get("deviceId"))) throw conflict("IDEMPOTENCY_CONFLICT");
            if(!flag(old,"active")) throw conflict("SESSION_ENDED");
            return Map.of("sessionId",old.get("id"),"messageIds",form.messageIds(),"replayed",true);
        }
        var m=liveMessage(form.messageIds().get(0));
        if(!v.equals(m.get("vehicleId"))) throw forbidden();
        if(m.get("completedAt")!=null) throw conflict("ALREADY_COMPLETED");
        if(distance(((Number)m.get("latitude")).doubleValue(),((Number)m.get("longitude")).doubleValue(),
            form.destinationLatitude(),form.destinationLongitude())>number(m,"radiusMeters")) throw conflict("DESTINATION_MISMATCH");
        db.update("UPDATE cm_sessions SET active=FALSE,ended_at=?,end_reason='CANCELLED' WHERE vehicle_id=? AND active=TRUE",now(),v);
        String id=uuid();
        db.update("INSERT INTO cm_sessions(id,vehicle_id,device_id,driver_id,destination_latitude,destination_longitude,source,created_at,request_id,request_hash) VALUES(?,?,?,?,?,?,?,?,?,?)",
            id,v,d.get("id"),user,form.destinationLatitude(),form.destinationLongitude(),form.source().name(),now(),form.requestId(),fingerprint);
        db.update("INSERT INTO cm_deliveries(session_id,message_id) VALUES(?,?)",id,m.get("id"));
        return Map.of("sessionId",id,"messageIds",form.messageIds(),"replayed",false);
    }
    public Map<String,Object> sessionDetail(String user,String id,String token) {
        actor(user); var s=session(user,id,token,false);
        Map<String,Object> view=new LinkedHashMap<>();
        for(String key:List.of("id","vehicleId","active","source","createdAt","endedAt","endReason")) view.put(key,s.get(key));
        view.put("sessionId",view.remove("id"));
        view.put("deliveries",db.list("SELECT message_id,status,version FROM cm_deliveries WHERE session_id=?",id));
        return view;
    }
    public void end(String user,String id,String token,EndReason reason) {
        actor(user); session(user,id,token,false);
        db.update("UPDATE cm_sessions SET active=FALSE,ended_at=?,end_reason=? WHERE id=? AND active=TRUE",now(),reason.name(),id);
    }
    public Map<String,Object> deliveryDetail(String user,String sid,String mid,String token) {
        actor(user); var session=session(user,sid,token,true); var message=liveMessage(mid);
        if(!session.get("vehicleId").equals(message.get("vehicleId"))) throw forbidden();
        var delivery=db.one("SELECT status,version FROM cm_deliveries WHERE session_id=? AND message_id=?",sid,validId(mid));
        Map<String,Object> view=messageView(message); view.put("playback",delivery); return view;
    }
    public byte[] audio(String user,String sid,String mid,String token) {
        actor(user); var s=session(user,sid,token,true); var m=liveMessage(mid);
        db.one("SELECT message_id FROM cm_deliveries WHERE session_id=? AND message_id=?",sid,mid);
        if(!s.get("vehicleId").equals(m.get("vehicleId"))) throw forbidden();
        if(m.get("completedAt")!=null) throw conflict("ALREADY_COMPLETED");
        return blobs.read(text(m,"audioName"));
    }
    public Map<String,Object> event(String user,String sid,String mid,String token,Event event) {
        actor(user); var s=session(user,sid,token,false); validId(mid); validId(event.eventId());
        var m=db.one("SELECT * FROM cm_messages WHERE id=? AND deleted_at IS NULL",mid);
        var delivery=db.one("SELECT * FROM cm_deliveries WHERE session_id=? AND message_id=? FOR UPDATE",sid,mid);
        if(!s.get("vehicleId").equals(m.get("vehicleId"))) throw forbidden();
        String fingerprint=hash(event.toString());
        var prior=db.list("SELECT * FROM cm_events WHERE session_id=? AND message_id=? AND event_id=?",sid,mid,event.eventId());
        if(!prior.isEmpty()) {
            var old=prior.get(0);
            if(!fingerprint.equals(old.get("payloadHash"))) throw conflict("EVENT_ID_CONFLICT");
            return Map.of("eventId",event.eventId(),"status",old.get("status"),"version",old.get("version"));
        }
        if(!flag(s,"active")) throw conflict("SESSION_ENDED");
        if(number(m,"expiresAt")<=now()) throw new ApiException(HttpStatus.GONE,"MESSAGE_EXPIRED","Message has expired.");
        if(number(delivery,"version")!=event.expectedVersion()) throw conflict("VERSION_CONFLICT");
        String before=text(delivery,"status"), after=event.status().name();
        if(Set.of("COMPLETED","SKIPPED").contains(before)) throw conflict("INVALID_TRANSITION");
        boolean valid=switch(event.status()) {
            case PLAYING -> Set.of("PENDING","PAUSED","HELD","FAILED").contains(before);
            case COMPLETED, PAUSED -> before.equals("PLAYING");
            case HELD, FAILED, SKIPPED -> !before.equals(after);
        };
        if(!valid) throw conflict("INVALID_TRANSITION");
        if(m.get("completedAt")!=null) throw conflict("ALREADY_COMPLETED");
        long version=number(delivery,"version")+1;
        db.update("UPDATE cm_deliveries SET status=?,version=? WHERE session_id=? AND message_id=?",after,version,sid,mid);
        db.update("INSERT INTO cm_events(session_id,message_id,event_id,payload_hash,status,version,source,reason,recorded_at) VALUES(?,?,?,?,?,?,?,?,?)",
            sid,mid,event.eventId(),fingerprint,after,version,event.source().name(),event.reason()==null?null:event.reason().name(),now());
        if(event.status()==Status.COMPLETED) db.update("UPDATE cm_messages SET completed_at=? WHERE id=?",now(),mid);
        return Map.of("eventId",event.eventId(),"status",after,"version",version);
    }
    public Map<String,Object> reply(String user,String sid,String mid,String token,Reply reply) {
        actor(user); session(user,sid,token,true); validId(reply.replyId()); liveMessage(mid);
        if(!Boolean.TRUE.equals(reply.parkedConfirmed())) throw conflict("PARKING_REQUIRED");
        var d=db.one("SELECT * FROM cm_deliveries WHERE session_id=? AND message_id=?",sid,validId(mid));
        if(!"COMPLETED".equals(d.get("status"))) throw conflict("PLAYBACK_NOT_COMPLETED");
        var prior=db.list("SELECT * FROM cm_replies WHERE session_id=? AND message_id=?",sid,mid);
        if(!prior.isEmpty()) {
            var old=prior.get(0);
            if(!reply.replyId().equals(old.get("id")) || !reply.kind().name().equals(old.get("kind"))) throw conflict("REPLY_CONFLICT");
            return Map.of("replyId",old.get("id"),"kind",old.get("kind"));
        }
        db.update("INSERT INTO cm_replies(id,session_id,message_id,author_id,kind,created_at) VALUES(?,?,?,?,?,?)",
            reply.replyId(),sid,mid,user,reply.kind().name(),now());
        return Map.of("replyId",reply.replyId(),"kind",reply.kind().name());
    }
    public void deleteAccount(String user) {
        actor(user);
        for(var f:db.list("SELECT id FROM cm_families WHERE owner_id=? AND active=TRUE",user)) {
            for(var car:db.list("SELECT id FROM cm_vehicles WHERE family_id=? AND active=TRUE",f.get("id"))) disconnect(user,text(car,"id"));
            db.update("UPDATE cm_families SET active=FALSE,name='Deleted family' WHERE id=?",f.get("id"));
            db.update("UPDATE cm_vehicles SET name='Disconnected vehicle' WHERE family_id=?",f.get("id"));
            db.update("UPDATE cm_codes SET consumed=TRUE WHERE family_id=?",f.get("id"));
        }
        db.update("UPDATE cm_messages SET deleted_at=? WHERE sender_id=? AND deleted_at IS NULL",now(),user);
        db.update("UPDATE cm_members SET active=FALSE,can_send=FALSE WHERE user_id=?",user);
        db.update("UPDATE cm_devices SET active=FALSE WHERE user_id=?",user);
        db.update("UPDATE cm_sessions SET active=FALSE,ended_at=?,end_reason='DISCONNECTED' WHERE driver_id=? AND active=TRUE",now(),user);
        db.update("UPDATE cm_users SET state='DELETED' WHERE id=?",user);
        db.update("INSERT INTO cm_auth_deletions(user_id,created_at) VALUES(?,?)",user,now());
    }
    /** Run after transaction commits; failed blob deletion is retried on the next sweep. */
    public int sweep() {
        long current=now(), cutoff=current-30L*24*3_600_000;
        db.update("UPDATE cm_sessions SET active=FALSE,ended_at=?,end_reason='EXPIRED' WHERE active=TRUE AND created_at<=?",current,current-24L*3_600_000);
        for(var m:db.list("SELECT id,audio_name FROM cm_messages WHERE audio_removed=FALSE AND (expires_at<=? OR deleted_at IS NOT NULL)",current)) {
            try {
                blobs.delete(text(m,"audioName"));
                db.update("UPDATE cm_messages SET audio_removed=TRUE WHERE id=?",m.get("id"));
            } catch(ApiException ignored) { /* no payload/path logging; retry next sweep */ }
        }
        for(var task:db.list("SELECT user_id FROM cm_auth_deletions WHERE completed=FALSE")) {
            try {
                if(settings.authMode.equals("supabase")) cloud.deleteAuthUser(text(task,"userId"));
                db.update("UPDATE cm_auth_deletions SET completed=TRUE WHERE user_id=?",task.get("userId"));
            } catch(ApiException ignored) { /* app access already blocked; retry remote Auth deletion */ }
        }
        int count=0;
        for(var m:db.list("SELECT id FROM cm_messages WHERE audio_removed=TRUE AND ((completed_at IS NOT NULL AND completed_at<?) OR (completed_at IS NULL AND expires_at<?))",cutoff,cutoff)) {
            Object id=m.get("id");
            db.update("DELETE FROM cm_replies WHERE message_id=?",id);
            db.update("DELETE FROM cm_events WHERE message_id=?",id);
            db.update("DELETE FROM cm_deliveries WHERE message_id=?",id);
            count+=db.update("DELETE FROM cm_messages WHERE id=?",id);
        }
        db.update("DELETE FROM cm_sessions WHERE active=FALSE AND ended_at<? AND id NOT IN(SELECT session_id FROM cm_deliveries)",cutoff);
        db.update("DELETE FROM cm_codes WHERE expires_at<?",current);
        return count;
    }
}
