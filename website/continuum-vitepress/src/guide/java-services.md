---
outline: deep
---

# Creating Services (Java)

Creating a Continuum service in Java is simple: define an interface, add the `@Publish` annotation, and implement it. The framework handles service discovery, registration, and all the networking details automatically.

## The `@Publish` Annotation

The `@Publish` annotation tells Continuum that this interface should be made available as a remote service. It's typically used on interfaces, not on the implementing class.

### Basic Usage

```java
import org.kinotic.continuum.api.annotations.Publish;

@Publish
public interface StoreService {
    // ... service methods
}
```

That's it! The service will be automatically discovered and registered when your Spring Boot application starts.

### Service Naming

By default, Continuum uses the package name and class name to identify your service. For example, a service `com.coolcompany.ecommerce_main.api.StoreService` will be available at that fully qualified name.

You can customize the namespace and name if needed:

```java
@Publish(namespace = "com.example", name = "MyStoreService")
public interface StoreService {
    // ...
}
```

But in most cases, the defaults work fine, so you don't need to specify them.

### Service Versioning

You can version your services using the `@Version` annotation:

```java
@Publish
@Version("0.1.0")
public interface StoreService {
    // ...
}
```

This is useful when you need to support multiple versions of a service simultaneously, or when you want to track service evolution.

### Service-to-Service Communication

If your service needs to be called by other Continuum services (not just clients), add the `@Proxy` annotation:

```java
@Publish
@Proxy
public interface MerchantServices {
    // ...
}
```

The `@Proxy` annotation makes the service accessible for service-to-service calls, allowing your Java services to invoke each other directly—even across different applications or processes. You can implement the service interface in one Spring Boot application, and then use the same interface with a Continuum-generated proxy in another application as if the service were local. Continuum handles the remote communication transparently, so your code can call remote service methods without worrying about their actual location or deployment.

## Service Interface Design

Design your service interface like any other Java interface. Continuum supports standard Java types, collections, and reactive types.

### Method Signatures

Your service methods can use any Java types that can be serialized (primitive types, Strings, POJOs, Collections, etc.):

```java
@Publish
public interface StoreService {
    
    // Simple return types
    List<Category> getAllCategories();
    
    // Parameters
    Product getProduct(Long productId);
    
    // Complex types
    Mono<UUID> checkout(CheckoutInfo checkoutInfo);
}
```

### Reactive Types

Continuum supports reactive types for async operations:

- `Mono<T>` - For single async results
- `Flux<T>` - For streaming results
- `CompletableFuture<T>` - Standard Java async type

```java
@Publish
public interface StoreService {
    
    // Reactive single result
    Mono<UUID> checkout(CheckoutInfo checkoutInfo);
    
    // Standard synchronous call
    Product getProduct(Long productId);
}
```

### Security Context (Participant)

For services that need security context (authentication, authorization, tenant information), you can include a `Participant` parameter:

```java
import org.kinotic.continuum.api.security.Participant;

@Publish
public interface SecureService {

    // Participant is automatically injected by the framework
    List<Data> getData(Participant participant);
}
```

The `Participant` parameter is automatically provided by Continuum based on the current security context, so you don't need to pass it from clients.

## Service Implementation

Implement your service interface as a standard Spring component. All standard Spring features work as expected.

### Basic Implementation

```java
import org.springframework.stereotype.Component;

@Component
public class DefaultStoreService implements StoreService {
    
    @Override
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }
    
    @Override
    public Product getProduct(Long productId) {
        return productRepository.findById(productId).orElse(null);
    }
    
    @Override
    public Mono<UUID> checkout(CheckoutInfo checkoutInfo) {
        // Your business logic here
        return Mono.just(UUID.randomUUID());
    }
}
```

That's it! No special Continuum-specific code needed. Just implement the interface methods with your business logic.

### Dependency Injection

Standard Spring dependency injection works normally:

```java
@Component
@RequiredArgsConstructor  // Lombok example
public class DefaultStoreService implements StoreService {
    
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final MerchantServices merchantServices;  // Another Continuum service!
    
    @Override
    public Mono<UUID> checkout(CheckoutInfo checkoutInfo) {
        // Call another Continuum service
        return merchantServices.processPayment(
            checkoutInfo.getCustomerInfo(),
            checkoutInfo.getPaymentInfo(),
            calculateTotal(checkoutInfo)
        );
    }
}
```

