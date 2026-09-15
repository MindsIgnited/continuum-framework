# C3 IDL Documentation

## Overview

The C3 Interface Definition Language (IDL) is a schema language within the Continuum platform from Mind Ignited. It's used for defining and managing data types to ensure interoperability and smooth integration across different system components by offering a structured method for defining complex data types and their properties.

## Core Components

### C3Type Base Class
- **Description**: The foundational class for all type schemas. Used to create various data types in the IDL.

### ComplexC3Type
- **Description**: An abstract class extending `C3Type`, representing complex data types with namespaces, names, decorators, and metadata.

### UnionC3Type
- **Description**: Defines a union type, allowing a data type to be one of several specified types. Useful for types that may vary across contexts.

### EnumC3Type
- **Description**: Defines an enumeration with a list of predefined constants, ideal for types with a fixed set of values.

### ObjectC3Type
- **Description**: Represents a complex object type with inheritance. It specifies properties and their types using other `C3Type` definitions.

### Primitive Types

#### IntC3Type
- **Description**: Represents integer values.

#### FloatC3Type
- **Description**: Represents float number values.

#### BooleanC3Type
- **Description**: Represents boolean values.

#### CharC3Type
- **Description**: Represents character values.

#### Other Number Types
- **ByteC3Type**: For byte values.
- **ShortC3Type**: For short integers.
- **DoubleC3Type**: For double-precision numbers.
- **LongC3Type**: For long integers.

### ReferenceC3Type
- **Description**: Facilitates referencing other components within the schema, promoting modular and reusable designs.

## Decorators

- **Purpose**: Types implementing `HasDecorators` can have C3Decorator instances, enhancing functionality with metadata descriptions and rendering hints.

## Namespaces and Service Definitions

### NamespaceDefinition
- **Description**: Organizes `ComplexC3Type` and `ServiceDefinition` instances within a named scope, aiding logical system separation.

## Conclusion

The C3Type IDL is a robust framework for defining data types in a consistent and interoperable fashion. Through its structures and features, developers can form detailed type definitions that foster system integration and efficient data management within the Continuum platform.

