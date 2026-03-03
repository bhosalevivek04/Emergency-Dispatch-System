# PostgreSQL + PostGIS Setup Guide

## Quick Start (3 Steps)

### Step 1: Start PostgreSQL
```bash
start-postgres.bat
```

This will:
- Pull the `postgis/postgis:15-3.3` image
- Start PostgreSQL container
- Create `emergency_dispatch` database
- Enable PostGIS extension
- Create initial tables
- Set up indexes

### Step 2: Test Connection
```bash
test-postgres.bat
```

This verifies:
- Container is running
- Database is accessible
- PostGIS is enabled
- Tables are created
- Indexes are set up

### Step 3: Connect to Database
```bash
postgres-shell.bat
```

Or use any PostgreSQL client:
- **Host**: localhost
- **Port**: 5432
- **Database**: emergency_dispatch
- **Username**: dispatch_user
- **Password**: dispatch_password

---

## What's Installed

### PostgreSQL 15 with PostGIS 3.3
- Full PostgreSQL database
- PostGIS spatial extension
- Geography data type support
- Spatial indexing (GIST)

### Tables Created
1. **ambulances** - Ambulance fleet data
2. **emergencies** - Emergency requests
3. **assignment_history** - Assignment audit trail
4. **state_transitions** - State change logs

### Indexes Created
- Spatial indexes on location columns (GIST)
- Status indexes for filtering
- Timestamp indexes for sorting
- Foreign key indexes (ready for Phase 2)

---

## Useful Commands

### Check Container Status
```bash
docker ps | findstr postgres
```

### View Logs
```bash
docker logs postgres
```

### Stop PostgreSQL
```bash
docker-compose stop postgres
```

### Start PostgreSQL
```bash
docker-compose start postgres
```

### Remove PostgreSQL (keeps data)
```bash
docker-compose stop postgres
docker-compose rm postgres
```

### Remove PostgreSQL + Data
```bash
docker-compose down -v
```

---

## Testing PostGIS

### Connect to shell
```bash
postgres-shell.bat
```

### Test PostGIS queries
```sql
-- Check PostGIS version
SELECT PostGIS_Version();

-- Create a test point
SELECT ST_AsText(ST_MakePoint(73.8567, 18.5204));

-- Calculate distance between two points (in meters)
SELECT ST_Distance(
    ST_MakePoint(73.8567, 18.5204)::geography,
    ST_MakePoint(73.8600, 18.5300)::geography
) as distance_meters;

-- Find points within 5km radius
SELECT ST_DWithin(
    ST_MakePoint(73.8567, 18.5204)::geography,
    ST_MakePoint(73.8600, 18.5300)::geography,
    5000  -- 5km in meters
) as within_5km;
```

---

## Verify Tables

```sql
-- List all tables
\dt

-- Describe ambulances table
\d ambulances

-- Check table structure
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'ambulances';

-- Verify spatial columns
SELECT f_table_name, f_geometry_column, type, srid
FROM geometry_columns;
```

---

## Insert Test Data

```sql
-- Insert test ambulance
INSERT INTO ambulances (
    ambulance_id, 
    status, 
    current_latitude, 
    current_longitude,
    current_location,
    vehicle_number
) VALUES (
    'AMB-101',
    'AVAILABLE',
    18.5204,
    73.8567,
    ST_SetSRID(ST_MakePoint(73.8567, 18.5204), 4326)::geography,
    'MH-12-AB-1234'
);

-- Insert test emergency
INSERT INTO emergencies (
    emergency_id,
    latitude,
    longitude,
    location,
    priority,
    status
) VALUES (
    'EMG-001',
    18.5074,
    73.8077,
    ST_SetSRID(ST_MakePoint(73.8077, 18.5074), 4326)::geography,
    'HIGH',
    'PENDING'
);

-- Query test data
SELECT * FROM ambulances;
SELECT * FROM emergencies;

-- Test spatial query
SELECT 
    ambulance_id,
    ST_Distance(
        current_location,
        (SELECT location FROM emergencies WHERE emergency_id = 'EMG-001')
    ) as distance_meters
FROM ambulances
WHERE status = 'AVAILABLE'
ORDER BY distance_meters
LIMIT 1;
```

