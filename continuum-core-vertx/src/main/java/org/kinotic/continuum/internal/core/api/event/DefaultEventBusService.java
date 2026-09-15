/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kinotic.continuum.internal.core.api.event;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.spi.cluster.RegistrationInfo;
import io.vertx.core.tracing.TracingPolicy;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.Validate;
import org.kinotic.continuum.core.api.event.Event;
import org.kinotic.continuum.core.api.event.EventBusService;
import org.kinotic.continuum.core.api.event.EventConstants;
import org.kinotic.continuum.core.api.event.ListenerStatus;
import org.kinotic.continuum.internal.ContinuumIgniteClusterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default implementation of {@link EventBusService} using the vertx {@link io.vertx.core.eventbus.EventBus} as a backend
 *
 *
 * Created by navid on 11/5/19
 */
@Component
public class DefaultEventBusService implements EventBusService {

    private static final Logger log = LoggerFactory.getLogger(DefaultEventBusService.class);
    @Autowired(required = false) // not available when clustering is disabled
    private ContinuumIgniteClusterManager clusterManager;
    private Scheduler scheduler;
    @Autowired
    private Vertx vertx;
    /**
     * Reference counted set of event bus addresses this node hosts a local handler for.
     * An entry is added by {@link #_listen} when a consumer's handler is attached on this node and removed
     * when that consumer is unregistered, so membership means "a local consumer exists at this address".
     * This is local by construction: {@link #_listen} only ever registers consumers on this node, other nodes'
     * registrations live solely in the cluster's subscription registry. The send path uses this to prefer
     * local delivery for service RPCs and avoid an unnecessary cluster hop.
     */
    private final Map<String, Integer> localListenerCounts = new ConcurrentHashMap<>();

    @PostConstruct
    public void init(){
        scheduler = Schedulers.fromExecutor(command -> vertx.executeBlocking(() -> {
            command.run();
            return null;
        }));
    }

    @Override
    public Mono<Boolean> isAnybodyListening(String cri) {
        Validate.notEmpty(cri, "The cri must be provided");
        if(clusterManager == null){
            throw new IllegalStateException("This method is not available when clustering is disabled");
        }
        return Mono.create(sink -> {
            Promise<List<RegistrationInfo>> promise = Promise.promise();
            clusterManager.getRegistrations(cri, promise);
            promise.future().onComplete(ar -> {
                if(ar.succeeded()){
                    List<RegistrationInfo> registrations = ar.result();
                    sink.success(registrations != null && !registrations.isEmpty());
                }else{
                    sink.error(ar.cause());
                }
            });
        });
    }

    @Override
    public Flux<Event<byte[]>> listen(String cri) {
        Validate.notEmpty(cri, "The cri must be provided");

        return _listen(cri, null);
    }

    @Override
    public Mono<Flux<Event<byte[]>>> listenWithAck(String cri) {
        Validate.notEmpty(cri, "The cri must be provided");

        return Mono.create(sink -> {
            final MessageConsumer<byte[]> consumer = vertx.eventBus().consumer(cri);
            final ConnectableFlux<Event<byte[]>> flux = _listen(cri, consumer).publish();
            consumer.completion().onComplete(ar ->{
                if(ar.succeeded()){
                    sink.success(flux);
                }else{
                    sink.error(ar.cause());
                }
            });
            flux.connect(); // we have to connect now so flux create will be signaled and vertx consumer handler will be set
        });
    }

    @Override
    public Flux<ListenerStatus> monitorListenerStatus(String cri) {
        Validate.notEmpty(cri, "The cri must be provided");
        if(clusterManager == null){
            throw new IllegalStateException("This method is not available when clustering is disabled");
        }
        // Fed by the registration updates the cluster manager already receives for message routing.
        // Emits the current status on subscribe and the resulting status of every registration change
        // after that, so consecutive duplicates are possible.
        return clusterManager.statusFlux(cri);
    }

    @Override
    public void send(Event<byte[]> event) {
        String baseResource = event.cri().baseResource();
        DeliveryOptions deliveryOptions = createDeliveryOptions(event, baseResource);
        vertx.eventBus().send(baseResource,
                              event.data(),
                              deliveryOptions);
    }

