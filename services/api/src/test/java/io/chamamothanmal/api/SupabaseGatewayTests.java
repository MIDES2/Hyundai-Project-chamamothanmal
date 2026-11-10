package io.chamamothanmal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

/** Local HTTP double, not a real Supabase project. */
class SupabaseGatewayTests {
    HttpServer server;
    SupabaseGateway gateway;
    CloudSettings settings;
    final String id="203ac493-f95a-4bbb-abd4-0405aff28e6b";
    final String filename="203ac493-f95a-4bbb-abd4-0405aff28e6b.wav";
    final byte[] audio={1,2,3,4};
    List<String> seen;
    boolean publicBucket;
    @BeforeEach void start() throws Exception {
        seen=new ArrayList<>(); publicBucket=false;
        settings=new CloudSettings(new MockEnvironment().withProperty("app.auth-mode","supabase")
            .withProperty("app.storage-mode","supabase").withProperty("app.supabase-url","https://test.supabase.co")
            .withProperty("app.supabase-publishable-key","test-public-key").withProperty("app.supabase-service-role-key","test-server-key"));
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange -> {
            String path=exchange.getRequestURI().getPath();
            seen.add(exchange.getRequestMethod()+" "+path);
            int status=200;byte[] body="{}".getBytes(StandardCharsets.UTF_8);
            if(path.equals("/auth/v1/user")) {
                if(!"Bearer valid-user-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))) status=401;
                else body=("{\"id\":\""+id+"\"}").getBytes(StandardCharsets.UTF_8);
            } else {
                if(!"Bearer test-server-key".equals(exchange.getRequestHeaders().getFirst("Authorization"))) status=403;
                if(path.equals("/storage/v1/bucket/voices")) body=("{\"public\":"+publicBucket+"}").getBytes(StandardCharsets.UTF_8);
                else if(exchange.getRequestMethod().equals("POST")) {
                    if(!Arrays.equals(audio,exchange.getRequestBody().readAllBytes())) status=400;
                } else if(exchange.getRequestMethod().equals("GET")) body=audio;
                else if(exchange.getRequestMethod().equals("DELETE") && path.startsWith("/storage/")) {
                    if(!new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8).contains(filename)) status=400;
                }
            }
            exchange.sendResponseHeaders(status,body.length);
            try(var out=exchange.getResponseBody()) { out.write(body); }
        });server.start();
        gateway=new SupabaseGateway(settings,new ObjectMapper(),"http://127.0.0.1:"+server.getAddress().getPort());
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void identityComesFromVerifiedAuthEndpoint() {
        assertThat(gateway.userId("valid-user-token")).isEqualTo(id);
        assertThatThrownBy(()->gateway.userId("forged-user-token")).isInstanceOfSatisfying(ApiException.class,
            e->assertThat(e.status().value()).isEqualTo(401));
    }
    @Test void privateStorageUploadDownloadAndDeleteUseServerCredential() {
        gateway.deleteAuthUser(id);
        gateway.upload(filename,audio);assertThat(gateway.download(filename)).isEqualTo(audio);gateway.delete(filename);
        assertThat(seen).contains("POST /storage/v1/object/voices/"+filename,
            "GET /storage/v1/object/authenticated/voices/"+filename,"DELETE /storage/v1/object/voices");
        assertThatThrownBy(()->gateway.download("../private")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void publicBucketCannotReceivePrivateVoice() {
        publicBucket=true;
        assertThatThrownBy(()->gateway.upload(filename,audio)).isInstanceOf(ApiException.class);
        assertThat(seen).noneMatch(x->x.startsWith("POST /storage/"));
    }
    @Test void outageFailsClosedWithoutReturningRemoteBody() {
        server.stop(0);
        assertThatThrownBy(()->gateway.userId("valid-user-token")).isInstanceOfSatisfying(ApiException.class,
            e-> { assertThat(e.status().value()).isEqualTo(503);assertThat(e.getMessage()).doesNotContain("test-server-key"); });
    }
    @Test void cloudAuthRejectsLegacyApiAndAcceptsOnlyVerifiedUser() throws Exception {
        var filter=new PrototypeAuth(null,new ObjectMapper(),settings,gateway);
        var request=new org.springframework.mock.web.MockHttpServletRequest("GET","/api/v2/me");
        request.addHeader("Authorization","Bearer valid-user-token");
        var response=new org.springframework.mock.web.MockHttpServletResponse();
        final boolean[] passed={false};filter.doFilter(request,response,(a,b)->passed[0]=true);
        assertThat(passed[0]).isTrue();assertThat(PrototypeAuth.actor(request).id()).isEqualTo(id);
        request=new org.springframework.mock.web.MockHttpServletRequest("GET","/api/v1/messages");
        response=new org.springframework.mock.web.MockHttpServletResponse();
        filter.doFilter(request,response,(a,b)-> { throw new AssertionError("legacy route must not execute"); });
        assertThat(response.getStatus()).isEqualTo(404);
    }
}
