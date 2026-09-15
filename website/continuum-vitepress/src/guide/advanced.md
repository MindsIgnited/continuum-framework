---
outline: deep
---

# Advanced Topics

This section covers advanced Continuum patterns and configurations for complex architectures.

## Multi-Service Architecture

Continuum excels at building distributed systems with multiple separate Java microservices. Each service is a separate Spring Boot application that can communicate with other services directly.

### Architecture Overview

In a multi-service architecture:
- Each service is a separate Spring Boot application
- Services communicate directly with each other (peer-to-peer)
- Gateway is typically embedded in one service or runs as a separate service for frontend access
- Services can be deployed independently and scaled separately

### CoolCommerce Example

The [CoolCommerce](https://github.com/MindsIgnited/continuum-examples/tree/main/CoolCommerce) project demonstrates a complete multi-service architecture:

**Service Structure:**
- `ecommerce-main`: Main store functionality (`StoreService`)
- `ecommerce-payment`: Payment processing (`MerchantServices`)
- `ecommerce-gateway`: Separate Gateway server for frontend access
- `ecommerce-frontend`: TypeScript/Vue frontend

**Each Service:**
```java
// ecommerce-main/src/main/groovy/.../EcommerceMainApplication.groovy
@SpringBootApplication
@EnableContinuum  // Service-to-service communication
class EcommerceMainApplication {
    // ...
}

// ecommerce-payment/src/main/groovy/.../EcommercePaymentApplication.groovy
@SpringBootApplication
@EnableContinuum  // Service-to-service communication
class EcommercePaymentApplication {
    // ...
}

// ecommerce-gateway/src/main/groovy/.../EcommerceGatewayApplication.groovy
@SpringBootApplication
@EnableContinuum
@EnableContinuumGateway  // Gateway for frontend access
class EcommerceGatewayApplication {
    // ...
}
```

### Service-to-Service Communication

Services communicate by sharing interface definitions and using `@Proxy`:

**Shared Interface** (in `ecommerce-payment-api` module):
```java
@Publish
@Proxy  // Makes this available to other services
public interface MerchantServices {
    Mono<UUID> processPayment(CustomerInfo info, PaymentInfo payment, BigDecimal amount);
}
```

**Service Implementation** (in `ecommerce-payment`):
```java
@Component
public class DefaultMerchantServices implements MerchantServices {
    // Implementation
}
```

**Service Consumer** (in `ecommerce-main`):
```java
@Component
public class DefaultStoreService implements StoreService {
    
    // Inject the other service - Continuum creates a proxy automatically
    private final MerchantServices merchantServices;
    
    public DefaultStoreService(MerchantServices merchantServices) {
        this.merchantServices = merchantServices;
    }
    
    @Override
    public Mono<UUID> checkout(CheckoutInfo checkoutInfo) {
        // Call the remote service as if it were local
        return merchantServices.processPayment(...);
    }
}
```

### Gateway Deployment Options

**Option 1: Embedded Gateway** (like Structures)
```java
@SpringBootApplication
@EnableContinuum
@EnableContinuumGateway  // Gateway embedded in main service
public class MyApplication {
    // Frontend connects to this service
}
```

**Option 2: Separate Gateway** (like CoolCommerce)
```java
// Gateway service
@SpringBootApplication
@EnableContinuum
@EnableContinuumGateway
public class GatewayApplication {
    // Frontend connects here
}

// Other services
@SpringBootApplication
@EnableContinuum  // No Gateway - services only
public class MainServiceApplication {
    // Services communicate directly, no Gateway needed
}
```

### When to Use Multi-Service Architecture

Use this pattern when:
- You need to scale services independently
- Different services have different responsibilities
- Teams want to deploy services separately
- You need service isolation for security or compliance
- You want to use different technologies per service (future: different languages)

### Deployment Considerations

- **Service Discovery**: Continuum handles automatic service discovery through clustering
- **Independent Deployment**: Each service can be deployed/updated independently
- **Shared Interfaces**: Services need access to interface definitions (shared library/module)
- **Gateway Access**: Frontend needs to connect through Gateway (embedded or separate)

## Security and Participant Context

Continuum provides security context through the `Participant` interface.

### Participant Parameter

Services can receive security context via `Participant`:

```java
@Publish
public interface SecureService {
    List<Data> getData(Participant participant);
}
```

The `Participant` contains:
- User identification
- Tenant information (for multi-tenancy)
- Roles and permissions
- Additional metadata

### Participant Injection

The framework automatically injects `Participant` based on the current security context:

```java
@Component
public class DefaultSecureService implements SecureService {
    
    @Override
    public List<Data> getData(Participant participant) {
        // Use participant for authorization, tenant filtering, etc.
        String tenantId = participant.getTenantId();
        Set<String> roles = participant.getRoles();
        
        return dataRepository.findByTenantAndRoles(tenantId, roles);
    }
}
```

### Security Best Practices

- Always validate participant context in service methods
- Use tenant isolation for multi-tenant applications
- Check roles/permissions before performing operations
- Never trust client-provided security information—always use the injected `Participant`

## Error Handling Strategies

### Service-Level Error Handling

Handle errors in your service implementations:

```java
@Component
public class DefaultStoreService implements StoreService {
    
    @Override
    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
```

### Reactive Error Handling

For reactive types, handle errors in the reactive chain:

```java
@Override
public Mono<UUID> checkout(CheckoutInfo checkoutInfo) {
    return merchantServices.processPayment(...)
        .onErrorMap(PaymentException.class, e -> new CheckoutException("Payment failed", e))
        .doOnError(e -> logger.error("Checkout failed", e));
}
```

### Client-Side Error Handling

Clients should always handle errors:

```typescript
try {
    const product = await storeService.invoke('getProduct', [productId])
    // Use product
} catch (error) {
    if (error instanceof ProductNotFoundException) {
        // Handle not found
    } else {
        // Handle other errors
    }
}
```

### Async Operations

Use reactive types (`Mono`, `Flux`) for non-blocking operations:

```java
@Publish
public interface DataService {
    // Non-blocking async operation
    Mono<Result> processData(Data data);
}
```

## Extension Points

Continuum is designed with extension points for advanced use cases:

- **Custom Transports**: Implement custom transport layers (future)
- **Custom Serialization**: Add support for additional serialization formats (future)
- **Context Interceptors**: Intercept and modify request context
- **Service Interceptors**: Add cross-cutting concerns to service calls

## Summary

Advanced Continuum patterns enable:
- **Multi-service architectures** with independent deployment
- **Secure, multi-tenant** applications with participant context
- **High-performance** systems with clustering and async operations
- **Testable** applications with comprehensive testing strategies

## What's Next?

- Check out [Examples](./examples) for complete working examples
- See the [CoolCommerce project](https://github.com/MindsIgnited/continuum-examples/tree/main/CoolCommerce) for a full multi-service implementation
