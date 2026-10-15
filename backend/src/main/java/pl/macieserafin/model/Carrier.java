package pl.macieserafin.model;

import java.time.LocalDateTime;

public record Carrier(String code, String name, LocalDateTime validFrom, LocalDateTime validTo) {}
