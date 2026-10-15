package pl.macieserafin.dto;

import java.time.LocalDate;
import java.util.List;

public record PkpScheduleResponse(List<Route> routes) {
    public record Route(Integer scheduleId, Integer orderId, Integer trainOrderId,
                        String name, String carrierCode, String nationalNumber,
                        List<LocalDate> operatingDates, List<Stop> stations) {}

    public record Stop(Integer stationId, Integer orderNumber,
                       Integer arrivalDay, String arrivalTime,
                       Integer departureDay, String departureTime,
                       String departureTrainNumber, String departurePlatform,
                       String arrivalPlatform, Integer stopTypeId) {}
}
