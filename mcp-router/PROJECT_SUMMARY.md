# Nacos MCP Router - Project Summary

## Project Overview

The Nacos MCP Router is a Spring Boot application that serves as a centralized registry and router for Model Context Protocol (MCP) servers. It leverages Alibaba Nacos for service discovery and provides RESTful APIs for server registration, management, and search capabilities.

## Architecture

### Core Components

1. **Controller Layer** (`controller/`)
   - `McpRouterController`: REST API endpoints for server management and search

2. **Service Layer** (`service/`)
   - `McpServerService`: Business logic for server registration and management
   - `SearchService`: Search functionality across registered servers
   - `SearchProvider`: Pluggable search providers (Nacos, Compass)

3. **Model Layer** (`model/`)
   - `McpServer`: Server representation model
   - `McpServerRegistrationRequest`: Registration request payload
   - `SearchRequest/SearchResponse`: Search operation models
   - `McpTool`: Tool representation for MCP servers

4. **Configuration** (`config/`)
   - `NacosConfig`: Nacos service discovery configuration
   - `McpRouterProperties`: Application configuration properties
   - `SpringAiConfig`: Spring AI integration configuration

## Key Features

### 1. MCP Server Registration
- Register MCP servers with Nacos service discovery
- Validation of server registration requests
- Support for multiple transport types (stdio, sse, streamable_http)
- Automatic health monitoring and status tracking

### 2. Server Discovery and Management
- List all registered MCP servers
- Server status monitoring
- Graceful server unregistration
- Load balancing support through Nacos

### 3. Search Integration
- Search across registered MCP servers
- Multiple search providers (Nacos-based, Compass integration)
- Configurable search parameters and filtering
- Search result ranking and relevance scoring

### 4. REST API
- Complete CRUD operations for server management
- RESTful endpoints following standard conventions
- JSON request/response format
- Error handling and validation

## API Endpoints

### MCP Server Management
- `POST /api/mcp/register` - Register new MCP server
- `GET /api/mcp/servers` - List all registered servers
- `DELETE /api/mcp/unregister/{serverId}` - Unregister server

### Search
- `POST /api/search` - Search across registered servers

### Health Monitoring
- `GET /actuator/health` - Application health status
- `GET /actuator/info` - Application information

## Technology Stack

### Core Framework
- **Spring Boot 3.2.0** - Main application framework
- **Spring WebFlux** - Reactive web framework
- **Spring Boot Actuator** - Health monitoring and metrics

### Service Discovery
- **Alibaba Nacos 2.3.0** - Service registry and configuration center
- **Nacos Discovery** - Service discovery integration

### Search & AI
- **Spring AI** - AI integration framework
- **Compass Search** - Enterprise search integration

### Development Tools
- **Lombok** - Code generation and boilerplate reduction
- **Jackson** - JSON serialization/deserialization
- **Validation API** - Request validation

### Testing
- **JUnit 5** - Unit testing framework
- **Mockito** - Mocking framework
- **Spring Boot Test** - Integration testing
- **Reactor Test** - Reactive testing utilities

## Configuration

### Application Properties
```yaml
server:
  port: 8000

nacos:
  discovery:
    server-addr: 127.0.0.1:8848
    username: nacos
    password: nacos
    namespace: public

mcp:
  router:
    search:
      enabled: true
      provider: nacos
    compass:
      endpoint: http://localhost:9200
      username: admin
      password: admin
```

## Deployment

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- Nacos Server 2.x running
- (Optional) Compass Search for enhanced search capabilities

### Build and Run
```bash
# Build the application
mvn clean package

# Run using Maven
mvn spring-boot:run

# Or run the JAR directly
java -jar target/nacos-mcp-router-spring-1.0.0.jar

# Or use the provided script
./run.sh
```

### Docker Support
The application can be containerized using standard Spring Boot Docker practices.

## Development Workflow

### Project Structure
```
src/
├── main/
│   ├── java/com/nacos/mcp/router/
│   │   ├── config/          # Configuration classes
│   │   ├── controller/      # REST controllers  
│   │   ├── model/           # Data models
│   │   ├── service/         # Business logic
│   │   └── Application.java # Main class
│   └── resources/
│       └── application.yml  # Configuration
└── test/
    └── java/                # Test classes
```

### Testing Strategy
- **Unit Tests**: Service layer business logic
- **Integration Tests**: Controller endpoints with MockMvc
- **Component Tests**: Individual component testing
- **End-to-End Tests**: Complete workflow testing

### Scripts and Tools
- `run.sh` - Application startup script
- `test-app.sh` - End-to-end testing script
- `demo-register-server.sh` - Demo server registration
- `test-coverage.sh` - Test coverage analysis

## Future Enhancements

### Planned Features
1. **Authentication & Authorization** - Secure API access
2. **Rate Limiting** - API usage throttling
3. **Monitoring & Metrics** - Enhanced observability
4. **Configuration Management** - Dynamic configuration updates
5. **Multi-tenancy** - Support for multiple tenants
6. **Server Versioning** - Version management for MCP servers

### Performance Optimizations
1. **Caching Layer** - Redis integration for improved performance
2. **Connection Pooling** - Optimized database connections
3. **Async Processing** - Background task processing
4. **Load Balancing** - Enhanced server selection algorithms

### Integration Capabilities
1. **Message Queues** - RabbitMQ/Kafka integration
2. **Event Streaming** - Real-time event processing
3. **API Gateway** - Integration with existing API gateways
4. **Service Mesh** - Istio/Linkerd compatibility

## Contributing

### Development Setup
1. Clone the repository
2. Ensure Java 17+ and Maven 3.6+ are installed
3. Start Nacos server locally
4. Run `mvn spring-boot:run` to start the application
5. Use `test-app.sh` to verify functionality

### Code Standards
- Follow Spring Boot best practices
- Use reactive programming patterns where appropriate
- Implement comprehensive error handling
- Write unit and integration tests for new features
- Update documentation for API changes

## License

This project is licensed under the Apache License 2.0. 