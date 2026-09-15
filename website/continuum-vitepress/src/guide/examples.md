---
outline: deep
---

# Examples & Tutorials

This section provides complete examples and tutorials for building applications with Continuum.

## CoolCommerce Example

The [CoolCommerce](https://github.com/MindsIgnited/continuum-examples/tree/main/CoolCommerce) project is a complete e-commerce application demonstrating Continuum's multi-service architecture.

### Architecture

CoolCommerce consists of:
- **ecommerce-main**: Main store functionality with `StoreService`
- **ecommerce-payment**: Payment processing with `MerchantServices`
- **ecommerce-gateway**: Separate Gateway server for frontend access
- **ecommerce-frontend**: TypeScript/Vue.js frontend application

### Key Features Demonstrated

1. **Multi-Service Architecture**: Multiple separate Java services communicating via Continuum
2. **Service-to-Service Communication**: `StoreService` calls `MerchantServices` seamlessly
3. **Frontend Integration**: TypeScript client connecting to Java backend services
4. **Gateway Pattern**: Separate Gateway server for frontend access

### Getting Started with CoolCommerce

1. Clone the repository:
   ```bash
   git clone https://github.com/MindsIgnited/continuum-examples.git
   cd continuum-examples/CoolCommerce
   ```

2. Build the project:
   ```bash
   ./gradlew build
   ```

3. Run with Docker Compose:
   ```bash
   ./gradlew bootBuildImage
   docker-compose up -d
   ```

4. Access the application:
   - Frontend: [http://localhost:9090](http://localhost:9090)
   - Gateway: [localhost:58503](localhost:58503)

See the [CoolCommerce README](https://github.com/MindsIgnited/continuum-examples/blob/main/CoolCommerce/README.md) for detailed setup instructions.

## Building a Simple Service

Let's walk through creating a simple Continuum service from scratch.

### Step 1: Create Spring Boot Application

Create a new Spring Boot application and add Continuum dependencies:

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter'
    implementation "org.kinotic:continuum-core:${continuumVersion}"
    implementation "org.kinotic:continuum-core-vertx:${continuumVersion}"
    implementation "org.kinotic:continuum-gateway:${continuumVersion}"
}
```

### Step 2: Enable Continuum

```java
@SpringBootApplication
@EnableContinuum
@EnableContinuumGateway
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

### Step 3: Create Service Interface

```java
package com.example.api;

import org.kinotic.continuum.api.annotations.Publish;

import java.util.List;

@Publish
public interface TodoService {
    List<Todo> getAllTodos();

    Todo getTodo(Long id);

    Todo createTodo(Todo todo);

    Todo updateTodo(Todo todo);

    void deleteTodo(Long id);
}
```

### Step 4: Implement Service

```java
package com.example.internal.api;

import com.example.api.TodoService;
import com.example.api.Todo;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class DefaultTodoService implements TodoService {
    
    private final ConcurrentHashMap<Long, Todo> todos = new ConcurrentHashMap<>();
    private long nextId = 1;
    
    @Override
    public List<Todo> getAllTodos() {
        return todos.values().stream().collect(Collectors.toList());
    }
    
    @Override
    public Todo getTodo(Long id) {
        return todos.get(id);
    }
    
    @Override
    public Todo createTodo(Todo todo) {
        todo.setId(nextId++);
        todos.put(todo.getId(), todo);
        return todo;
    }
    
    @Override
    public Todo updateTodo(Todo todo) {
        todos.put(todo.getId(), todo);
        return todo;
    }
    
    @Override
    public void deleteTodo(Long id) {
        todos.remove(id);
    }
}
```

### Step 5: Create TypeScript Client

```typescript
import { IServiceProxy, Continuum } from '@kinotic/continuum-client'

export interface ITodoService {
    getAllTodos(): Promise<Todo[]>
    getTodo(id: number): Promise<Todo>
    createTodo(todo: Todo): Promise<Todo>
    updateTodo(todo: Todo): Promise<Todo>
    deleteTodo(id: number): Promise<void>
}

export class TodoService implements ITodoService {
    protected serviceProxy: IServiceProxy
    
    constructor() {
        this.serviceProxy = Continuum.serviceProxy(
            'com.example.api.TodoService'
        )
    }
    
    async getAllTodos(): Promise<Todo[]> {
        return this.serviceProxy.invoke('getAllTodos')
    }
    
    async getTodo(id: number): Promise<Todo> {
        return this.serviceProxy.invoke('getTodo', [id])
    }
    
    async createTodo(todo: Todo): Promise<Todo> {
        return this.serviceProxy.invoke('createTodo', [todo])
    }
    
    async updateTodo(todo: Todo): Promise<Todo> {
        return this.serviceProxy.invoke('updateTodo', [todo])
    }
    
    async deleteTodo(id: number): Promise<void> {
        return this.serviceProxy.invoke('deleteTodo', [id])
    }
}

export const TODO_SERVICE: ITodoService = new TodoService()
```

### Step 6: Connect and Use

```typescript
import { Continuum } from '@kinotic/continuum-client'
import { TODO_SERVICE } from './services/todoService'

// Connect
const connectionInfo = {
    host: 'localhost',
    port: 58503
}
await Continuum.connect(connectionInfo)

// Use the service
const todos = await TODO_SERVICE.getAllTodos()
const newTodo = await TODO_SERVICE.createTodo({ title: 'Learn Continuum', completed: false })
```

## Frontend Integration Tutorial

This tutorial shows how to integrate Continuum into a Vue.js frontend application.

### Step 1: Install Dependencies

```bash
npm install @kinotic/continuum-client
```

### Step 2: Create Connection State

```typescript
// stores/connection.ts
import { ConnectedInfo, ConnectionInfo, Continuum } from '@kinotic/continuum-client'
import { reactive } from 'vue'

export class ConnectionState {
    public connectedInfo: ConnectedInfo | null = null
    private connected: boolean = false
    
    async connect(host: string = 'localhost', port: number = 58503): Promise<void> {
        const connectionInfo: ConnectionInfo = { host, port }
        this.connectedInfo = await Continuum.connect(connectionInfo)
        this.connected = true
    }
    
    isConnected(): boolean {
        return this.connected
    }
}

export const connectionState = reactive(new ConnectionState())
```

### Step 3: Create Service Proxies

```typescript
// services/storeService.ts
import { IServiceProxy, Continuum } from '@kinotic/continuum-client'

export class StoreService {
    protected serviceProxy: IServiceProxy
    
    constructor() {
        this.serviceProxy = Continuum.serviceProxy(
            'com.example.api.StoreService'
        )
    }
    
    async getProducts(): Promise<Product[]> {
        return this.serviceProxy.invoke('getAllProducts')
    }
}
```

### Step 4: Use in Components

```vue
<template>
  <div>
    <div v-for="product in products" :key="product.id">
      {{ product.name }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { connectionState } from '@/stores/connection'
import { STORE_SERVICE } from '@/services/storeService'

const products = ref([])

onMounted(async () => {
  if (!connectionState.isConnected()) {
    await connectionState.connect()
  }
  
  try {
    products.value = await STORE_SERVICE.getProducts()
  } catch (error) {
    console.error('Failed to load products:', error)
  }
})
</script>
```

## Multi-Service Architecture Tutorial

This tutorial demonstrates building a multi-service architecture like CoolCommerce.

### Architecture

- **user-service**: User management
- **product-service**: Product catalog
- **order-service**: Order processing (calls user-service and product-service)
- **gateway-service**: Gateway for frontend access

### Step 1: Create Shared API Module

Create a shared module with service interfaces:

```java
// shared-api module
@Publish
@Proxy
public interface UserService {
    User getUser(Long id);
}

@Publish
@Proxy
public interface ProductService {
    Product getProduct(Long id);
}
```

### Step 2: Implement Services

```java
// user-service
@SpringBootApplication
@EnableContinuum
public class UserServiceApplication {
    // ...
}

@Component
public class DefaultUserService implements UserService {
    // Implementation
}

// product-service
@SpringBootApplication
@EnableContinuum
public class ProductServiceApplication {
    // ...
}

@Component
public class DefaultProductService implements ProductService {
    // Implementation
}
```

### Step 3: Create Order Service

```java
// order-service
@SpringBootApplication
@EnableContinuum
public class OrderServiceApplication {
    // ...
}

@Component
public class DefaultOrderService implements OrderService {
    
    private final UserService userService;  // Injected proxy
    private final ProductService productService;  // Injected proxy
    
    public DefaultOrderService(UserService userService, ProductService productService) {
        this.userService = userService;
        this.productService = productService;
    }
    
    @Override
    public Order createOrder(OrderRequest request) {
        // Call other services as if they were local
        User user = userService.getUser(request.getUserId());
        Product product = productService.getProduct(request.getProductId());
        
        return new Order(user, product, request.getQuantity());
    }
}
```

### Step 4: Create Gateway

```java
// gateway-service
@SpringBootApplication
@EnableContinuum
@EnableContinuumGateway
public class GatewayApplication {
    // Frontend connects here
}
```

### Step 5: Deploy

Deploy each service independently:
- Deploy user-service on port 8081
- Deploy product-service on port 8082
- Deploy order-service on port 8083
- Deploy gateway-service on port 8080 (Gateway on 58503)

Services automatically discover each other through Continuum's clustering.

## Summary

These examples demonstrate:
- **Simple Service**: Basic service creation and client usage
- **Frontend Integration**: Vue.js integration patterns
- **Multi-Service Architecture**: Complex distributed system patterns

For more examples, check out the [CoolCommerce project](https://github.com/MindsIgnited/continuum-examples/tree/main/CoolCommerce) for a complete production-ready example.

## What's Next?

- Explore [Advanced Topics](./advanced) for complex scenarios
