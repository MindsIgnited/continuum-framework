---
outline: deep
---

# What is Continuum?

Continuum is an RPC and IoT framework that enables seamless, type-safe communication between services and devices. It provides a powerful yet simple way to build distributed applications, microservices architectures, and IoT systems without getting bogged down in the complexities of networking, transport protocols, or serialization.

## Core Philosophy: The Framework Gets Out of Your Way

The central idea behind Continuum is simple: **you shouldn't have to think about how things work, you should just be able to use them**.

When you need dependency injection, you don't think about how the DI framework wires up your dependencies. You just use `@Autowired` or constructor injection, and it works. Continuum follows the same philosophy.

Want to expose a service remotely? Just add `@Publish` to your interface. That's it. The framework handles service discovery, serialization, transport, routing, and all the infrastructure details—you focus on building your application logic.

This means:
- **No boilerplate**: Write your service interface, implement it, and you're done
- **Minimal configuration needed**: Sensible defaults work out of the box
- **No network knowledge required**: You don't need to understand transport protocols or service discovery to get started
- **When you need depth, it's there**: The documentation covers internals, but you don't need them to use Continuum effectively

## Key Value Propositions

### Build Faster

Stop writing boilerplate code for service communication. Continuum eliminates the need for REST endpoints, message queues setup, or manual serialization. Define your service interface, and the framework handles the rest.

### Type Safe Across Languages

Write services in Java and call them from TypeScript with full type safety. Or mix and match—Continuum's polyglot support means you can use the right language for each part of your system while maintaining type safety across boundaries.

### Real Time Built In

Continuum provides native support for streaming responses and event driven communication—perfect for IoT applications where devices need to send continuous data streams or receive real time updates.

### Polyglot Support

Currently supporting **Java** and **TypeScript**, with **Python** and **Golang** coming soon. This flexibility means you can choose the best language for each component of your system.

### Minimal Configuration

Sensible defaults mean you can get started immediately. Configure only what you need to customize everything else just works.

## What Problems Does Continuum Solve?

### Microservices Communication Complexity

Building microservices means services need to talk to each other. Traditionally, this means REST APIs, message queues, service discovery, load balancing, and more. Continuum simplifies this to "publish a service and call it"—all the infrastructure is handled automatically.

### IoT Device Integration

IoT applications need to handle streams of data from devices, send commands to devices, and manage connections. Continuum's event streaming and RPC capabilities are built specifically for these scenarios.

### Cross Language Communication

Want to write your backend in Java but your frontend in TypeScript? Need to call a service from Python? Continuum's type safe RPC works across language boundaries, eliminating the need for manually defined APIs and client libraries.

### Building Distributed Systems

Distributed systems are complex. Continuum abstracts away networking details, transport protocols, serialization formats, and service discovery, letting you focus on your business logic.

## Who Is Continuum For?

Continuum is designed for:

- **Microservices developers** building distributed systems who want to focus on business logic, not infrastructure
- **IoT application developers** who need real time communication with devices and event streaming
- **Full stack teams** building applications with Java backends and TypeScript/JavaScript frontends
- **Polyglot teams** working across multiple languages who need type safe communication
- **Anyone building distributed systems** who wants simplicity without sacrificing power

## Architecture Overview

Continuum is built around a pluggable architecture that handles the complexities of distributed communication:

- **Pluggable Transport Layer**: The framework uses pluggable transport and serialization layers. The current implementation uses STOMP over WebSockets with JSON serialization, but custom transports can be implemented.
- **Service Discovery & Registration**: Services are automatically discovered and registered. No manual service registry configuration needed.
- **Event Streaming**: Built in support for event streams, perfect for IoT scenarios where devices emit continuous data.
- **Multiple Deployment Patterns**: Whether you're building a single service, multiple microservices, or a full stack application, Continuum supports your architecture.

The beauty of this architecture is that you don't need to understand it to use it. The framework handles all the complexity behind the scenes.

## Language Support

**Currently Available:**
- **Java**: Full server and client support with Spring Boot integration
- **TypeScript**: Full client support for browsers and Node.js

**Coming Soon:**
- **Python**: Server and client support
- **Golang**: Server and client support

## Ready to Get Started?

Now that you understand what Continuum is and why you might want to use it, let's get you up and running. The [Quick Start guide](./quick-start) will walk you through setting up Continuum for your specific use case.

Whether you're building a Java backend with a TypeScript frontend, a Node.js backend, or multiple microservices, the Quick Start guide will get you from zero to a working Continuum application in minutes.

[Get Started →](./quick-start)
