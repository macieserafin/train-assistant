package pl.macieserafin;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import pl.macieserafin.exception.PkpApiException;
import pl.macieserafin.client.PkpClient;
import pl.macieserafin.controller.ApiExceptionHandler;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.json.JsonMapper;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "pkp.api-key=test-secret", "pkp.read-timeout=500ms"
})
class CarriersEndpointTests {
    private static final AtomicInteger upstreamStatus = new AtomicInteger(200);
    private static final AtomicInteger delay = new AtomicInteger();
    private static final AtomicInteger calls = new AtomicInteger();
    private static final AtomicReference<String> body = new AtomicReference<>();
    private static final AtomicReference<String> key = new AtomicReference<>();
    private static final AtomicReference<String> scheduleBody = new AtomicReference<>();
    private static final AtomicReference<String> scheduleQuery = new AtomicReference<>();
    private final JsonMapper json = JsonMapper.builder().build();
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final HttpServer upstream = startUpstream();
    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    private static HttpServer startUpstream() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/api/v1/dictionaries/carriers", exchange -> {
                calls.incrementAndGet();
                key.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
                int status = upstreamStatus.get();
                byte[] bytes = body.get().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                try {
                    Thread.sleep(delay.get());
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException ignored) {
                    // The timeout test intentionally closes the connection early.
                } finally {
                    exchange.close();
                }
            });
            server.createContext("/api/v1/dictionaries/stations", exchange -> {
                String query = java.net.URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
                String stations = query.contains("Gdynia") ? "[{\"id\":5900,\"name\":\"Gdynia Główna\"}]"
                        : query.contains("Gdańsk") ? "[{\"id\":7500,\"name\":\"Gdańsk Główny\"}]" : "[]";
                byte[] bytes = ("{\"stations\":" + stations + ",\"page\":1,\"totalPages\":1,\"totalCount\":1}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.createContext("/api/v1/schedules", exchange -> {
                scheduleQuery.set(exchange.getRequestURI().getQuery());
                byte[] bytes = scheduleBody.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void configureUpstream(DynamicPropertyRegistry registry) {
        registry.add("pkp.base-url", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
    }

    @BeforeEach
    void reset() {
        upstreamStatus.set(200);
        delay.set(0);
        calls.set(0);
        scheduleBody.set(routes(
                route(1, "2026-10-16", "07:30:00", "08:00:00", 0, 0, 1, 2, null, null),
                route(2, "2026-10-16", "07:10:00", "07:45:00", 0, 0, 1, 2, null, null),
                route(3, "2026-10-16", "06:50:00", "07:30:00", 0, 0, 1, 2, null, null)));

        body.set("""
                {"generatedAt":"2026-10-15T08:00:00Z","carriers":[
                {"code":"PR","name":"POLREGIO S.A.","validFrom":"1999-01-01T00:00:00",
                 "validTo":null,"futureField":"ignored"},
                {"code":"SKMT","name":"PKP SKM w Trójmieście","validFrom":null,"validTo":null},
                {"code":"SKM","name":"Szybka Kolej Miejska Warszawa","validFrom":null,"validTo":null},
                {"code":"IC","name":"PKP Intercity S.A.","validFrom":null,"validTo":null}],
                "usage":{"description":"test"}}
                """);
    }

    @AfterAll
    static void stop() {
        upstream.stop(0);
        executor.shutdownNow();
    }

    private HttpResponse<String> getCarriers() throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/carriers"))
                .timeout(Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void returnsRealCarrierFieldsAndSendsSecretOnlyToUpstream() throws Exception {
        var response = getCarriers();
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));
        assertTrue(response.body().startsWith("["));
        assertTrue(response.body().contains("\"code\":\"PR\""));
        assertTrue(response.body().contains("\"code\":\"SKMT\""));
        assertEquals(2, json.readTree(response.body()).size());
        assertFalse(response.body().contains("\"code\":\"SKM\""));
        assertFalse(response.body().contains("\"code\":\"IC\""));
        assertTrue(response.body().contains("POLREGIO S.A."));
        assertEquals(2, json.readTree(response.body()).get(0).size());
        assertFalse(response.body().contains("validFrom"));
        assertFalse(response.body().contains("validTo"));
        assertFalse(response.body().contains("usage"));
        assertFalse(response.body().contains("test-secret"));
        assertEquals("test-secret", key.get());
        assertEquals(1, calls.get());
    }

    @Test
    void returnsEmptyListWhenUpstreamHasNoCarriers() throws Exception {
        body.set("{\"carriers\":[]}");
        var response = getCarriers();
        assertEquals(200, response.statusCode());
        assertEquals("[]", response.body());
    }

    @Test
    void mapsUpstreamErrorsToSanitizedProblemDetails() throws Exception {
        for (int status : new int[]{401, 403, 429, 500, 302}) {
            upstreamStatus.set(status);
            body.set("{\"message\":\"sensitive upstream body test-secret\"}");
            var response = getCarriers();
            assertEquals(status == 429 ? 503 : 502, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));
            var error = json.readTree(response.body());
            assertEquals(2, error.size());
            assertTrue(error.has("code"));
            assertTrue(error.has("message"));
            assertFalse(error.has("upstreamStatus"));
            assertEquals(status == 429 ? "RATE_LIMITED" : status == 401 || status == 403
                    ? "ACCESS_DENIED" : "UPSTREAM_ERROR", error.get("code").asText());
            assertFalse(response.body().contains("test-secret"));
            assertFalse(response.body().contains("sensitive upstream body"));
        }
    }

    @Test
    void rejectsMalformedOrIncompleteUpstreamData() throws Exception {
        for (String invalid : new String[]{"not json", "null", "{}", "{\"carriers\":null}",
                "{\"carriers\":[null]}", "{\"carriers\":[{\"code\":\"IC\"}]}",
                "{\"carriers\":[{\"code\":\"IC\",\"name\":\"IC\",\"validFrom\":\"invalid\"}]}"}) {
            body.set(invalid);
            var response = getCarriers();
            assertEquals(502, response.statusCode(), invalid);
            assertFalse(response.body().contains("test-secret"));
        }
    }

    @Test
    void returnsGatewayTimeout() throws Exception {
        delay.set(1200);
        assertEquals(504, getCarriers().statusCode());
    }

    @Test
    void missingKeyDoesNotContactUpstream() {
        var client = new PkpClient(RestClient.create("http://127.0.0.1:" + upstream.getAddress().getPort()), " ");
        var exception = assertThrows(PkpApiException.class, client::getCarriers);
        assertEquals(PkpApiException.Reason.MISSING_KEY, exception.reason());
        assertEquals(503, new ApiExceptionHandler().handlePkpError(exception).getStatusCode().value());
        assertEquals(0, calls.get());
    }

    @Test
    void connectionFailureIsBadGateway() throws Exception {
        var closed = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int closedPort = closed.getAddress().getPort();
        closed.start();
        closed.stop(0);
        var client = new PkpClient(RestClient.create("http://127.0.0.1:" + closedPort), "test-secret");
        var exception = assertThrows(PkpApiException.class, client::getCarriers);
        assertEquals(PkpApiException.Reason.CONNECTION_ERROR, exception.reason());
        assertEquals(502, new ApiExceptionHandler().handlePkpError(exception).getStatusCode().value());
    }

    private static String route(int id, String day, String departure, String arrival,
                                int departureDay, int arrivalDay, int fromOrder, int toOrder,
                                Integer fromStopType, Integer toStopType) {
        return """
                {"scheduleId":2026,"orderId":%d,"trainOrderId":%d,"nationalNumber":"TEST-%d",
                 "carrierCode":"SKMT","operatingDates":["%s"],"stations":[
                 {"stationId":5900,"orderNumber":%d,"departureDay":%d,"departureTime":"%s","stopTypeId":%s},
                 {"stationId":7500,"orderNumber":%d,"arrivalDay":%d,"arrivalTime":"%s","stopTypeId":%s}]}
                """.formatted(id, id, id, day, fromOrder, departureDay, departure, fromStopType,
                toOrder, arrivalDay, arrival, toStopType);
    }

    private static String routes(String... routes) {
        return "{\"routes\":[" + String.join(",", routes) + "]}";
    }

    private String connectionPath(String from, String to, String arrivalBefore) {
        return "/api/connections?from=" + URLEncoder.encode(from, StandardCharsets.UTF_8)
                + "&to=" + URLEncoder.encode(to, StandardCharsets.UTF_8)
                + "&arrivalBefore=" + arrivalBefore;
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void searchesStationsWithPagination() throws Exception {
        var response = get("/api/stations?search=Gdynia&page=1&pageSize=10");
        assertEquals(200, response.statusCode());
        var result = json.readTree(response.body());
        assertEquals(5900, result.get("stations").get(0).get("id").asInt());
        assertEquals(2, result.size());
        assertFalse(result.get("hasMore").asBoolean());
    }

    @Test
    void returnsThreeDistinctProposalsAndFiltersUnusableTrains() throws Exception {
        String normal = scheduleBody.get();
        String extra = routes(
                route(4, "2026-10-16", "07:30:00", "08:01:00", 0, 0, 1, 2, null, null),
                route(5, "2026-10-16", "07:00:00", "07:59:00", 0, 0, 3, 2, null, null),
                route(6, "2026-10-17", "07:00:00", "07:59:00", 0, 0, 1, 2, null, null),
                route(7, "2026-10-16", "07:00:00", "07:59:00", 0, 0, 1, 2, 2, null),
                route(8, "2026-10-16", "07:00:00", "07:59:00", 0, 0, 1, 2, null, 1),
                route(1, "2026-10-16", "07:30:00", "08:00:00", 0, 0, 1, 2, null, null));
        scheduleBody.set(normal.substring(0, normal.length() - 2) + "," + extra.substring(11));
        var response = get(connectionPath("Gdynia Główna", "Gdańsk Główny", "2026-10-16T08:00"));
        assertEquals(200, response.statusCode(), response.body());
        var result = json.readTree(response.body());
        var proposals = result.get("proposals");
        assertEquals(3, proposals.size());
        assertEquals("LAST_MINUTE", proposals.get(0).get("type").asText());
        assertEquals("TEST-1", proposals.get(0).get("trainNumber").asText());
        assertEquals(0, proposals.get(0).get("bufferMinutes").asInt());
        assertEquals("TEST-2", proposals.get(1).get("trainNumber").asText());
        assertEquals(15, proposals.get(1).get("bufferMinutes").asInt());
        assertEquals("TEST-3", proposals.get(2).get("trainNumber").asText());
        assertEquals(30, proposals.get(2).get("bufferMinutes").asInt());
        assertEquals(1, result.size());
        assertEquals(10, proposals.get(0).size());
        assertTrue(scheduleQuery.get().contains("fromStations=5900"));
        assertTrue(scheduleQuery.get().contains("toStations=7500"));
        assertTrue(scheduleQuery.get().contains("dateFrom=2026-10-15"));
        assertFalse(response.body().contains("test-secret"));
    }

    @Test
    void handlesMidnightAndReportsFewerThanThreeProposals() throws Exception {
        scheduleBody.set(routes(route(1, "2026-10-15", "23:50:00", "00:20:00", 0, 1, 1, 2, null, null)));
        var response = get(connectionPath("Gdynia Główna", "Gdańsk Główny", "2026-10-16T00:30"));
        assertEquals(200, response.statusCode());
        var proposals = json.readTree(response.body()).get("proposals");
        assertEquals(1, proposals.size());
        assertEquals("2026-10-15T23:50:00", proposals.get(0).get("departureTime").asText());
        assertEquals("2026-10-16T00:20:00", proposals.get(0).get("arrivalTime").asText());
        assertEquals(10, proposals.get(0).get("bufferMinutes").asInt());
    }

    @Test
    void returnsEmptyProposalsInsteadOfInventingConnections() throws Exception {
        scheduleBody.set(routes());
        var response = get(connectionPath("Gdynia Główna", "Gdańsk Główny", "2026-10-16T08:00"));
        assertEquals(200, response.statusCode());
        assertEquals(0, json.readTree(response.body()).get("proposals").size());
    }

    @Test
    void rejectsInvalidSearchParameters() throws Exception {
        for (String path : new String[]{"/api/connections", "/api/stations?search=x",
                "/api/stations?search=Gdynia&page=0", "/api/stations?search=Gdynia&pageSize=1001",
                connectionPath("Unknown", "Gdańsk Główny", "2026-10-16T08:00"),
                connectionPath("Gdynia Główna", "Gdynia Główna", "2026-10-16T08:00"),
                connectionPath("Gdynia Główna", "Gdańsk Główny", "08:00")}) {
            var response = get(path);
            assertEquals(400, response.statusCode(), path);
            var error = json.readTree(response.body());
            assertEquals(2, error.size());
            assertTrue(error.has("code"));
            assertTrue(error.has("message"));
            assertFalse(response.body().contains("test-secret"));
        }
    }

    @Test
    void rejectsMalformedSchedules() throws Exception {
        for (String invalid : new String[]{"{}", "{\"routes\":[null]}",
                routes(route(1, "2026-10-16", "bad-time", "07:59:00", 0, 0, 1, 2, null, null))}) {
            scheduleBody.set(invalid);
            assertEquals(502, get(connectionPath("Gdynia Główna", "Gdańsk Główny", "2026-10-16T08:00")).statusCode());
        }
    }

    @Test
    void restrictsConnectionsToPrAndSkmEvenIfUpstreamIgnoresFilter() throws Exception {
        scheduleBody.set(routes(
                route(1, "2026-10-16", "07:30:00", "08:00:00", 0, 0, 1, 2, null, null)
                        .replace("\"carrierCode\":\"SKMT\"", "\"carrierCode\":\"PR\""),
                route(2, "2026-10-16", "07:10:00", "07:45:00", 0, 0, 1, 2, null, null),
                route(3, "2026-10-16", "06:50:00", "07:30:00", 0, 0, 1, 2, null, null),
                route(4, "2026-10-16", "07:40:00", "08:00:00", 0, 0, 1, 2, null, null)
                        .replace("\"carrierCode\":\"SKMT\"", "\"carrierCode\":\"IC\""),
                route(5, "2026-10-16", "07:20:00", "07:45:00", 0, 0, 1, 2, null, null)
                        .replace("\"carrierCode\":\"SKMT\"", "\"carrierCode\":null"),
                route(6, "2026-10-16", "07:40:00", "08:00:00", 0, 0, 1, 2, null, null)
                        .replace("\"carrierCode\":\"SKMT\"", "\"carrierCode\":\"SKM\"")));
        var response = get(connectionPath("Gdynia Główna", "Gdańsk Główny", "2026-10-16T08:00"));
        assertEquals(200, response.statusCode(), response.body());
        var proposals = json.readTree(response.body()).get("proposals");
        assertEquals(3, proposals.size());
        assertEquals("PR", proposals.get(0).get("carrierCode").asText());
        assertEquals("SKMT", proposals.get(1).get("carrierCode").asText());
        assertEquals("SKMT", proposals.get(2).get("carrierCode").asText());
        assertTrue(scheduleQuery.get().contains("carriersInclude=PR,SKMT"));
    }

    @Test
    void returnsNoProposalsWhenOnlyOtherCarriersAreAvailable() throws Exception {
        scheduleBody.set(scheduleBody.get().replace("\"carrierCode\":\"SKMT\"", "\"carrierCode\":\"IC\""));
        var response = get(connectionPath("Gdynia Główna", "Gdańsk Główny", "2026-10-16T08:00"));
        assertEquals(200, response.statusCode());
        assertEquals(0, json.readTree(response.body()).get("proposals").size());
    }

    @Test
    void returnsMinimalEnglishErrorsForUnknownEndpointsAndMethods() throws Exception {
        var missing = get("/api/unknown");
        assertEquals(404, missing.statusCode());
        assertEquals("Endpoint not found.", json.readTree(missing.body()).get("message").asText());
        assertEquals(2, json.readTree(missing.body()).size());
        var wrongMethod = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/connections"))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(405, wrongMethod.statusCode());
        assertEquals("Method not allowed.", json.readTree(wrongMethod.body()).get("message").asText());
        assertEquals(2, json.readTree(wrongMethod.body()).size());
    }

    @Test
    void explicitRecommendedModeMatchesDefault() throws Exception {
        String path = connectionPath("Gdynia Główna", "Gdańsk Główny", "2026-10-16T08:00");
        var defaultResponse = get(path);
        var explicitResponse = get(path + "&mode=recommended");
        assertEquals(200, explicitResponse.statusCode());
        assertEquals(json.readTree(defaultResponse.body()), json.readTree(explicitResponse.body()));
    }

    @Test
    void allModeReturnsEveryDistinctArrivalWithinInclusiveThirtyMinuteWindow() throws Exception {
        scheduleBody.set(routes(
                route(1, "2026-10-16", "07:00:00", "07:30:00", 0, 0, 1, 2, null, null),
                route(2, "2026-10-16", "07:10:00", "07:40:00", 0, 0, 1, 2, null, null),
                route(3, "2026-10-16", "07:20:00", "07:50:00", 0, 0, 1, 2, null, null),
                route(4, "2026-10-16", "07:30:00", "08:00:00", 0, 0, 1, 2, null, null),
                route(4, "2026-10-16", "07:30:00", "08:00:00", 0, 0, 1, 2, null, null),
                route(5, "2026-10-16", "07:00:00", "07:29:59", 0, 0, 1, 2, null, null),
                route(6, "2026-10-16", "07:30:00", "08:00:01", 0, 0, 1, 2, null, null),
                route(7, "2026-10-16", "07:30:00", "08:00:00", 0, 0, 1, 2, null, null)
                        .replace("\"carrierCode\":\"SKMT\"", "\"carrierCode\":\"IC\"")));
        var response = get(connectionPath("Gdynia Główna", "Gdańsk Główny", "2026-10-16T08:00") + "&mode=all");
        assertEquals(200, response.statusCode(), response.body());
        var proposals = json.readTree(response.body()).get("proposals");
        assertEquals(4, proposals.size());
        for (int i = 0; i < 4; i++) {
            assertEquals("ALL", proposals.get(i).get("type").asText());
            assertEquals("TEST-" + (4 - i), proposals.get(i).get("trainNumber").asText());
            assertEquals(i * 10, proposals.get(i).get("bufferMinutes").asInt());
            assertEquals(10, proposals.get(i).size());
        }
    }

    @Test
    void returnsPlatformAndTrackForBothEndsWithArrivalFallback() throws Exception {
        String route = route(1, "2026-10-16", "07:30:00", "07:59:00", 0, 0, 1, 2, null, null)
                .replace("\"departureTime\":", "\"departurePlatform\":\"1\",\"departureTrack\":\"502\",\"departureTime\":")
                .replace("\"arrivalTime\":", "\"departurePlatform\":\"3\",\"departureTrack\":\"501\",\"arrivalTime\":");
        String path = connectionPath("Gdynia Główna", "Gdańsk Główny", "2026-10-16T08:00");
        for (String mode : new String[]{"recommended", "all"}) {
            scheduleBody.set(routes(route));
            var response = get(path + "&mode=" + mode);
            assertEquals(200, response.statusCode(), response.body());
            var proposal = json.readTree(response.body()).get("proposals").get(0);
            assertEquals("1", proposal.get("departurePlatform").asText());
            assertEquals("502", proposal.get("departureTrack").asText());
            assertEquals("3", proposal.get("arrivalPlatform").asText());
            assertEquals("501", proposal.get("arrivalTrack").asText());
        }
        scheduleBody.set(routes(route.replace("\"arrivalTime\":",
                "\"arrivalPlatform\":\"4\",\"arrivalTrack\":\"7\",\"arrivalTime\":")));
        var proposal = json.readTree(get(path).body()).get("proposals").get(0);
        assertEquals("4", proposal.get("arrivalPlatform").asText());
        assertEquals("7", proposal.get("arrivalTrack").asText());
    }

    @Test
    void allModeReturnsPrAndSkmTogether() throws Exception {
        scheduleBody.set(routes(
                route(1, "2026-10-16", "07:30:00", "07:59:00", 0, 0, 1, 2, null, null)
                        .replace("\"carrierCode\":\"SKMT\"", "\"carrierCode\":\"PR\""),
                route(2, "2026-10-16", "07:10:00", "07:55:00", 0, 0, 1, 2, null, null),
                route(3, "2026-10-16", "07:00:00", "07:47:00", 0, 0, 1, 2, null, null)
                        .replace("\"carrierCode\":\"SKMT\"", "\"carrierCode\":\"PR\"")));
        var response = get(connectionPath("Gdynia Główna", "Gdańsk Główny", "2026-10-16T08:00") + "&mode=all");
        assertEquals(200, response.statusCode(), response.body());
        var proposals = json.readTree(response.body()).get("proposals");
        assertEquals(3, proposals.size());
        assertEquals("PR", proposals.get(0).get("carrierCode").asText());
        assertEquals("SKMT", proposals.get(1).get("carrierCode").asText());
        assertEquals("PR", proposals.get(2).get("carrierCode").asText());
        assertTrue(scheduleQuery.get().contains("carriersInclude=PR,SKMT"));
    }

    @Test
    void allModeHandlesWindowAcrossMidnight() throws Exception {
        scheduleBody.set(routes(
                route(1, "2026-10-15", "23:20:00", "23:40:00", 0, 0, 1, 2, null, null),
                route(2, "2026-10-15", "23:30:00", "00:00:00", 0, 1, 1, 2, null, null),
                route(3, "2026-10-15", "23:40:00", "00:10:00", 0, 1, 1, 2, null, null),
                route(4, "2026-10-15", "23:10:00", "23:39:59", 0, 0, 1, 2, null, null)));
        var response = get(connectionPath("Gdynia Główna", "Gdańsk Główny", "2026-10-16T00:10") + "&mode=all");
        assertEquals(200, response.statusCode());
        var proposals = json.readTree(response.body()).get("proposals");
        assertEquals(3, proposals.size());
        assertEquals("2026-10-16T00:10:00", proposals.get(0).get("arrivalTime").asText());
        assertEquals("2026-10-15T23:40:00", proposals.get(2).get("arrivalTime").asText());
    }

    @Test
    void allModeReturnsEmptyListWhenWindowHasNoArrivals() throws Exception {
        scheduleBody.set(routes(route(1, "2026-10-16", "07:00:00", "07:29:59", 0, 0, 1, 2, null, null)));
        var response = get(connectionPath("Gdynia Główna", "Gdańsk Główny", "2026-10-16T08:00") + "&mode=all");
        assertEquals(200, response.statusCode());
        assertEquals(0, json.readTree(response.body()).get("proposals").size());
    }

    @Test
    void rejectsUnknownSearchMode() throws Exception {
        var response = get(connectionPath("Gdynia Główna", "Gdańsk Główny", "2026-10-16T08:00") + "&mode=invalid");
        assertEquals(400, response.statusCode());
        var error = json.readTree(response.body());
        assertEquals(2, error.size());
        assertEquals("mode must be recommended or all.", error.get("message").asText());
    }
}