---

## Troubleshooting

### Container won't start
```bash
# Check if port 5432 is already in use
netstat -ano | findstr :5432

# Stop any existing PostgreSQL
docker stop postgres
docker rm postgres

# Try again
start-postgres.bat
```

### Can't connect to database
```bash
# Check container logs
docker logs postgres

# Verify container is healthy
docker inspect postgres | findstr Health

# Wait a bit longer (PostgreSQL takes 10-15 seconds to start)
timeout /t 15
test-postgres.bat
```

### PostGIS not working
```bash
# Connect to shell
postgres-shell.bat

# Manually enable PostGIS
CREATE EXTENSION IF NOT EXISTS postgis;

# Verify
SELECT PostGIS_Version();
```

### Tables not created
```bash
# Check init script ran
docker logs postgres | findstr "Database initialized"

# Manually run init script
docker exec -i postgres psql -U dispatch_user -d emergency_dispatch < init-db/01-init.sql
```

---

## Docker Compose Integration

PostgreSQL is now part of your main `docker-compose.yml`:

```yaml
postgres:
  image: postgis/postgis:15-3.3
  container_name: postgres
  environment:
    POSTGRES_DB: emergency_dispatch
    POSTGRES_USER: dispatch_user
    POSTGRES_PASSWORD: dispatch_password
  ports:
    - "5432:5432"
  volumes:
    - postgres_data:/var/lib/postgresql/data
    - ./init-db:/docker-entrypoint-initdb.d
  networks:
    - dispatch-network
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U dispatch_user -d emergency_dispatch"]
    interval: 10s
    timeout: 5s
    retries: 5
```

### Start everything
```bash
docker-compose up -d
```

### Start only infrastructure
```bash
docker-compose up -d kafka redis postgres
```

---

## Next Steps

### Phase 1: Basic Integration (This Week)
1. ✅ PostgreSQL installed
2. ✅ Tables created
3. ✅ PostGIS enabled
4. ⏳ Add JPA dependencies to services
5. ⏳ Create entity classes
6. ⏳ Create repositories
7. ⏳ Update services to persist data

### Phase 2: Production Patterns (Next Week)
1. ⏳ Add foreign key constraints
2. ⏳ Add @Version for optimistic locking
3. ⏳ Implement outbox pattern
4. ⏳ Add comprehensive tests

---

## Connection Strings

### JDBC URL
```
jdbc:postgresql://localhost:5432/emergency_dispatch
```

### Spring Boot application.yml
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/emergency_dispatch
    username: dispatch_user
    password: dispatch_password
    driver-class-name: org.postgresql.Driver
```

### Docker services (use container name)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/emergency_dispatch
```

---

## Backup & Restore

### Backup database
```bash
docker exec postgres pg_dump -U dispatch_user emergency_dispatch > backup.sql
```

### Restore database
```bash
docker exec -i postgres psql -U dispatch_user -d emergency_dispatch < backup.sql
```

### Backup with Docker volume
```bash
docker run --rm -v emergency-dispatch-service_postgres_data:/data -v %cd%:/backup ubuntu tar czf /backup/postgres_backup.tar.gz /data
```

---

## Monitoring

### Check database size
```sql
SELECT pg_size_pretty(pg_database_size('emergency_dispatch'));
```

### Check table sizes
```sql
SELECT 
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```

### Check active connections
```sql
SELECT count(*) FROM pg_stat_activity;
```

### Check slow queries
```sql
SELECT pid, now() - pg_stat_activity.query_start AS duration, query
FROM pg_stat_activity
WHERE state = 'active'
ORDER BY duration DESC;
```

---

## Success! ✅

You now have:
- ✅ PostgreSQL 15 running
- ✅ PostGIS 3.3 enabled
- ✅ Database created
- ✅ Tables initialized
- ✅ Indexes set up
- ✅ Ready for Phase 1 implementation

**Next**: Add JPA dependencies to your services and start coding!
