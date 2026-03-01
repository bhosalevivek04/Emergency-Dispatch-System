# Deployment Guide

## Table of Contents
- [Local Development](#local-development)
- [Docker Deployment](#docker-deployment)
- [Production Deployment](#production-deployment)
- [Monitoring Setup](#monitoring-setup)
- [Troubleshooting](#troubleshooting)

## Local Development

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker & Docker Compose
- Git

### Setup Steps

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd emergency-dispatch-system
   ```

2. **Build all services**
   ```bash
   # Build each service
   cd emergency-service && mvn clean package && cd ..
   cd dispatch-service && mvn clean package && cd ..
   cd ambulance-service && mvn clean package && cd ..
   cd notification-service && mvn clean package && cd ..
   cd api-gateway && mvn clean package && cd ..
   ```

3. **Start infrastructure services**
   ```bash
   docker-compose up -d kafka redis prometheus grafana
   ```

4. **Run services locally (for development)**
   ```bash
   # Terminal 1
   cd emergency-service && mvn spring-boot:run
   
   # Terminal 2
   cd dispatch-service && mvn spring-boot:run
   
   # Terminal 3
   cd ambulance-service && mvn spring-boot:run
   
   # Terminal 4
   cd notification-service && mvn spring-boot:run
   
   # Terminal 5
   cd api-gateway && mvn spring-boot:run
   ```

## Docker Deployment

### Quick Start

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Check service status
docker-compose ps

# Stop all services
docker-compose down

# Stop and remove volumes
docker-compose down -v
```

### Individual Service Management

```bash
# Restart a specific service
docker-compose restart emergency-service

# View logs for specific service
docker-compose logs -f dispatch-service

# Rebuild and restart
docker-compose up -d --build ambulance-service
```

### Environment Variables

Create `.env` file in root directory:

```env
# Kafka Configuration
KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# Redis Configuration
REDIS_HOST=redis
REDIS_PORT=6379

# Service Ports
EMERGENCY_SERVICE_PORT=8081
DISPATCH_SERVICE_PORT=8083
AMBULANCE_SERVICE_PORT=8082
NOTIFICATION_SERVICE_PORT=8084
API_GATEWAY_PORT=8080

# Monitoring
PROMETHEUS_PORT=9090
GRAFANA_PORT=3000
```

## Production Deployment

### Docker Swarm

1. **Initialize Swarm**
   ```bash
   docker swarm init
   ```

2. **Deploy Stack**
   ```bash
   docker stack deploy -c docker-compose.yml emergency-dispatch
   ```

3. **Scale Services**
   ```bash
   docker service scale emergency-dispatch_emergency-service=3
   docker service scale emergency-dispatch_ambulance-service=2
   ```

### Kubernetes

#### Prerequisites
- Kubernetes cluster (EKS, GKE, AKS, or local minikube)
- kubectl configured
- Helm 3+ (optional)

#### Deployment Steps

1. **Create namespace**
   ```bash
   kubectl create namespace emergency-dispatch
   ```

2. **Deploy Kafka (using Strimzi operator)**
   ```bash
   kubectl create -f 'https://strimzi.io/install/latest?namespace=emergency-dispatch' -n emergency-dispatch
   kubectl apply -f k8s/kafka-cluster.yaml -n emergency-dispatch
   ```

3. **Deploy Redis**
   ```bash
   helm repo add bitnami https://charts.bitnami.com/bitnami
   helm install redis bitnami/redis -n emergency-dispatch
   ```

4. **Deploy Services**
   ```bash
   kubectl apply -f k8s/emergency-service.yaml -n emergency-dispatch
   kubectl apply -f k8s/dispatch-service.yaml -n emergency-dispatch
   kubectl apply -f k8s/ambulance-service.yaml -n emergency-dispatch
   kubectl apply -f k8s/notification-service.yaml -n emergency-dispatch
   kubectl apply -f k8s/api-gateway.yaml -n emergency-dispatch
   ```

5. **Deploy Monitoring**
   ```bash
   helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
   helm install prometheus prometheus-community/kube-prometheus-stack -n emergency-dispatch
   ```

### Cloud Deployment (AWS Example)

#### Architecture
```
Internet → ALB → ECS Services → RDS/ElastiCache
                              → MSK (Kafka)
```

#### Services
- **ECS Fargate**: Run containerized services
- **MSK**: Managed Kafka
- **ElastiCache**: Managed Redis
- **ALB**: Application Load Balancer
- **CloudWatch**: Logging and monitoring
- **ECR**: Container registry

#### Deployment Steps

1. **Build and push images**
   ```bash
   # Login to ECR
   aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com
   
   # Tag and push
   docker tag emergency-service:latest <account-id>.dkr.ecr.us-east-1.amazonaws.com/emergency-service:latest
   docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/emergency-service:latest
   ```

2. **Create ECS Task Definitions**
   ```bash
   aws ecs register-task-definition --cli-input-json file://ecs/emergency-service-task.json
   ```

3. **Create ECS Services**
   ```bash
   aws ecs create-service --cluster emergency-dispatch --service-name emergency-service --task-definition emergency-service --desired-count 2
   ```

## Monitoring Setup

### Prometheus Configuration

Prometheus is pre-configured to scrape all services. Access at `http://localhost:9090`

**Key Queries**:
```promql
# Emergency request rate
rate(emergency_requests_total[5m])

# Assignment success rate
rate(dispatch_assignments_published_total[5m])

# Queue depth
dispatch_emergencies_queue_depth

# Available ambulances
dispatch_ambulances_available_count
```

### Grafana Setup

1. **Access Grafana**: http://localhost:3000
2. **Login**: admin/admin (change on first login)
3. **Add Prometheus Data Source**:
   - URL: http://prometheus:9090
   - Access: Server (default)

4. **Import Dashboard**:
   - Create dashboard with panels for:
     - Emergency request rate
     - Assignment latency
     - Queue depth
     - Ambulance availability
     - Error rates

### Alerting

Configure alerts in Prometheus (`monitoring/alerts.yml`):

```yaml
groups:
  - name: emergency_dispatch
    interval: 30s
    rules:
      - alert: HighQueueDepth
        expr: dispatch_emergencies_queue_depth > 100
        for: 5m
        annotations:
          summary: "Emergency queue depth is high"
          
      - alert: NoAvailableAmbulances
        expr: dispatch_ambulances_available_count == 0
        for: 2m
        annotations:
          summary: "No ambulances available"
```

## Health Checks

All services expose health endpoints:

```bash
# Emergency Service
curl http://localhost:8081/actuator/health

# Dispatch Service
curl http://localhost:8083/actuator/health

# Ambulance Service
curl http://localhost:8082/actuator/health

# Notification Service
curl http://localhost:8084/actuator/health
```

## Troubleshooting

### Services Not Starting

**Check logs**:
```bash
docker-compose logs <service-name>
```

**Common issues**:
- Kafka not ready: Wait 30 seconds after starting
- Port conflicts: Check if ports are already in use
- Memory issues: Increase Docker memory allocation

### Kafka Issues

**Check Kafka topics**:
```bash
docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 --list
```

**Create topic manually**:
```bash
docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 --create --topic emergency-topic --partitions 1 --replication-factor 1
```

### Redis Issues

**Connect to Redis**:
```bash
docker exec -it redis redis-cli
```

**Check keys**:
```redis
KEYS *
GET ambulance:A1:status
```

### Performance Issues

**Check resource usage**:
```bash
docker stats
```

**Increase resources**:
- Edit `docker-compose.yml`
- Add resource limits:
```yaml
deploy:
  resources:
    limits:
      cpus: '2'
      memory: 2G
```

### Network Issues

**Check network**:
```bash
docker network ls
docker network inspect emergency-dispatch-system_default
```

**Test connectivity**:
```bash
docker exec emergency-service ping kafka
docker exec dispatch-service ping redis
```

## Backup and Recovery

### Redis Backup

```bash
# Manual backup
docker exec redis redis-cli BGSAVE

# Copy dump file
docker cp redis:/data/dump.rdb ./backup/

# Restore
docker cp ./backup/dump.rdb redis:/data/
docker-compose restart redis
```

### Kafka Backup

For production, use:
- Kafka MirrorMaker for replication
- Confluent Replicator
- Regular topic snapshots

## Scaling Guidelines

### Horizontal Scaling

**Stateless services** (can scale freely):
```bash
docker-compose up -d --scale emergency-service=3
docker-compose up -d --scale notification-service=2
```

**Stateful services** (requires coordination):
- Dispatch Service: Use leader election or partition-based processing
- Ambulance Service: Partition by ambulance ID

### Vertical Scaling

Increase resources in `docker-compose.yml`:
```yaml
services:
  dispatch-service:
    deploy:
      resources:
        limits:
          cpus: '4'
          memory: 4G
```

## Security Hardening

### Production Checklist

- [ ] Enable authentication on all services
- [ ] Use TLS for all communication
- [ ] Enable Kafka ACLs
- [ ] Set Redis password
- [ ] Use secrets management (Vault, AWS Secrets Manager)
- [ ] Enable network policies
- [ ] Regular security updates
- [ ] Implement rate limiting
- [ ] Enable audit logging
- [ ] Use non-root containers

### Example: Enable Redis Authentication

```yaml
redis:
  image: redis:7
  command: redis-server --requirepass your-strong-password
  environment:
    REDIS_PASSWORD: your-strong-password
```

Update services:
```yaml
environment:
  SPRING_DATA_REDIS_PASSWORD: your-strong-password
```

## Maintenance

### Regular Tasks

**Daily**:
- Check service health
- Monitor error rates
- Review logs for anomalies

**Weekly**:
- Review metrics and trends
- Check disk usage
- Update dependencies

**Monthly**:
- Security patches
- Performance optimization
- Capacity planning

### Upgrade Procedure

1. Backup data (Redis, Kafka)
2. Test in staging environment
3. Deploy during maintenance window
4. Monitor closely after deployment
5. Have rollback plan ready

```bash
# Upgrade with zero downtime
docker-compose pull
docker-compose up -d --no-deps --build emergency-service
# Wait and verify
docker-compose up -d --no-deps --build dispatch-service
# Continue for other services
```
