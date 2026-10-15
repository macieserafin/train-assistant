package pl.macieserafin.dto;

import pl.macieserafin.model.Station;
import java.util.List;

public record PkpStationsResponse(List<Station> stations, int page, int totalPages, int totalCount) {}
