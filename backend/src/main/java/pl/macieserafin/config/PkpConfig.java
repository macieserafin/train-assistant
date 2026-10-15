package pl.macieserafin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class PkpConfig {
    @Bean
    RestClient pkpRestClient(RestClient.Builder builder, SslBundles sslBundles,
                             @Value("${pkp.base-url}") String baseUrl,
                             @Value("${pkp.connect-timeout}") Duration connectTimeout,
                             @Value("${pkp.read-timeout}") Duration readTimeout) {
        var httpClient = HttpClient.newBuilder()
                .sslContext(sslBundles.getBundle("pkp").createSslContext())
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return builder.baseUrl(baseUrl).requestFactory(factory).build();
    }
}
