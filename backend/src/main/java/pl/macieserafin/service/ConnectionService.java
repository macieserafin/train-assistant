package pl.macieserafin.service;

import org.springframework.stereotype.Service;
import pl.macieserafin.client.PkpClient;
import pl.macieserafin.dto.PkpScheduleResponse.Route;
import pl.macieserafin.dto.PkpScheduleResponse.Stop;
import pl.macieserafin.exception.PkpApiException;
import pl.macieserafin.exception.SearchValidationException;
import pl.macieserafin.model.ConnectionSearchResult;
import pl.macieserafin.model.ConnectionSearchResult.Proposal;

import java.time.*;
import java.util.*;

import static pl.macieserafin.exception.PkpApiException.Reason.INVALID_RESPONSE;

@Service
public class ConnectionService {
    private final PkpClient pkp;
    private final StationService stations;

    public ConnectionService(PkpClient pkp, StationService stations) {
        this.pkp = pkp;
        this.stations = stations;
    }

    public ConnectionSearchResult find(String from, String to, LocalDateTime arrivalBefore, String mode) {
        if (!"recommended".equals(mode) && !"all".equals(mode)) {
            throw new SearchValidationException("mode must be recommended or all.");
        }
        if (arrivalBefore == null) {
            throw new SearchValidationException("Provide arrivalBefore as YYYY-MM-DDTHH:mm.");
        }
        var origin = stations.resolve(from);
        var destination = stations.resolve(to);
        if (origin.id().equals(destination.id())) {
            throw new SearchValidationException("Origin and destination must be different.");
        }
        var response = pkp.getSchedules(origin.id(), destination.id(),
                arrivalBefore.toLocalDate().minusDays(1), arrivalBefore.toLocalDate(), SupportedCarriers.CODES);
        if (response == null || response.routes() == null) {
            throw new PkpApiException(INVALID_RESPONSE, null);
        }
        var candidates = new ArrayList<Candidate>();
        for (var route : response.routes()) {
            if (route == null) throw new PkpApiException(INVALID_RESPONSE, null);
            if (!SupportedCarriers.includes(route.carrierCode())) continue;
            validateRoute(route);
            for (var start : route.stations()) {
                if (!origin.id().equals(start.stationId()) || start.departureTime() == null
                        || Objects.equals(start.stopTypeId(), 2)) continue;
                for (var end : route.stations()) {
                    if (!destination.id().equals(end.stationId()) || end.arrivalTime() == null
                            || start.orderNumber() >= end.orderNumber()
                            || Objects.equals(end.stopTypeId(), 1)) continue;
                    for (var day : route.operatingDates()) {
                        if (day == null) throw new PkpApiException(INVALID_RESPONSE, null);
                        var departure = at(day, start.departureDay(), start.departureTime());
                        var arrival = at(day, end.arrivalDay(), end.arrivalTime());
                        if (arrival.isAfter(departure) && !arrival.isAfter(arrivalBefore)
                                && !departure.isBefore(arrivalBefore.minusDays(1))) {
                            candidates.add(new Candidate(route, start, end, day, departure, arrival));
                        }
                    }
                }
            }
        }
        candidates.sort(Comparator.comparing(Candidate::arrival).reversed()
                .thenComparing(Candidate::departure, Comparator.reverseOrder())
                .thenComparing(c -> c.route().orderId()));
        var proposals = new ArrayList<Proposal>();
        var used = new HashSet<String>();
        if ("all".equals(mode)) {
            var windowStart = arrivalBefore.minusMinutes(30);
            for (var candidate : candidates) {
                if (!candidate.arrival().isBefore(windowStart) && used.add(candidate.key())) {
                    proposals.add(toProposal(candidate, "ALL", arrivalBefore));
                }
            }
            return new ConnectionSearchResult(List.copyOf(proposals));
        }
        int[] buffers = {0, 15, 30};
        String[] types = {"LAST_MINUTE", "IDEAL", "EARLY"};
        for (int i = 0; i < buffers.length; i++) {
            var cutoff = arrivalBefore.minusMinutes(buffers[i]);
            for (var candidate : candidates) {
                if (candidate.arrival().isAfter(cutoff) || !used.add(candidate.key())) continue;
                proposals.add(toProposal(candidate, types[i], arrivalBefore));
                break;
            }
        }
        return new ConnectionSearchResult(List.copyOf(proposals));
    }

    private Proposal toProposal(Candidate candidate, String type, LocalDateTime arrivalBefore) {
        String number = candidate.start().departureTrainNumber();
        if (number == null || number.isBlank()) number = candidate.route().nationalNumber();
        return new Proposal(type, number, candidate.route().carrierCode(),
                candidate.departure(), candidate.arrival(),
                Duration.between(candidate.arrival(), arrivalBefore).toMinutes(),
                candidate.start().departurePlatform(), candidate.start().departureTrack(),
                arrivalValue(candidate.end().arrivalPlatform(), candidate.end().departurePlatform()),
                arrivalValue(candidate.end().arrivalTrack(), candidate.end().departureTrack()));
    }

    private String arrivalValue(String arrival, String departure) {
        return arrival == null || arrival.isBlank() ? departure : arrival;
    }

    private void validateRoute(Route route) {
        if (route == null || route.scheduleId() == null || route.orderId() == null
                || route.operatingDates() == null || route.stations() == null
                || route.stations().stream().anyMatch(s -> s == null || s.stationId() == null
                || s.orderNumber() == null)) {
            throw new PkpApiException(INVALID_RESPONSE, null);
        }
    }

    private LocalDateTime at(LocalDate day, Integer offset, String time) {
        try {
            return day.plusDays(offset == null ? 0 : offset).atTime(LocalTime.parse(time));
        } catch (DateTimeException e) {
            throw new PkpApiException(INVALID_RESPONSE, null);
        }
    }

    private record Candidate(Route route, Stop start, Stop end, LocalDate day,
                             LocalDateTime departure, LocalDateTime arrival) {
        String key() {
            return route.scheduleId() + ":" + route.orderId() + ":" + route.trainOrderId() + ":" + day;
        }
    }
}
