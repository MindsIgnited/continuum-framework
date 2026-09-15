---
outline: deep
---

# Events & Streaming (IoT)

Continuum provides powerful event streaming capabilities perfect for IoT applications, real-time data feeds, and event-driven architectures. Events differ from RPC calls in that they are one-way, asynchronous messages that can be broadcast to multiple subscribers.

## Events vs RPC

Understanding when to use events versus RPC is important:

**RPC (Remote Procedure Call):**
- Request-response pattern
- Client sends a request and waits for a response
- Point-to-point communication
- Use when you need a response or acknowledgment

**Events:**
- One-way, asynchronous messages
- Publisher sends events, subscribers receive them
- One-to-many communication (multiple subscribers can receive the same event)
- Use when you need to broadcast information or trigger actions without waiting for responses

## Event Streams Overview

Continuum supports event streaming for:

- **IoT Device Data**: Devices emit sensor readings, status updates, or telemetry
- **Real-Time Notifications**: System events that multiple components need to know about
- **Event-Driven Architecture**: Loose coupling between services through events
- **Live Data Feeds**: Real-time updates that clients subscribe to

## Continuum Resource Identifier (CRI) Format

Events are routed using Continuum Resource Identifiers (CRI), which have a specific format:

```
scheme://[scope@]resourceName[/path][#version]
```

**Format Components:**
- `scheme` - Required. For events, use `stream://` (persistent event streams via EventStreamService)
  - Note: `srv://` is used for RPC service calls, not events
- `[scope@]` - Optional. Used for multi-tenancy, device isolation, or resource scoping
- `resourceName` - Required. The name of the resource (e.g., event type, stream name)
- `[/path]` - Optional. Additional path segments for hierarchical organization
- `[#version]` - Optional. Version identifier for the resource

**Examples:**
- `stream://temperature/sensor-1` - Persistent stream for temperature from sensor-1
- `stream://device@building-a/status` - Persistent stream for device status in building-a scope
- `stream://orders/placed#v1` - Versioned persistent stream for order events

## Receiving Event Streams (Subscriptions)

To receive events, you subscribe to an event stream using a CRI pattern.

### Example: Subscribing to Events

```java
// Subscribe to a persistent stream
eventService.subscribe(CRI.create("stream://temperature/sensor-1"), event -> {
    System.out.println("Temperature reading: " + event.getData());
});

// Subscribe with scope
eventService.subscribe(CRI.create("stream://device@building-a/status"), event -> {
    System.out.println("Device status: " + event.getData());
});
```

```typescript
// Subscribe to events
eventService.subscribe('stream://temperature/sensor-1', (event) => {
    console.log('Temperature reading:', event.data)
})
```

## Emitting Event Streams (Publishing Events)

To publish events, you create an event with a CRI destination and send it.

### Publishing Events

```java
import org.kinotic.continuum.core.api.event.EventService;
import org.kinotic.continuum.core.api.event.CRI;
import org.kinotic.continuum.core.api.event.Event;
import org.kinotic.continuum.core.api.event.DefaultEvent;

@Service
public class SensorService {

    @Autowired
    private EventService eventService;

    public void publishTemperatureReading(String sensorId, double temperature) {
        // Create CRI - using stream:// for persistent events
        CRI destination = CRI.create("stream://temperature/" + sensorId);

        // Create and send the event
        Event<Double> event = new DefaultEvent<>(destination, temperature);
        eventService.send(event).subscribe();
    }
}
```

### Event Metadata

Events can include metadata for additional context:

```java
Event<Double> event = new DefaultEvent<>(destination, temperature);
event.getMetadata().put("timestamp", System.currentTimeMillis());
event.getMetadata().put("sensorType", "temperature");
event.getMetadata().put("location", "building-a-floor-2");

eventService.send(event).subscribe();
```

## Event Streams

Events use the `stream://` scheme and are routed to the EventStreamService. All events are **persistent** and suitable for:
- IoT sensor data
- Audit logs
- Event sourcing
- Any data that needs to be retained
- Real-time data feeds

```java
// Persistent event stream
CRI destination = CRI.create("stream://temperature/sensor-1");
Event<Double> event = new DefaultEvent<>(destination, 72.5);
eventService.send(event).subscribe();
```

## IoT Use Cases

Continuum's event streaming is particularly well-suited for IoT scenarios:

### Sensor Data Collection

```java
// Device publishes sensor readings
public void onSensorReading(String sensorId, String sensorType, double value) {
    CRI destination = CRI.create("stream://" + sensorType + "/" + sensorId);
    Event<Double> event = new DefaultEvent<>(destination, value);
    eventService.send(event).subscribe();
}
```

### Scoped Events (Multi-Tenant)

Use scope for tenant/device isolation:

```java
// Publish with scope for tenant/device isolation
CRI destination = CRI.create("stream://device@" + deviceId + "/status");
Event<DeviceStatus> event = new DefaultEvent<>(destination, status);
eventService.send(event).subscribe();
```

## Differences from RPC

| Feature | RPC | Events |
|---------|-----|--------|
| **Direction** | Bidirectional (request-response) | Unidirectional (one-way) |
| **Communication** | Point-to-point | One-to-many (broadcast) |
| **Response** | Always gets a response | No response expected |
| **Use Case** | Operations that need results | Notifications, data streams |
| **Coupling** | Tight (caller knows callee) | Loose (publisher doesn't know subscribers) |

## When to Use Events vs RPC

**Use Events when:**
- Multiple components need to react to the same occurrence
- You don't need a response or acknowledgment
- You want loose coupling between components
- You're broadcasting information (IoT sensors, system events)
- You need real-time data streams

**Use RPC when:**
- You need a response or result
- You're performing operations (create, update, delete)
- You need to know if an operation succeeded or failed
- Point-to-point communication is sufficient

## Example: Mixed Architecture

Many applications use both RPC and events:

```java
@Service
public class OrderService {
    
    @Autowired
    private EventService eventService;
    
    @Autowired
    private PaymentService paymentService;  // RPC service
    
    public Mono<Order> placeOrder(OrderRequest request) {
        // Use RPC to process payment (need response)
        return paymentService.processPayment(request.getPaymentInfo())
            .map(paymentId -> {
                Order order = createOrder(request, paymentId);
                
                // Use events to notify other systems (no response needed)
                CRI destination = CRI.create("stream://orders/placed");
                Event<Order> event = new DefaultEvent<>(destination, order);
                eventService.send(event).subscribe();
                
                return order;
            });
    }
}
```

In this example:
- **RPC** is used for payment processing (need to know if it succeeded)
- **Events** are used to notify other systems about the order (they just need to know, no response needed)

## Summary

Events and streaming provide powerful capabilities for:

- **IoT Applications**: Device data collection and command distribution
- **Real-Time Updates**: Live data feeds and notifications
- **Event-Driven Architecture**: Loose coupling through asynchronous messaging
- **Broadcasting**: One-to-many communication patterns

Use events when you need to broadcast information or trigger actions without waiting for responses. Use RPC when you need request-response semantics.

## What's Next?

- Explore [Advanced Topics](./advanced) for complex event patterns
