package aforo.metering.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(Duration.ofSeconds(10))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer
                                .defaultCodecs()
                                .maxInMemorySize(5 * 1024 * 1024))
                        .build());
    }

    @Bean(name = "billableMetricWebClient")
    public WebClient billableMetricWebClient(
            WebClient.Builder builder,
            @Value("${aforo.clients.billableMetrics.baseUrl}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }

    @Bean(name = "ratePlanWebClient")
    public WebClient ratePlanWebClient(
            WebClient.Builder builder,
            @Value("${aforo.clients.productRatePlan.baseUrl}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }

    @Bean(name = "subscriptionWebClient")
    public WebClient subscriptionWebClient(
            WebClient.Builder builder,
            @Value("${aforo.clients.subscription.baseUrl}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }

    @Bean(name = "customerWebClient")
    public WebClient customerWebClient(
            WebClient.Builder builder,
            @Value("${aforo.clients.customer.baseUrl}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
