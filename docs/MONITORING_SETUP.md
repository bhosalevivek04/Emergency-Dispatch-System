# 📊 Monitoring Setup - Emergency Dispatch System

## Overview

Complete observability stack for microservices monitoring:
- **Prometheus**: Metrics collection
- **Grafana**: Visualization dashboards
- **Spring Boot Actuator**: Metrics exposure

---

## 🎯 Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Microservices                            │
│                                                             │
│  Emergency (8081)  Ambulance (8082)  Dispatch (8083)       │
│  Notification (8084)  Tracking (8085)  Gateway (8080)      │
│                                                             │
│  Each exposes: /actuator/prometheus                         │
└────────────────────┬────────────────────────────────────────┘
                     │
                     │ Scrapes metrics every 15s
                     ↓
┌─────────────────────────────────────────────────────────────┐
│                    PROMETHEUS (9090)                        │
│                                                             │
│  • Collects metrics from all services                       │
│  • Stores time-series data                                  │
│  • Provides query language (PromQL)                         │
│  • Alerting rules                                           │
└────────────────────┬────────────────────────────────────────┘
                     │
                     │ Data source
                     ↓
┌─────────────────────────────────────────────────────────────┐
│                    GRAFANA (3000)                           │
│                                                             │
│  • Beautiful dashboards                                     │
│  • Real-time graphs                                         │
│  • Alerts and notifications                                 │
│  • Custom queries                                           │
└─────────────────────────────────────────────────────────────┘
```

---

## 🚀 Quick Start

### Step 1: Start Prometheus

**Using Docker**:
```bash
docker run -d \
  --name prometheus \
  -p 9090:9090 \
  -v $(pwd)/monitoring/prometheus.yml:/etc/prometheus/prometheus.yml \
  prom/prometheus
```

**Using Binary** (Windows):
```bash
cd monitoring
prometheus --config.file=prometheus.yml
```

Access: http://localhost:9090

### Step 2: Start Grafana

**Using Docker**:
```bash
docker run -d \
  --name grafana \
  -p 3000:3000 \
  grafana/grafana
```

**Using Binary** (Windows):
```bash
# Download from https://grafana.com/grafana/download
grafana-server.exe
```

Access: http://localhost:3000
- Default login: admin/admin

### Step 3: Configure Grafana

1. **Add Prometheus Data Source**:
   - Go to Configuration → Data Sources
   - Click "Add data source"
   - Select "Prometheus"
   - URL: `http://localhost:9090`
   - Click "Save & Test"

2. **Import Dashboard**:
   - Go to Dashboards → Import
   - Upload `monitoring/grafana-dashboard.json`
   - Select Prometheus data source
   - Click "Import"

---

## 📊 Key Metrics

### Emergency Service Metrics

```promql
# Total emergency requests
emergency_requests_total

# Emergency requests per second
rate(emergency_requests_total[1m])

# Emergency requests by priority
emergency_requests_total{priority="HIGH"}
emergency_requests_total{priority="MEDIUM"}
emergency_requests_total{priority="LOW"}
```

### Dispatch Service Metrics

```promql
# Total emergencies queued
dispatch_emergencies_queued_total

# Successful assignments
dispatch_assignments_published_total

# Failed assignments (no ambulance available)
dispatch_no_available_ambulance_total

# Assignment success rate
rate(dispatch_assignments_published_total[5m]) / 
rate(dispatch_emergencies_queued_total[5m])

# Average dispatch latency
rate(dispatch_assignment_publish_latency_sum[5m]) / 
rate(dispatch_assignment_publish_latency_count[5m])

# Current queue depth
dispatch_emergencies_queue_depth

# Available ambulances
dispatch_ambulances_available_count

# Known ambulances
dispatch_ambulances_known_count
```

### Notification Service Metrics

```promql
# Total notifications sent
notification_sent_total

# Notification failures
notification_consume_failures_total

# Notification success rate
rate(notification_sent_total[5m]) / 
rate(notification_assignments_consumed_total[5m])
```

### Ambulance Service Metrics

