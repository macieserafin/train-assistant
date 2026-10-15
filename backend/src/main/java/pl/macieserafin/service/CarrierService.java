package pl.macieserafin.service;

import org.springframework.stereotype.Service;
import pl.macieserafin.client.PkpClient;
import pl.macieserafin.exception.PkpApiException;
import pl.macieserafin.model.CarrierSummary;

import java.util.List;

import static pl.macieserafin.exception.PkpApiException.Reason.INVALID_RESPONSE;

@Service
public class CarrierService {
    private final PkpClient pkpClient;

    public CarrierService(PkpClient pkpClient) {
        this.pkpClient = pkpClient;
    }

    public List<CarrierSummary> getCarriers() {
        var response = pkpClient.getCarriers();
        if (response == null || response.carriers() == null || response.carriers().stream()
                .anyMatch(carrier -> carrier == null || carrier.code() == null || carrier.code().isBlank()
                        || carrier.name() == null || carrier.name().isBlank())) {
            throw new PkpApiException(INVALID_RESPONSE, null);
        }
        return response.carriers().stream()
                .filter(carrier -> SupportedCarriers.includes(carrier.code()))
                .map(carrier -> new CarrierSummary(carrier.code(), carrier.name()))
                .toList();
    }
}
