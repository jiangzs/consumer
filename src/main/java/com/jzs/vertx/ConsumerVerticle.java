package com.jzs.vertx;

import com.hazelcast.config.Config;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.HystrixMetricHandler;
import io.vertx.core.*;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.ext.web.Router;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by jiangzs@gmail.com on 2017/4/21.
 */
@Slf4j
public class ConsumerVerticle extends AbstractVerticle {

    private final String EVENT_QUEUE = new String("work");

    public static void main(String[] args) {

        Config hazelcastConfig = new Config();
        hazelcastConfig.getNetworkConfig().getJoin().getTcpIpConfig().addMember("172.21.230.157").setEnabled(true);
//        hazelcastConfig.getNetworkConfig().getJoin().getTcpIpConfig().addMember("192.168.99.1").setEnabled(true);
//        hazelcastConfig.getNetworkConfig().getJoin().getTcpIpConfig().addMember("127.0.0.1").setEnabled(true);
        hazelcastConfig.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);

        ClusterManager mgr = new HazelcastClusterManager(hazelcastConfig);
        VertxOptions options = new VertxOptions().setClusterManager(mgr);

        DropwizardMetricsOptions metricsOptions = new DropwizardMetricsOptions();
        metricsOptions.setJmxEnabled(true);
        metricsOptions.setEnabled(true);

        options.setMetricsOptions(metricsOptions);

        Vertx.clusteredVertx(options, res -> {

            if (res.succeeded()) {
                Vertx vertx = res.result();
                DeploymentOptions doptions = new DeploymentOptions().setWorker(true);
                vertx.deployVerticle(new ConsumerVerticle(), doptions);

                Router router = Router.router(vertx);
                router.get("/hystrix-metrics").handler(HystrixMetricHandler.create(vertx));
                vertx.createHttpServer()
                        .requestHandler(router::accept)
                        .listen(8082);
            } else {

                log.error("cluster vertx error: {}", res.cause().getMessage());
            }
        });
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        super.start(startFuture);

        final CircuitBreaker breaker = CircuitBreaker.create("Consumer.breaker", vertx, new CircuitBreakerOptions().setTimeout(10))
                .openHandler(open ->
                        log.info("Consumer OPEN OPEN OPEN OPEN")
                )
                .closeHandler(close ->
                        log.info("Consumer CLOSE CLOSE CLOSE")
                )
                .halfOpenHandler(half ->
                        log.info("Consumer HALF HALF HALF")
                );


        startFuture.compose((Void nofuture) -> {

            final EventBus eb = vertx.eventBus();

            final Handler<Message<String>> eventHandler = event -> {

//                log.info("handle   -- {}", event.body());

//                vertx.createHttpClient().getNow(80, "www.baidu.com", "/", response -> {
//                    if (response.statusCode() != 200) {
//                        log.error("statusCode {}  -- {}", response.statusCode(), event.body());
//                    } else {
//                        response.exceptionHandler(v -> {
//                            log.error("exceptionHandler   -- {}", event.body());
//                        }).bodyHandler(buffer -> {
//                            log.info("bodyHandler   -- {}", event.body());
//                            event.reply(" response :".concat(event.body()).concat(" from ").concat(deploymentID()));
//                        });
//                    }
//                });

                event.reply(" response :".concat(event.body()).concat(" from ").concat(deploymentID()));

//                breaker.executeWithFallback(future -> {
//                    Observable.<String>unsafeCreate(s->{
//                        event.reply(" response :".concat(event.body()).concat(" from ").concat(deploymentID()), ar -> {
//                            if (ar.succeeded()) {
//                                s.onNext("success");
//                            } else {
//                                s.onNext("error");
//                            }
//                            s.onCompleted();
//                        });
//                    }).toBlocking().subscribe(s->{
//                        future.complete(s);
//                    });
//                }, fallback -> {
//                    return "fallback";
//                }).setHandler(ar -> {
//                    log.info("ar {}", ar.result());
//                });


            };

            eb.consumer("work", eventHandler);

            log.info("Consumer started id:{}",deploymentID());

        }, null);
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        super.stop(stopFuture);
        stopFuture.compose(event -> log.info("stop Consumer "), null);

    }
}
