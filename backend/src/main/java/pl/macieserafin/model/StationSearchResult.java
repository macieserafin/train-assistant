package pl.macieserafin.model;

import java.util.List;

public record StationSearchResult(List<Station> stations, boolean hasMore) {}
