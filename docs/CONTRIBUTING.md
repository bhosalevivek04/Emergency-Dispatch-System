# Contributing to Emergency Dispatch System

Thank you for your interest in contributing to the Emergency Dispatch System! This document provides guidelines for contributing to this project.

## 🤝 How to Contribute

### Reporting Issues

If you find a bug or have a suggestion:

1. Check if the issue already exists in the [Issues](../../issues) section
2. If not, create a new issue with:
   - Clear title and description
   - Steps to reproduce (for bugs)
   - Expected vs actual behavior
   - System information (OS, Java version, Docker version)
   - Relevant logs or screenshots

### Submitting Changes

1. **Fork the repository**
   ```bash
   git clone https://github.com/YOUR_USERNAME/emergency-dispatch-system.git
   cd emergency-dispatch-system
   ```

2. **Create a feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Make your changes**
   - Follow the existing code style
   - Add tests if applicable
   - Update documentation as needed

4. **Test your changes**
   ```bash
   # Build all services
   mvn clean package
   
   # Run with Docker Compose
   docker-compose up -d
   
   # Run load tests
   ./simulate-emergencies.ps1 -Count 50
   ```

5. **Commit your changes**
   ```bash
   git add .
   git commit -m "feat: add your feature description"
   ```

   Follow [Conventional Commits](https://www.conventionalcommits.org/):
   - `feat:` - New feature
   - `fix:` - Bug fix
   - `docs:` - Documentation changes
   - `refactor:` - Code refactoring
   - `test:` - Adding tests
   - `chore:` - Maintenance tasks

6. **Push to your fork**
   ```bash
   git push origin feature/your-feature-name
   ```

7. **Create a Pull Request**
   - Provide a clear description of changes
   - Reference any related issues
   - Ensure all checks pass

## 📝 Code Style Guidelines

### Java
- Follow standard Java naming conventions
- Use Lombok annotations to reduce boilerplate
- Add JavaDoc for public methods
- Keep methods focused and small
- Use meaningful variable names

### Spring Boot
- Use constructor injection (via Lombok's `@RequiredArgsConstructor`)
- Externalize configuration to `application.yml`
- Use appropriate Spring annotations
- Follow REST API best practices

### Kafka
- Use descriptive topic names
- Include proper error handling
- Implement Dead Letter Topics for failures

### Redis
- Use clear key naming conventions
- Implement proper TTL for temporary data
- Use Lua scripts for atomic operations

## 🧪 Testing

- Write unit tests for business logic
- Add integration tests for Kafka consumers/producers
- Test Redis state management scenarios
- Verify metrics are properly exposed

## 📚 Documentation

- Update README.md for new features
- Add inline comments for complex logic
- Update API documentation
- Include examples where helpful

## 🎯 Areas for Contribution

We welcome contributions in these areas:

- **Features**: Authentication, WebSocket support, database persistence
- **Testing**: Integration tests, load testing improvements
- **Documentation**: Tutorials, architecture deep-dives
- **Performance**: Optimization, caching strategies
- **Monitoring**: Additional metrics, alerting rules
- **DevOps**: Kubernetes manifests, CI/CD pipelines

## 💬 Questions?

Feel free to open an issue for questions or discussions about potential contributions.

## 📜 License

By contributing, you agree that your contributions will be licensed under the MIT License.
