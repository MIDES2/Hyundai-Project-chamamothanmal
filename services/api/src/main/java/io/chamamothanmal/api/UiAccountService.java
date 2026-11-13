package io.chamamothanmal.api;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Local integration accounts only. Supabase remains a separate production authentication mode. */
@Service
@Transactional
public class UiAccountService {
    public record Profile(String id, String name, String handle, String phone, String role, List<String> friendIds) { }
    private record Session(String user, long expires) { }
    private final FamilyRepository db;
    private final CloudSettings cloud;
    private final ApiProperties settings;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, long[]> attempts = new HashMap<>();
    public UiAccountService(FamilyRepository db, CloudSettings cloud, ApiProperties settings, Clock clock) {
        this.db=db; this.cloud=cloud; this.settings=settings; this.clock=clock;
    }
    void requireLocal() {
        if (!"local".equals(cloud.authMode) || !"development".equals(settings.environment()))
            throw new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","Local account login is not enabled.");
    }
    synchronized void throttle(String address) {
        long now=clock.millis();
        attempts.entrySet().removeIf(e -> e.getValue()[0] <= now);
        if (attempts.size() >= 2048 && !attempts.containsKey(address)) throw limited();
        long[] entry=attempts.computeIfAbsent(address,k -> new long[]{now+60000,0});
        if (++entry[1]>20) throw limited();
    }
    private ApiException limited() { return new ApiException(HttpStatus.TOO_MANY_REQUESTS,"TOO_MANY_ATTEMPTS","잠시 후 다시 시도해주세요."); }
    private String key(String value) {
        String text=value==null?"":value.trim().toLowerCase(Locale.ROOT);
        return text.matches("[+0-9() -]+") ? text.replaceAll("[^0-9]","") : text;
    }
    private String secret() { byte[] b=new byte[32];random.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private String hash(String value) { return AudioStorage.digest(value.getBytes(StandardCharsets.UTF_8)); }
    private byte[] password(String value, byte[] salt) {
        PBEKeySpec spec=new PBEKeySpec(value.toCharArray(),salt,600000,256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (GeneralSecurityException e) { throw new IllegalStateException("Password processing unavailable"); }
        finally { spec.clearPassword(); }
    }
    public Profile register(UiAccountController.Register form) {
        requireLocal();
        String handle=key(form.handle()), phone=key(form.phone());
        if (handle.isBlank()) handle=phone;
        if (handle.isBlank() || handle.length()>100 || handle.matches(".*\\s.*") ||
            (!phone.isBlank() && !phone.matches("[0-9]{10,11}")) ||
            !Set.of("driver","recorder").contains(form.role())) throw ApiException.invalid("계정 정보를 확인해주세요.");
        String id=UUID.randomUUID().toString();
        byte[] salt=new byte[16];random.nextBytes(salt);
        String digest=Base64.getEncoder().encodeToString(password(form.password(),salt));
        try {
            db.update("INSERT INTO cm_users(id) VALUES(?)",id);
            db.update("INSERT INTO cm_ui_accounts(id,name,handle,phone,account_role,password_salt,password_hash) VALUES(?,?,?,?,?,?,?)",
                id,form.name().trim(),handle,phone,form.role(),Base64.getEncoder().encodeToString(salt),digest);
            for (String alias:new HashSet<>(List.of(handle,phone))) if (!alias.isBlank())
                db.update("INSERT INTO cm_ui_logins(login_key,account_id) VALUES(?,?)",alias,id);
        } catch(DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT,"ACCOUNT_EXISTS","이미 등록된 아이디 또는 전화번호입니다.");
        }
        return profile(id,true);
    }
    public Map<String,Object> login(String login,String password) {
        requireLocal();
        var rows=db.list("SELECT a.* FROM cm_ui_accounts a JOIN cm_ui_logins l ON l.account_id=a.id JOIN cm_users u ON u.id=a.id WHERE l.login_key=? AND u.state='ACTIVE'",key(login));
        // Perform the same expensive derivation for unknown accounts; never reveal which field failed.
        byte[] salt=rows.isEmpty()?new byte[16]:Base64.getDecoder().decode((String)rows.get(0).get("passwordSalt"));
        byte[] supplied=password(password,salt);
        byte[] expected=rows.isEmpty()?new byte[32]:Base64.getDecoder().decode((String)rows.get(0).get("passwordHash"));
        if (!MessageDigest.isEqual(supplied,expected) || rows.isEmpty())
            throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_LOGIN","아이디 또는 비밀번호를 확인해주세요.");
        sessions.entrySet().removeIf(e -> e.getValue().expires()<=clock.millis());
        if(sessions.size()>=4096) throw limited();
        String token=secret(),id=(String)rows.get(0).get("id");
        long expires=clock.millis()+8*60*60*1000L;
        sessions.put(hash(token),new Session(id,expires));
        return Map.of("accessToken",token,"expiresAt",expires,"profile",profile(id,true));
    }
    public String authenticate(String token) {
        if (!"local".equals(cloud.authMode)) return null;
        Session s=sessions.get(hash(token));
        if(s==null || s.expires()<=clock.millis()) return null;
        return db.list("SELECT id FROM cm_users WHERE id=? AND state='ACTIVE'",s.user()).isEmpty()?null:s.user();
    }
    public void logout(String token) { if(token!=null) sessions.remove(hash(token)); }
    public Profile profile(String id,boolean own) {
        db.ensureUser(id);
        db.one("SELECT id FROM cm_users WHERE id=? AND state='ACTIVE'",id);
        var rows=db.list("SELECT * FROM cm_ui_accounts WHERE id=?",id);
        List<String> friends=db.list("SELECT f.friend_id FROM cm_friends f JOIN cm_users u ON u.id=f.friend_id WHERE f.owner_id=? AND u.state='ACTIVE'",id)
            .stream().map(x -> (String)x.get("friendId")).toList();
        if(rows.isEmpty()) return new Profile(id,id,id,"",id.equals("demo-sender")?"recorder":"driver",own?friends:List.of());
        var p=rows.get(0);
        return new Profile(id,(String)p.get("name"),(String)p.get("handle"),own?(String)p.get("phone"):"",(String)p.get("accountRole"),own?friends:List.of());
    }
    public List<Profile> friends(String user) {
        return profile(user,true).friendIds().stream().map(id->profile(id,false)).toList();
    }
    public List<Profile> search(String user,String query) {
        String normalized=key(query);
        if(normalized.isBlank() || normalized.length()>100) return List.of();
        return db.list("SELECT a.id FROM cm_ui_logins l JOIN cm_ui_accounts a ON a.id=l.account_id JOIN cm_users u ON u.id=a.id WHERE l.login_key=? AND u.state='ACTIVE' AND a.id<>?",normalized,user)
            .stream().map(row -> profile((String)row.get("id"),false)).toList();
    }
    public void connect(String user,String other) {
        Profile a=profile(user,true), b=profile(other,false);
        if(user.equals(other) || a.role().equals(b.role())) throw ApiException.invalid("운전자와 녹음자 계정을 서로 연결해주세요.");
        // Lock both users in a consistent order so simultaneous reciprocal adds cannot race.
        for(String id:new TreeSet<>(List.of(user,other))) db.one("SELECT id FROM cm_users WHERE id=? FOR UPDATE",id);
        for(var pair:List.of(List.of(user,other),List.of(other,user)))
            if(db.list("SELECT owner_id FROM cm_friends WHERE owner_id=? AND friend_id=?",pair.get(0),pair.get(1)).isEmpty())
                db.update("INSERT INTO cm_friends(owner_id,friend_id) VALUES(?,?)",pair.get(0),pair.get(1));
    }
}
