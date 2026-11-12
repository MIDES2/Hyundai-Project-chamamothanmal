package io.chamamothanmal.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Search on demand. No search history, provider key, raw URL or coordinates are logged. */
@Service
public class PlaceSearchService {
    public record Place(String provider,String id,String placeName,String address,
                        String roadAddress,String lotAddress,double latitude,double longitude) { }
    public record Results(List<Place> items,String type,int page,int size,int totalCount,int pageableCount,boolean hasNext) { }
    private final String key;
    private final ObjectMapper json;
    private final String origin;
    private final HttpClient client;
    @Autowired
    public PlaceSearchService(Environment env,ObjectMapper json) {
        this(env.getProperty("app.places.kakao-key",""),json,"https://dapi.kakao.com");
    }
    // Only local HTTP contract tests override the origin; clients cannot supply a provider URL.
    PlaceSearchService(String key,ObjectMapper json,String origin) {
        this.key=key;this.json=json;this.origin=origin;
        client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public Results search(String query,String type,int page,int size) {
        String q=query==null?"":query.strip();String mode=type==null?"keyword":type.toLowerCase(Locale.ROOT);
        if(q.isBlank() || q.length()>100 || q.codePoints().anyMatch(Character::isISOControl)
            || !Set.of("keyword","address").contains(mode) || page<1 || page>45 || size<1 || size>15)
            throw ApiException.invalid("Use query (1-100 characters), type keyword/address, page 1-45 and size 1-15.");
        if(key.isBlank()) throw error(HttpStatus.SERVICE_UNAVAILABLE,"PLACE_SEARCH_NOT_CONFIGURED");
        if(!key.matches("[A-Za-z0-9_-]{16,256}")) throw error(HttpStatus.SERVICE_UNAVAILABLE,"PLACE_SEARCH_NOT_CONFIGURED");
        String path="/v2/local/search/"+mode+".json?query="+URLEncoder.encode(q,StandardCharsets.UTF_8)+"&page="+page+"&size="+size;
        try {
            var request=HttpRequest.newBuilder(URI.create(origin+path)).timeout(Duration.ofSeconds(8))
                .header("Authorization","KakaoAK "+key).header("Accept","application/json").GET().build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
            try(var stream=response.body()) {
                if(response.statusCode()==429) throw error(HttpStatus.SERVICE_UNAVAILABLE,"PLACE_SEARCH_LIMITED");
                if(response.statusCode()==401 || response.statusCode()==403) throw error(HttpStatus.BAD_GATEWAY,"PLACE_PROVIDER_CONFIGURATION_ERROR");
                if(response.statusCode()!=200) throw error(HttpStatus.BAD_GATEWAY,"PLACE_PROVIDER_UNAVAILABLE");
                byte[] bytes=stream.readNBytes(1_048_577);
                if(bytes.length>1_048_576) throw error(HttpStatus.BAD_GATEWAY,"PLACE_PROVIDER_INVALID_RESPONSE");
                JsonNode root=json.readTree(bytes),meta=root.path("meta"),docs=root.path("documents");
                if(!docs.isArray() || docs.size()>size || !meta.path("is_end").isBoolean()
                    || !meta.path("total_count").canConvertToInt() || !meta.path("pageable_count").canConvertToInt())
                    throw error(HttpStatus.BAD_GATEWAY,"PLACE_PROVIDER_INVALID_RESPONSE");
                List<Place> results=new ArrayList<>();
                for(JsonNode d:docs) results.add(map(d,mode));
                int total=meta.get("total_count").asInt(),pageable=meta.get("pageable_count").asInt();
                if(total<0 || pageable<0) throw error(HttpStatus.BAD_GATEWAY,"PLACE_PROVIDER_INVALID_RESPONSE");
                return new Results(List.copyOf(results),mode,page,size,total,pageable,!meta.get("is_end").asBoolean() && page<45);
            }
        } catch(ApiException ex) { throw ex; }
        catch(java.net.http.HttpTimeoutException ex) { throw error(HttpStatus.GATEWAY_TIMEOUT,"PLACE_PROVIDER_TIMEOUT"); }
        catch(InterruptedException ex) { Thread.currentThread().interrupt();throw error(HttpStatus.SERVICE_UNAVAILABLE,"PLACE_PROVIDER_UNAVAILABLE"); }
        catch(Exception ex) { throw error(HttpStatus.BAD_GATEWAY,"PLACE_PROVIDER_INVALID_RESPONSE"); }
    }
    private Place map(JsonNode d,String mode) {
        double longitude=coordinate(d,"x",-180,180),latitude=coordinate(d,"y",-90,90);
        String road,lot,name,id;
        if(mode.equals("keyword")) {
            name=string(d,"place_name");id=string(d,"id");road=string(d,"road_address_name");lot=string(d,"address_name");
        } else {
            road=string(d.path("road_address"),"address_name");lot=string(d.path("address"),"address_name");
            name=string(d.path("road_address"),"building_name");
            if(name.isBlank()) name=string(d,"address_name");
            id="address:"+AudioStorage.digest((name+"|"+latitude+"|"+longitude).getBytes(StandardCharsets.UTF_8));
        }
        String address=road.isBlank()?lot:road;
        if(mode.equals("address") && address.isBlank()) address=string(d,"address_name");
        if(name.isBlank() || id.isBlank() || name.length()>120 || address.length()>500) throw error(HttpStatus.BAD_GATEWAY,"PLACE_PROVIDER_INVALID_RESPONSE");
        return new Place("KAKAO",id,name,address,road,lot,latitude,longitude);
    }
    private double coordinate(JsonNode d,String key,double min,double max) {
        try {
            double value=Double.parseDouble(d.path(key).asText());
            if(!Double.isFinite(value) || value<min || value>max) throw new NumberFormatException();
            return value;
        } catch(NumberFormatException ex) { throw error(HttpStatus.BAD_GATEWAY,"PLACE_PROVIDER_INVALID_RESPONSE"); }
    }
    private String string(JsonNode d,String name) { return d.path(name).isTextual()?d.get(name).asText():""; }
    private ApiException error(HttpStatus status,String code) {
        return new ApiException(status,code,"Place search is unavailable or not configured. Check the server settings and try again.");
    }
}
