package pl.macieserafin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.macieserafin.model.CarrierSummary;
import pl.macieserafin.service.CarrierService;

import java.util.List;

@RestController
public class CarriersController {
    private final CarrierService carrierService;

    public CarriersController(CarrierService carrierService) {
        this.carrierService = carrierService;
    }

    @GetMapping("/api/carriers")
    public List<CarrierSummary> getCarriers() {
        return carrierService.getCarriers();
    }
}
