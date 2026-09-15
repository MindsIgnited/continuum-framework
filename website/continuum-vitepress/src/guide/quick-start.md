---
outline: deep
---

# Quick Start

This guide will get you up and running with Continuum quickly. Continuum supports different deployment patterns, so choose the path that matches what you're building. Each path includes step-by-step instructions to get you from zero to a working Continuum application.

## Choose Your Path

What do you want to build? Click on a path below to expand the step-by-step instructions.

<details>
<summary><strong>Path 1: Java backend with a web frontend</strong></summary>

Building a full-stack application with Java services and a Vue/React/etc. frontend

### Prerequisites

- Java 21 or higher
- A Spring Boot application (or create a new one)
- npm, pnpm, or yarn (to install the Continuum client library in your frontend)

### Step 1: Add Dependencies

Add Continuum dependencies to your `build.gradle` (or equivalent Maven dependencies):

```gradle
dependencies {
    implementation "org.kinotic:continuum-core:${continuumVersion}"
    implementation "org.kinotic:continuum-core-vertx:${continuumVersion}"
    implementation "org.kinotic:continuum-gateway:${continuumVersion}"
    // ... your other dependencies
}
```

Replace `${continuumVersion}` with the latest Continuum version.

### Step 2: Enable Continuum and Gateway

In your main Spring Boot application class, add the `@EnableContinuum` and `@EnableContinuumGateway` annotations:

```java
import org.kinotic.continuum.api.annotations.EnableContinuum;
import org.kinotic.continuum.gateway.api.annotations.EnableContinuumGateway;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableContinuum
@EnableContinuumGateway
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

The `@EnableContinuum` annotation enables Continuum in your application. The `@EnableContinuumGateway` annotation embeds the Gateway in your server, allowing frontend clients to connect.

### Step 3: Create Your First Service

Create a service interface and add the `@Publish` annotation:

```java
import org.kinotic.continuum.api.annotations.Publish;

@Publish
public interface HelloService {
    String sayHello(String name);
}
```

That's it! The `@Publish` annotation tells Continuum to make this service available remotely. The service will be automatically discovered and registered.

### Step 4: Implement the Service

Implement your service interface as a standard Spring component:

```java
import org.springframework.stereotype.Component;

@Component
public class DefaultHelloService implements HelloService {
    
    @Override
    public String sayHello(String name) {
        return "Hello, " + name + "!";
    }
}
```

Standard Spring dependency injection works as normal. The framework handles all the complexity of making this service available remotely.

### Step 5: Frontend Setup

Install the Continuum client library in your frontend project:

```bash
npm install @kinotic/continuum-client
# or
pnpm add @kinotic/continuum-client
# or
yarn add @kinotic/continuum-client
```

### Step 6: Connect and Call Your Service

In your frontend code (Vue, React, etc.), connect to Continuum and create a service proxy:

```typescript
import { Continuum, IServiceProxy } from '@kinotic/continuum-client'

// Connect to the backend
const connectionInfo = {
    host: 'localhost',
    port: 58503  // Default Continuum Gateway port
}

await Continuum.connect(connectionInfo)

// Create a typed service proxy
interface IHelloService {
    sayHello(name: string): Promise<string>
}

class HelloService implements IHelloService {
    protected serviceProxy: IServiceProxy
    
    constructor() {
        this.serviceProxy = Continuum.serviceProxy(
            'com.yourpackage.HelloService'
        )
    }
    
    async sayHello(name: string): Promise<string> {
        return this.serviceProxy.invoke('sayHello', [name])
    }
}

const HELLO_SERVICE = new HelloService()

// Call your service
const greeting = await HELLO_SERVICE.sayHello('World')
console.log(greeting)  // "Hello, World!"
```

### Step 7: Run and Test

1. Start your Spring Boot application
2. The Gateway will start on port 58503 (default)
3. In your frontend, connect and call your service

You now have a working Continuum application!

### What's Next?

- Learn more about [creating services](./java-services)
- Explore [client usage patterns](./clients)
- See [RPC communication patterns](./rpc-patterns)

</details>

<details>
<summary><strong>Path 2: Node.js or Bun backend with a web frontend</strong></summary>

Building with a Node.js/Bun backend and Vue/React/etc. frontend

This path is for developers building backend services with Node.js or Bun. The Continuum Gateway serves as a router between your clients and your microservices, providing security and observability.

### Prerequisites

- Docker (for the Gateway server)
- Node.js 18+ or Bun (for your backend service)
- A frontend framework (Vue, React, etc.)

### Step 1: Set Up the Gateway Server

```bash
docker run -p 58503:58503 kinoticai/continuum-gateway-server:latest
```

The Gateway acts as a router, allowing your Node.js/Bun services and frontend clients to communicate through Continuum.

### Step 2: Node.js/Bun Backend Setup

In your Node.js/Bun project, install the Continuum client:

```bash
npm install @kinotic/continuum-client
```

### Step 3: Create and Publish Services

Create your service in Node.js/Bun. (Note: Full Node.js/Bun server support documentation is coming. For now, you can connect Node.js/Bun services through the Gateway.)

### Step 4: Frontend Setup

Set up your Vue/React/etc. frontend following the same pattern as Path 1 (Steps 5-6), connecting to the Gateway server.

### Step 5: Run and Test

1. Start the Gateway server (Java application)
2. Start your Node.js/Bun backend
3. Start your frontend
4. Connect and test

### What's Next?

- Explore [client usage patterns](./clients)
- Check back for upcoming Node.js/Bun server documentation

</details>

---