    @Override
    public Mono<Void> sendWithAck(Event<byte[]> event) {
        Validate.notNull(event, "Event must not be null");
        return Mono.create(sink -> {
            String baseResource = event.cri().baseResource();
            DeliveryOptions deliveryOptions = createDeliveryOptions(event, baseResource);
            // We expect that a response will be sent upon receipt. This will happen automatically if the listener is created with this class.
            vertx.eventBus()
                 .request(baseResource,
                          event.data(),
                          deliveryOptions)
                 .onComplete(reply -> {
                     if(reply.succeeded()){
                         sink.success();
                     }else{
                         sink.error(reply.cause());
                     }
                 });
        }).subscribeOn(scheduler).then();
    }

    private Flux<Event<byte[]>> _listen(String cri, MessageConsumer<byte[]> vertxEventBusConsumer) {
        MessageConsumer<byte[]> consumer;
        if(vertxEventBusConsumer != null){
            consumer = vertxEventBusConsumer;
        }else{
            consumer = vertx.eventBus().consumer(cri);
        }

        Flux<Event<byte[]>> ret = Flux.create(fluxSink -> {
            // Record that this node now hosts a local handler for cri. Done here, where the consumer's handler is
            // attached, and undone in onDispose, where the consumer is unregistered, so the count is coupled to the
            // consumer's actual registration lifecycle on this node.
            localListenerCounts.merge(cri, 1, Integer::sum);
            final AtomicBoolean unregistered = new AtomicBoolean(false);

            // Setup all required handlers that are needed prior to consuming messages
            fluxSink.onDispose(() -> {
                // Decrement exactly once on the first unregister, removing the entry when no local handlers remain.
                if(unregistered.compareAndSet(false, true)){
                    localListenerCounts.computeIfPresent(cri, (k, n) -> n == 1 ? null : n - 1);
                }
                consumer.unregister();
            });

            // TODO: deal with back pressure properly.. ?
            //fluxSink.onRequest()

            consumer.exceptionHandler(fluxSink::error);
            consumer.endHandler(event -> fluxSink.complete()); // this should never occur, but we handle in case..

            // now activate handler to start consuming messages
            consumer.handler(message -> {
                // ack that we received the message if desired by sender.
                if (message.replyAddress() != null){
                    message.reply(null);
                }

                if(!fluxSink.isCancelled()) {
                    vertx.executeBlocking(() -> fluxSink.next(new MessageEventAdapter<>(message)));
                }
            });
        });

        return ret; // ensure message delivery happens on vertx event loop, not sure but this by itself did not move the next above to the work loop
    }

    private DeliveryOptions createDeliveryOptions(Event<?> event, String baseResource){
        DeliveryOptions deliveryOptions = new DeliveryOptions();
        deliveryOptions.setTracingPolicy(TracingPolicy.IGNORE);
        // fast path for MultiMapMetadataAdapter's
        if(event.metadata() instanceof MultiMapMetadataAdapter){
            deliveryOptions.setHeaders(((MultiMapMetadataAdapter)event.metadata()).getMultiMap());
        }else{
            for(Map.Entry<String, String> entry: event.metadata()){
                deliveryOptions.addHeader(entry.getKey(), entry.getValue());
            }
        }
        deliveryOptions.addHeader(EventConstants.CRI_HEADER, event.cri().raw());

        // Prefer local delivery when this node already hosts a handler for the target service address.
        // In the clustered event bus the point-to-point selector round-robins sends across every node
        // registered for the address, so without this ~half of a node's own service calls get shipped to a
        // remote node. localOnly=true bypasses the cluster selector and delivers to the local handler.
        // Only set when this node actually hosts a handler, otherwise localOnly would fail with NO_HANDLERS.
        if(shouldPreferLocalDelivery(event, baseResource)){
            deliveryOptions.setLocalOnly(true);
        }

        return deliveryOptions;
    }

    /**
     * Determines if a send to the given {@code baseResource} should be delivered to a local handler only.
     * True only for point-to-point {@link EventConstants#SERVICE_DESTINATION_SCHEME service} sends whose
     * address is currently hosted by a local handler on this node. This is a lock free
     * {@link Map#containsKey} plus a scheme check, no cluster or registry query.
     * Package private for testing.
     */
    boolean shouldPreferLocalDelivery(Event<?> event, String baseResource){
        return EventConstants.SERVICE_DESTINATION_SCHEME.equals(event.cri().scheme())
                && localListenerCounts.containsKey(baseResource);
    }

}
