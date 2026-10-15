package pl.macieserafin.service;

import org.springframework.stereotype.Service;
import pl.macieserafin.client.PkpClient;
import pl.macieserafin.exception.PkpApiException;
import pl.macieserafin.exception.SearchValidationException;
import pl.macieserafin.model.Station;
import pl.macieserafin.model.StationSearchResult;

import java.util.Locale;
import static pl.macieserafin.exception.PkpApiException.Reason.INVALID_RESPONSE;

@Service
public class StationService {
    private final PkpClient pkp;

    public StationService(PkpClient pkp) { this.pkp = pkp; }

    public StationSearchResult search(String search, int page, int pageSize) {
        if (search == null || search.strip().length() < 2 || search.strip().length() > 100) {
            throw new SearchValidationException("Station name must contain 2 to 100 characters.");
        }
        if (page < 1 || pageSize < 1 || pageSize > 1000) {
            throw new SearchValidationException("page must be positive and pageSize must be between 1 and 1000.");
        }
        var response = pkp.getStations(search.strip(), page, pageSize);
        if (response == null || response.stations() == null || response.stations().stream()
                .anyMatch(s -> s == null || s.id() == null || s.id() <= 0
                        || s.name() == null || s.name().isBlank())) {
            throw new PkpApiException(INVALID_RESPONSE, null);
        }
        return new StationSearchResult(java.util.List.copyOf(response.stations()),
                response.page() < response.totalPages());
    }

    public Station resolve(String name) {
        var response = search(name, 1, 100);
        var matches = response.stations().stream()
                .filter(s -> normalize(s.name()).equals(normalize(name))).toList();
        if (matches.size() != 1) {
            throw new SearchValidationException("Station not found or ambiguous. Select an exact name using /api/stations.");
        }
        return matches.getFirst();
    }

    private String normalize(String value) {
        return value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
