package pl.macieserafin.model;

import java.time.LocalDateTime;
import java.util.List;

public record ConnectionSearchResult(List<Proposal> proposals) {
    public record Proposal(String type, String trainNumber, String carrierCode,
                           LocalDateTime departureTime, LocalDateTime arrivalTime,
                           long bufferMinutes, String departurePlatform, String departureTrack,
                           String arrivalPlatform, String arrivalTrack) {}
}