```promql
# Ambulance location updates
ambulance_location_updates_total

# Ambulances by status
ambulance_status{status="AVAILABLE"}
ambulance_status{status="ASSIGNED"}
ambulance_status{status="ON_ROUTE"}
```

### System Metrics (All Services)

```promql
# JVM Memory Usage
jvm_memory_used_bytes{application="dispatch-service"}

# CPU Usage
process_cpu_usage{application="dispatch-service"}

# HTTP Request Rate
rate(http_server_requests_seconds_count[1m])

# HTTP Request Duration (p95)
histogram_quantile(0.95, 
  rate(http_server_requests_seconds_bucket[5m])
)

# Active Threads
jvm_threads_live_threads
```

---

## 📈 Grafana Dashboard Panels

### Panel 1: Emergency Requests Overview
```
Type: Graph
Query: rate(emergency_requests_total[1m])
Title: Emergency Requests per Second
```

### Panel 2: Dispatch Success Rate
```
Type: Gauge
Query: 
  rate(dispatch_assignments_published_total[5m]) / 
  rate(dispatch_emergencies_queued_total[5m]) * 100
Title: Dispatch Success Rate (%)
Thresholds: 
  - Red: < 80%
  - Yellow: 80-95%
  - Green: > 95%
```

### Panel 3: Available Ambulances
```
Type: Stat
Query: dispatch_ambulances_available_count
Title: Available Ambulances
```

### Panel 4: Queue Depth
```
Type: Graph
Query: dispatch_emergencies_queue_depth
Title: Emergency Queue Depth
```

### Panel 5: Average Dispatch Time
```
Type: Graph
Query: 
  rate(dispatch_assignment_publish_latency_sum[5m]) / 
  rate(dispatch_assignment_publish_latency_count[5m])
Title: Average Dispatch Time (seconds)
```

### Panel 6: Service Health
```
Type: Table
Query: up{job=~".*-service"}
Title: Service Status
```

### Panel 7: Notification Delivery
```
Type: Graph
Query: rate(notification_sent_total[1m])
Title: Notifications Sent per Second
```

### Panel 8: JVM Memory Usage
```
Type: Graph
Query: jvm_memory_used_bytes{application=~".*-service"}
Title: Memory Usage by Service
```

---

## 🚨 Alerting Rules

### Critical Alerts

**No Available Ambulances**:
```yaml
- alert: NoAvailableAmbulances
  expr: dispatch_ambulances_available_count == 0
  for: 1m
  labels:
    severity: critical
  annotations:
    summary: "No ambulances available for dispatch"
    description: "All ambulances are busy or offline"
```

**High Queue Depth**:
```yaml
- alert: HighEmergencyQueue
  expr: dispatch_emergencies_queue_depth > 10
  for: 2m
  labels:
    severity: warning
  annotations:
    summary: "Emergency queue is backing up"
    description: "Queue depth: {{ $value }}"
```

**Low Dispatch Success Rate**:
```yaml
- alert: LowDispatchSuccessRate
  expr: |
    rate(dispatch_assignments_published_total[5m]) / 
    rate(dispatch_emergencies_queued_total[5m]) < 0.8
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "Dispatch success rate below 80%"
```

**Service Down**:
```yaml
- alert: ServiceDown
  expr: up{job=~".*-service"} == 0
  for: 1m
  labels:
    severity: critical
  annotations:
    summary: "Service {{ $labels.job }} is down"
```

**High Response Time**:
```yaml
- alert: HighResponseTime
  expr: |
    histogram_quantile(0.95, 
      rate(http_server_requests_seconds_bucket[5m])
    ) > 5
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "High response time on {{ $labels.application }}"
    description: "P95 latency: {{ $value }}s"
```

---

## 🔍 Useful Queries

### Top 5 Slowest Endpoints
```promql
topk(5, 
  histogram_quantile(0.95, 
    rate(http_server_requests_seconds_bucket[5m])
  )
)
```

### Error Rate by Service
```promql
rate(http_server_requests_seconds_count{status=~"5.."}[5m])
```

### Kafka Consumer Lag
```promql
kafka_consumer_lag{group="dispatch-group"}
```

