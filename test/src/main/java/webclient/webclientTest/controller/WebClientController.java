package webclient.webclientTest.controller;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import reactor.netty.http.client.HttpClient;


@RestController
public class WebClientController {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    private WebClient webClient;

    @GetMapping("/test")
    public Mono<String> doTest() {
        WebClient client = WebClient.create();
        return client.get()
                .uri("http://localhost:1111/webclient/test-create")
                .retrieve()
                .bodyToMono(String.class);
    }

    @GetMapping("/test2")
    public Mono<String> doTest2() {

        HttpClient httpClient = HttpClient.create()
                .tcpConfiguration(
                        client -> client.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000) //miliseconds
                                .doOnConnected(
                                        conn -> conn.addHandlerLast(new ReadTimeoutHandler(5)) //sec
                                                .addHandlerLast(new WriteTimeoutHandler(60)) //sec
                                )
                );

        //Memory 조정: 2M (default 256KB)
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2*1024*1024))
                .build();

        WebClient client = WebClient.builder()
                .baseUrl("http://localhost:1111")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(
                        (req, next) -> next.exchange(
                                ClientRequest.from(req).header("from", "webclient").build()
                        )
                )
                .filter(
                        ExchangeFilterFunction.ofRequestProcessor(
                                clientRequest -> {
                                    log.info(">>>>>>> REQUEST <<<<<<<");
                                    log.info("Request: {} {}", clientRequest.method(), clientRequest.url());
                                    clientRequest.headers().forEach(
                                            (name, values) -> values.forEach(value -> log.info("{} : {}", name, value))
                                    );
                                    return Mono.just(clientRequest);
                                }
                        )
                )
                .filter(
                        ExchangeFilterFunction.ofResponseProcessor(
                                clientResponse -> {
                                    log.info(">>>>>>> RESPONSE <<<<<<<");
                                    clientResponse.headers().asHttpHeaders().forEach((name, values) -> values.forEach(value -> log.info("{} : {}", name, value)));
                                    return Mono.just(clientResponse);
                                }
                        )
                )
                .exchangeStrategies(exchangeStrategies)
                .build();

        return client.get()
                .uri("/webclient/test-builder")
                .retrieve()
                .bodyToMono(String.class);
    }

    @GetMapping("/test3")
    public Mono<String> doTest3() {
        return webClient.get()
                .uri("/webclient/test-builder")
                .retrieve()
                .bodyToMono(String.class);
    }

}
