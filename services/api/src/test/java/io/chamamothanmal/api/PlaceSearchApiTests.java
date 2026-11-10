package io.chamamothanmal.api;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties={"app.places.kakao-key=","spring.datasource.url=jdbc:h2:mem:place-api-tests;DB_CLOSE_DELAY=-1"})
class PlaceSearchApiTests {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate db;
    @Autowired FamilyService families; @Autowired PrototypeAuth auth;
    static final String S="Bearer test_sender_token_0000000000000000000001";
    @BeforeEach void reset(){db.update("DELETE FROM cm_users");}
    @Test void requiresUserAuthentication() throws Exception {
        mvc.perform(get("/api/v2/places").param("query","test")).andExpect(status().isUnauthorized());
    }
    @Test void missingProviderKeyReturnsClearServiceError() throws Exception {
        mvc.perform(get("/api/v2/places").header("Authorization",S).param("query","test"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("PLACE_SEARCH_NOT_CONFIGURED"));
    }
    @Test void invalidInputReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/v2/places").header("Authorization",S).param("query"," ")).andExpect(status().isBadRequest());
    }
    @Test void successfulProxyReturnsPlaceNameAddressAndCoordinates() throws Exception {
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange->{
            byte[] body=("{\"meta\":{\"total_count\":1,\"pageable_count\":1,\"is_end\":true},\"documents\":[{\"id\":\"fixture-1\",\"place_name\":\"가상대학교 정문\",\"address_name\":\"시험시 가상동 1\",\"road_address_name\":\"시험시 가상로 1\",\"x\":\"127\",\"y\":\"37\"}]}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length);try(var stream=exchange.getResponseBody()){stream.write(body);}
        });server.start();
        try {
            var provider=new PlaceSearchService("synthetic_provider_key_0000000000",new com.fasterxml.jackson.databind.ObjectMapper(),"http://127.0.0.1:"+server.getAddress().getPort());
            var http=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new PlaceSearchController(provider,families))
                .setControllerAdvice(new ApiErrors()).addFilters(auth).build();
            http.perform(get("/api/v2/places").header("Authorization",S).param("query","가상대학교 정문"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].placeName").value("가상대학교 정문"))
                .andExpect(jsonPath("$.items[0].address").value("시험시 가상로 1"))
                .andExpect(jsonPath("$.items[0].latitude").value(37)).andExpect(jsonPath("$.items[0].longitude").value(127));
        } finally { server.stop(0); }
    }
    @Test void deletedUsersCannotSearch() throws Exception {
        db.update("INSERT INTO cm_users(id,state) VALUES('demo-sender','DELETED')");
        mvc.perform(get("/api/v2/places").header("Authorization",S).param("query","test")).andExpect(status().isForbidden());
    }
}
