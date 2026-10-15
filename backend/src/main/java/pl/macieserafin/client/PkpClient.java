package pl.macieserafin.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import pl.macieserafin.exception.PkpApiException;
import pl.macieserafin.model.Carrier;
import pl.macieserafin.dto.PkpStationsResponse;
import pl.macieserafin.dto.PkpScheduleResponse;
import org.springframework.web.util.UriBuilder;
import java.net.URI;
import java.time.LocalDate;
import java.util.function.Function;

import javax.net.ssl.SSLException;
import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.util.List;

import static pl.macieserafin.exception.PkpApiException.Reason.*;

@Component
public class PkpClient {
    private final RestClient http;
    private final String apiKey;

    public PkpClient(@Qualifier("pkpRestClient") RestClient http,
                     @Value("${pkp.api-key}") String apiKey) {
        this.http = http;
        this.apiKey = apiKey.trim();
    }

    public CarriersResponse getCarriers() {
        return get(uri -> uri.path("/api/v1/dictionaries/carriers").build(), CarriersResponse.class);
    }

    public PkpStationsResponse getStations(String search, int page, int pageSize) {
        return get(uri -> uri.path("/api/v1/dictionaries/stations")
                .queryParam("search", search).queryParam("page", page)
                .queryParam("pageSize", pageSize).build(), PkpStationsResponse.class);
    }

    public PkpScheduleResponse getSchedules(int fromId, int toId, LocalDate dateFrom, LocalDate dateTo,
                                            List<String> carriersInclude) {
        return get(uri -> uri.path("/api/v1/schedules")
                .queryParam("fromStations", fromId).queryParam("toStations", toId)
                .queryParam("dateFrom", dateFrom).queryParam("dateTo", dateTo)
                .queryParam("carriersInclude", String.join(",", carriersInclude))
                .queryParam("dictionaries", false).build(), PkpScheduleResponse.class);
    }

    private <T> T get(Function<UriBuilder, URI> uri, Class<T> responseType) {
        if (apiKey.isBlank()) {
            throw new PkpApiException(MISSING_KEY, null);
        }
        try {
            return http.get().uri(uri)
                    .header("X-API-Key", apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (request, upstream) -> {
                        int status = upstream.getStatusCode().value();
                        throw switch (status) {
                            case 401, 403 -> new PkpApiException(ACCESS_DENIED, status);
                            case 429 -> new PkpApiException(RATE_LIMITED, status);
                            default -> new PkpApiException(UPSTREAM_ERROR, status);
                        };
                    })
                    .body(responseType);
        } catch (ResourceAccessException e) {
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                    throw new PkpApiException(TIMEOUT, null);
                }
                if (cause instanceof SSLException) {
                    throw new PkpApiException(TLS_ERROR, null);
                }
            }
            throw new PkpApiException(CONNECTION_ERROR, null);
        } catch (RestClientException e) {
            throw new PkpApiException(INVALID_RESPONSE, null);
        }
    }

    public record CarriersResponse(List<Carrier> carriers) {}
}
