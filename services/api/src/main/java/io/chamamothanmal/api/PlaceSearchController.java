package io.chamamothanmal.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/places")
public class PlaceSearchController {
    private final PlaceSearchService places;
    private final FamilyService families;
    public PlaceSearchController(PlaceSearchService places,FamilyService families) { this.places=places;this.families=families; }
    @GetMapping
    public PlaceSearchService.Results search(@RequestParam String query,@RequestParam(defaultValue="keyword") String type,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size,HttpServletRequest request) {
        families.checkActiveUser(PrototypeAuth.actor(request).id());
        return places.search(query,type,page,size);
    }
}