### Redis Connection Pool
```promql
redis_connection_pool_active_connections
redis_connection_pool_idle_connections
```

### Dispatch Performance Over Time
```promql
# Average ETA of assigned ambulances
avg(dispatch_assignment_eta_seconds)

# Distance distribution
histogram_quantile(0.5, dispatch_assignment_distance_km_bucket)
histogram_quantile(0.95, dispatch_assignment_distance_km_bucket)
```

---

## 📱 Mobile Dashboard

Create a mobile-friendly dashboard for operations team:

**Panels**:
1. Current Available Ambulances (Big Number)
2. Active Emergencies (Big Number)
3. Queue Depth (Gauge)
4. Last 10 Assignments (Table)
5. Map View (if Grafana supports)

---

## 🔔 Notification Channels

Configure Grafana to send alerts:

### Slack Integration
```yaml
notifiers:
  - name: slack
    type: slack
    settings:
      url: https://hooks.slack.com/services/YOUR/WEBHOOK/URL
      channel: #emergency-alerts
```

### Email Integration
```yaml
notifiers:
  - name: email
    type: email
    settings:
      addresses: ops-team@example.com
```

### PagerDuty Integration
```yaml
notifiers:
  - name: pagerduty
    type: pagerduty
    settings:
      integrationKey: YOUR_INTEGRATION_KEY
```

---

## 📊 Sample Dashboard Layout

```
┌─────────────────────────────────────────────────────────────┐
│  Emergency Dispatch System - Operations Dashboard           │
├─────────────┬─────────────┬─────────────┬─────────────────┤
│  Available  │   Active    │    Queue    │   Success Rate  │
│  Ambulances │ Emergencies │    Depth    │      95%        │
│      8      │      3      │      2      │   [Gauge]       │
├─────────────┴─────────────┴─────────────┴─────────────────┤
│  Emergency Requests per Second                              │
│  [Line Graph showing last 1 hour]                           │
├─────────────────────────────────────────────────────────────┤
│  Dispatch Latency (P50, P95, P99)                           │
│  [Line Graph showing percentiles]                           │
├─────────────────────────────────────────────────────────────┤
│  Service Health Status                                      │
│  [Table: Service | Status | Uptime | Last Seen]            │
├─────────────────────────────────────────────────────────────┤
│  Recent Assignments                                         │
│  [Table: Time | Emergency | Ambulance | ETA | Distance]    │
└─────────────────────────────────────────────────────────────┘
```

---

## 🎯 Best Practices

1. **Set up alerts** for critical metrics
2. **Monitor trends** over time, not just current values
3. **Create dashboards** for different audiences (ops, dev, management)
4. **Use annotations** to mark deployments and incidents
5. **Set retention policies** for metrics (default: 15 days)
6. **Export dashboards** as JSON for version control
7. **Test alerts** regularly to ensure they work

---

## 🔧 Troubleshooting

### Prometheus not scraping metrics
- Check service is running: `curl http://localhost:8081/actuator/health`
- Check Prometheus targets: http://localhost:9090/targets
- Verify prometheus.yml configuration

### Grafana not showing data
- Check Prometheus data source connection
- Verify query syntax in panel
- Check time range selection

### High memory usage
- Reduce metrics retention period
- Increase Prometheus memory limit
- Use recording rules for expensive queries

---

## 🚀 Production Recommendations

1. **Use Prometheus Operator** for Kubernetes
2. **Set up Thanos** for long-term storage
3. **Enable authentication** on Grafana
4. **Use HTTPS** for all connections
5. **Set up backup** for Grafana dashboards
6. **Monitor the monitors** (meta-monitoring)
7. **Document runbooks** for each alert

---

## 📈 This is Production-Grade Monitoring!

Your system now has:
- ✅ Real-time metrics collection
- ✅ Beautiful visualizations
- ✅ Automated alerting
- ✅ Performance tracking
- ✅ Service health monitoring
- ✅ Business metrics (dispatch success rate, ETA, etc.)

Perfect for operations and SRE teams! 🔥