You can inject repositories, other services (including other Continuum services), or any Spring-managed bean just like normal.

### Calling Other Microservices 

You can call other Continuum services directly by injecting them as Spring beans—just like normal, even if they're implemented in another process or microservice. In this case, you'll need to make sure the service interface is shared in both projects (either by using a shared library module, or copying the interface). Continuum automatically creates a proxy, so you can interact with the remote service as if it were local.

> ℹ️ **Note:** This approach works across processes or distributed services, not just within the same Spring application.  
> To see a full, production-ready example, check out the [CoolCommerce project](https://github.com/MindsIgnited/continuum-examples/tree/main/CoolCommerce).


When you need to call another Continuum service, just inject it like any other Spring bean:

```java
@Component
public class DefaultStoreService implements StoreService {
    
    private final MerchantServices merchantServices;
    
    // MerchantServices is automatically available because it's @Publish
    // Continuum creates a proxy for it
}
```

Continuum automatically creates proxies for published services, so you can use them like local beans.

## Real-World Example

Let's look at a complete example from the CoolCommerce project:

### Service Interface

```java
package com.coolcompany.ecommerce_main.api;

import com.coolcompany.ecommerce_main.api.domain.Category;
import com.coolcompany.ecommerce_main.api.domain.CheckoutInfo;
import com.coolcompany.ecommerce_main.api.domain.Product;
import org.kinotic.continuum.api.annotations.Publish;
import org.kinotic.continuum.api.annotations.Version;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Publish
@Version("0.1.0")
public interface StoreService {

    List<Category> getAllCategories();

    List<Product> getAllProductsForCategoryId(Long categoryId);

    Product getProduct(Long productId);

    Mono<UUID> checkout(CheckoutInfo checkoutInfo);
}
```

### Service Implementation

```java
package com.coolcompany.ecommerce_main.internal.api;

import com.coolcompany.ecommerce_main.api.StoreService;
import com.coolcompany.ecommerce_main.api.domain.Category;
import com.coolcompany.ecommerce_main.api.domain.CheckoutInfo;
import com.coolcompany.ecommerce_main.api.domain.Product;
import com.coolcompany.ecommerce_main.internal.repositories.CategoryRepository;
import com.coolcompany.ecommerce_main.internal.repositories.ProductRepository;
import com.coolcompany.ecommerce_payment.api.MerchantServices;  // Another service!
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DefaultStoreService implements StoreService {
    
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final MerchantServices merchantServices;  // Injected automatically
    
    @Override
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }
    
    @Override
    public List<Product> getAllProductsForCategoryId(Long categoryId) {
        return productRepository.findByCategoryId(categoryId);
    }
    
    @Override
    public Product getProduct(Long productId) {
        return productRepository.findById(productId).orElse(null);
    }
    
    @Override
    public Mono<UUID> checkout(CheckoutInfo checkoutInfo) {
        // Calculate total and call payment service
        BigDecimal total = calculateTotal(checkoutInfo);
        return merchantServices.processPayment(
            checkoutInfo.getCustomerInfo(),
            checkoutInfo.getPaymentInfo(),
            total
        );
    }
}
```

Notice how:
- The service interface uses `@Publish` and `@Version`
- The implementation is a standard Spring `@Component`
- Dependency injection works normally (repositories and other services)
- The service can call other Continuum services (`MerchantServices`) like local beans
- No Continuum-specific code in the implementation - just business logic

## Service Registration and Discovery

Continuum automatically:
- Scans for `@Publish` annotated interfaces
- Registers them when your application starts
- Makes them available for remote calls
- Creates proxies for service-to-service communication

You don't need to do anything special - just start your Spring Boot application and your services are available.

## Summary

Creating a Continuum service is simple:

1. **Define an interface** with your service methods
2. **Add `@Publish`** to make it available remotely
3. **Optionally add `@Version`** for versioning
4. **Optionally add `@Proxy`** if other services need to call it
5. **Implement it** as a Spring `@Component`
6. **Use dependency injection** like normal Spring code

The framework handles everything else. You focus on your business logic, Continuum handles the infrastructure.

## What's Next?

- Learn about [using services from clients](./clients)
- Explore [RPC communication patterns](./rpc-patterns)
- See [advanced topics](./advanced) for multi-service architectures
