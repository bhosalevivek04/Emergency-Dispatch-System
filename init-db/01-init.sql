-- Initialize PostgreSQL with PostGIS extension
-- This script runs automatically when the container starts for the first time

-- Enable PostGIS extension
CREATE EXTENSION IF NOT EXISTS postgis;

-- Verify PostGIS is installed
SELECT PostGIS_Version();

-- Create initial tables (basic schema for Phase 1)

-- Ambulances table
CREATE TABLE IF NOT EXISTS ambulances (
    id BIGSERIAL PRIMARY KEY,
    ambulance_id VARCHAR(50) UNIQUE NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_location GEOGRAPHY(POINT, 4326),
    current_latitude DECIMAL(10, 8),
    current_longitude DECIMAL(11, 8),
    vehicle_number VARCHAR(50),
    equipment_type VARCHAR(50),
    crew_size INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_location_update TIMESTAMP,
    
    CONSTRAINT chk_ambulance_status CHECK (status IN ('AVAILABLE', 'ASSIGNED', 'ON_ROUTE', 'ARRIVED', 'COMPLETED'))
);

-- Emergencies table
CREATE TABLE IF NOT EXISTS emergencies (
    id BIGSERIAL PRIMARY KEY,
    emergency_id VARCHAR(50) UNIQUE NOT NULL,
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    latitude DECIMAL(10, 8) NOT NULL,
    longitude DECIMAL(11, 8) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    assigned_ambulance_id VARCHAR(50),
    assignment_timestamp TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    caller_phone VARCHAR(20),
    description TEXT,
    
    CONSTRAINT chk_emergency_priority CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_emergency_status CHECK (status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);

-- Assignment history table
CREATE TABLE IF NOT EXISTS assignment_history (
    id BIGSERIAL PRIMARY KEY,
    emergency_id VARCHAR(50) NOT NULL,
    ambulance_id VARCHAR(50) NOT NULL,
    assignment_type VARCHAR(20) NOT NULL,
    assignment_status VARCHAR(20) NOT NULL,
    emergency_location GEOGRAPHY(POINT, 4326),
    ambulance_location GEOGRAPHY(POINT, 4326),
    distance_km DECIMAL(10, 2),
    estimated_eta_seconds INTEGER,
    assigned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    acknowledged_at TIMESTAMP,
    completed_at TIMESTAMP,
    assignment_version INTEGER,
    notes TEXT,
    
    CONSTRAINT chk_assignment_type CHECK (assignment_type IN ('INITIAL', 'REASSIGNMENT')),
    CONSTRAINT chk_assignment_status CHECK (assignment_status IN ('ACCEPTED', 'REJECTED', 'TIMEOUT', 'COMPLETED'))
);

-- State transitions table
CREATE TABLE IF NOT EXISTS state_transitions (
    id BIGSERIAL PRIMARY KEY,
    ambulance_id VARCHAR(50) NOT NULL,
    emergency_id VARCHAR(50),
    from_state VARCHAR(20) NOT NULL,
    to_state VARCHAR(20) NOT NULL,
    location GEOGRAPHY(POINT, 4326),
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    transitioned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    duration_seconds INTEGER,
    trigger_type VARCHAR(50),
    notes TEXT
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_ambulances_status ON ambulances(status);
CREATE INDEX IF NOT EXISTS idx_ambulances_location ON ambulances USING GIST(current_location);

CREATE INDEX IF NOT EXISTS idx_emergencies_status ON emergencies(status);
CREATE INDEX IF NOT EXISTS idx_emergencies_location ON emergencies USING GIST(location);
CREATE INDEX IF NOT EXISTS idx_emergencies_created_at ON emergencies(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_emergencies_priority ON emergencies(priority);

CREATE INDEX IF NOT EXISTS idx_assignment_emergency ON assignment_history(emergency_id);
CREATE INDEX IF NOT EXISTS idx_assignment_ambulance ON assignment_history(ambulance_id);
CREATE INDEX IF NOT EXISTS idx_assignment_timestamp ON assignment_history(assigned_at DESC);

CREATE INDEX IF NOT EXISTS idx_transitions_ambulance ON state_transitions(ambulance_id);
CREATE INDEX IF NOT EXISTS idx_transitions_emergency ON state_transitions(emergency_id);
CREATE INDEX IF NOT EXISTS idx_transitions_timestamp ON state_transitions(transitioned_at DESC);

-- Grant permissions
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO dispatch_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO dispatch_user;

-- Log success
DO $$
BEGIN
    RAISE NOTICE 'Database initialized successfully with PostGIS extension';
    RAISE NOTICE 'Tables created: ambulances, emergencies, assignment_history, state_transitions';
    RAISE NOTICE 'Indexes created for optimal query performance';
END $$;
