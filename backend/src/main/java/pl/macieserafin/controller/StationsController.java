package pl.macieserafin.controller;

import org.springframework.web.bind.annotation.*;
import pl.macieserafin.model.StationSearchResult;
import pl.macieserafin.service.StationService;

@RestController
public class StationsController {
    private final StationService stations;

    public StationsController(StationService stations) { this.stations = stations; }

    @GetMapping("/api/stations")
    public StationSearchResult search(@RequestParam String search,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "100") int pageSize) {
        return stations.search(search, page, pageSize);
    }
}
