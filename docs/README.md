# Documentation

Complete documentation for the Emergency Ambulance Dispatch System.

## Table of Contents

### Getting Started
- [Main README](../README.md) - Quick start guide and overview
- [Project Structure](PROJECT_STRUCTURE.md) - Directory organization and file layout

### Architecture & Design
- [System Architecture](ARCHITECTURE.md) - High-level system design and components
- [Microservices Architecture](MICROSERVICES_ARCHITECTURE.md) - Detailed microservices breakdown
- [API Documentation](API_DOCUMENTATION.md) - REST API endpoints and WebSocket protocols

### Operations
- [Monitoring Setup](MONITORING_SETUP.md) - Metrics, logging, and health checks

### Development
- [Contributing Guidelines](CONTRIBUTING.md) - How to contribute to the project

## Quick Links

### Architecture Overview
The system consists of 6 microservices:
1. **API Gateway** (8080) - Entry point
2. **Emergency Service** (8081) - Emergency management
3. **Ambulance Service** (8082) - Location tracking & movement
4. **Dispatch Service** (8083) - Intelligent assignment
5. **Notification Service** (8084) - Alerts
6. **Tracking Service** (8085) - Real-time WebSocket updates

### Key Features
- **Dynamic State Transitions**: Realistic timing based on actual route distance
- **Intelligent Routing**: OSRM integration with fallback calculation
- **Real-time Tracking**: WebSocket-based live updates
- **Event-Driven**: Kafka for inter-service communication
- **Scalable**: Independent microservices with Redis state management

### Technology Stack
- **Backend**: Spring Boot, Kafka, Redis
- **Frontend**: React, Leaflet, WebSocket
- **Routing**: OSRM (OpenStreetMap Routing Machine)
- **Infrastructure**: Docker, Docker Compose

## Documentation Structure

```
docs/
├── README.md                          # This file
├── ARCHITECTURE.md                    # System architecture
├── MICROSERVICES_ARCHITECTURE.md      # Microservices details
├── API_DOCUMENTATION.md               # API reference
├── PROJECT_STRUCTURE.md               # Project organization
├── MONITORING_SETUP.md                # Monitoring guide
└── CONTRIBUTING.md                    # Contribution guidelines
```

## Need Help?

- **Setup Issues**: See [Main README](../README.md) troubleshooting section
- **API Questions**: Check [API Documentation](API_DOCUMENTATION.md)
- **Architecture Questions**: See [System Architecture](ARCHITECTURE.md)
- **Contributing**: Read [Contributing Guidelines](CONTRIBUTING.md)

## Additional Resources

### External Documentation
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Redis Documentation](https://redis.io/documentation)
- [OSRM Documentation](http://project-osrm.org/)
- [Leaflet Documentation](https://leafletjs.com/)

### Related Projects
- [OpenStreetMap](https://www.openstreetmap.org/) - Map data source
- [Geofabrik](https://download.geofabrik.de/) - OSM data downloads

## Version History

See the main [README](../README.md) for version information and changelog.
