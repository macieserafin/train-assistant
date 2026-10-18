package pl.macieserafin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorsTests {
    @LocalServerPort
    private int port;

    private HttpResponse<String> preflight(String origin, String method) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/api/stations"))
                .header("Origin", origin)
                .header("Access-Control-Request-Method", method)
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void permitsIosGetRequests() throws Exception {
        var response = preflight("capacitor://localhost", "GET");
        assertEquals(200, response.statusCode());
        assertEquals("capacitor://localhost",
                response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
    }

    @Test
    void rejectsUnknownOriginsAndWriteMethods() throws Exception {
        assertEquals(403, preflight("https://untrusted.example", "GET").statusCode());
        assertEquals(403, preflight("capacitor://localhost", "POST").statusCode());
    }

    @Test
    void healthCheckDoesNotRequireExternalApi() throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/healthz")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("{\"status\":\"ok\"}", response.body());
    }
}
