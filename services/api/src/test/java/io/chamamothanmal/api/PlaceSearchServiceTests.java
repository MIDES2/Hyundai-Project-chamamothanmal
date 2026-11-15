package io.chamamothanmal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class PlaceSearchServiceTests {
    HttpServer server;PlaceSearchService service;
    int responseStatus;String responseBody,lastPath,lastAuthorization;AtomicInteger calls;
    static final String KEY="synthetic_rest_key_0000000000000000";
    String keyword="{\"meta\":{\"total_count\":2,\"pageable_count\":2,\"is_end\":false},\"documents\":[{\"id\":\"test-1\",\"place_name\":\"가상대학교 정문\",\"address_name\":\"시험시 가상동 1\",\"road_address_name\":\"시험시 가상로 1\",\"x\":\"127.0\",\"y\":\"37.0\"}]}";
    @BeforeEach void start() throws Exception {
        responseStatus=200;responseBody=keyword;calls=new AtomicInteger();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{ calls.incrementAndGet();lastPath=e.getRequestURI().toASCIIString();lastAuthorization=e.getRequestHeaders().getFirst("Authorization");
            byte[] bytes=responseBody.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(responseStatus,bytes.length);
            try(var out=e.getResponseBody()){out.write(bytes);}
        });server.start();service=new PlaceSearchService(KEY,new ObjectMapper(),"http://127.0.0.1:"+server.getAddress().getPort());
    }
    @AfterEach void stop(){server.stop(0);}
    void failure(Runnable action,int status,String code){assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,e->{
        assertThat(e.status().value()).isEqualTo(status);assertThat(e.code()).isEqualTo(code);assertThat(e.getMessage()).doesNotContain(KEY);
    });}
    @Test void koreanKeywordUsesEncodedQueryAndMapsLongitudeLatitudeCorrectly(){
        var r=service.search("가상대학교 정문 &x=9","keyword",1,10);var item=r.items().get(0);
        assertThat(item.placeName()).isEqualTo("가상대학교 정문");assertThat(item.address()).isEqualTo("시험시 가상로 1");
        assertThat(item.latitude()).isEqualTo(37);assertThat(item.longitude()).isEqualTo(127);
        assertThat(lastAuthorization).isEqualTo("KakaoAK "+KEY);assertThat(lastPath).startsWith("/v2/local/search/keyword.json?");
        assertThat(lastPath).contains("%26x%3D9").doesNotContain(KEY);assertThat(r.hasNext()).isTrue();
    }
    @Test void roadAddressMissingFallsBackToLotAddress(){
        responseBody=keyword.replace("시험시 가상로 1","");
        assertThat(service.search("place","keyword",1,10).items().get(0).address()).isEqualTo("시험시 가상동 1");
    }
    @Test void addressSearchUsesAddressEndpointAndBuildingName(){
        responseBody="{\"meta\":{\"total_count\":1,\"pageable_count\":1,\"is_end\":true},\"documents\":[{\"address_name\":\"시험시 가상로 1\",\"x\":\"127\",\"y\":\"37\",\"road_address\":{\"address_name\":\"시험시 가상로 1\",\"building_name\":\"시험 건물\"},\"address\":{\"address_name\":\"시험시 가상동 1\"}}]}";
        var r=service.search("시험시 가상로 1","address",1,10);
        assertThat(lastPath).startsWith("/v2/local/search/address.json?");assertThat(r.items().get(0).placeName()).isEqualTo("시험 건물");assertThat(r.hasNext()).isFalse();
    }
    @Test void emptyResultRemainsEmptyInsteadOfInventingLocation(){
        responseBody="{\"meta\":{\"total_count\":0,\"pageable_count\":0,\"is_end\":true},\"documents\":[]}";
        var r=service.search("unknown","keyword",1,10);assertThat(r.items()).isEmpty();assertThat(r.hasNext()).isFalse();
    }
    @Test void validatesBeforeCallingProvider(){
        for(Runnable call:java.util.List.<Runnable>of(()->service.search(" ","keyword",1,10),()->service.search("x","wrong",1,10),
            ()->service.search("x","keyword",0,10),()->service.search("x","keyword",1,16),()->service.search("x\nsecret","keyword",1,10)))
            failure(call,400,"INVALID_REQUEST");
        assertThat(calls.get()).isZero();
    }
    @Test void missingKeyDoesNotDisableServerOrFakeResults(){
        var disabled=new PlaceSearchService("",new ObjectMapper(),"http://127.0.0.1:"+server.getAddress().getPort());
        failure(()->disabled.search("place","keyword",1,10),503,"PLACE_SEARCH_NOT_CONFIGURED");assertThat(calls.get()).isZero();
    }
    @Test void providerErrorsAreSanitized(){
        responseBody="private provider details "+KEY;responseStatus=401;
        failure(()->service.search("place","keyword",1,10),502,"PLACE_PROVIDER_CONFIGURATION_ERROR");
        responseStatus=429;failure(()->service.search("place","keyword",1,10),503,"PLACE_SEARCH_LIMITED");
        responseStatus=500;failure(()->service.search("place","keyword",1,10),502,"PLACE_PROVIDER_UNAVAILABLE");
    }
    @Test void malformedOrNonFiniteCoordinatesAreNotReturned(){
        responseBody=keyword.replace("127.0","NaN");failure(()->service.search("place","keyword",1,10),502,"PLACE_PROVIDER_INVALID_RESPONSE");
        responseBody=keyword.replace("37.0","999");failure(()->service.search("place","keyword",1,10),502,"PLACE_PROVIDER_INVALID_RESPONSE");
        responseBody="not json";failure(()->service.search("place","keyword",1,10),502,"PLACE_PROVIDER_INVALID_RESPONSE");
    }
    @Test void paginationIsBounded(){
        assertThat(service.search("place","keyword",45,10).hasNext()).isFalse();
        failure(()->service.search("place","keyword",46,10),400,"INVALID_REQUEST");
    }
}
