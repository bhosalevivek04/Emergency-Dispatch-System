# OSRM Map Data

This directory contains OpenStreetMap data for OSRM routing.

## Download Pune Map Data

1. Go to [Geofabrik Downloads - India](https://download.geofabrik.de/asia/india.html)
2. Download **Maharashtra** region: `maharashtra-latest.osm.pbf`
3. Rename it to `pune.osm.pbf` and place it in this directory

Or use direct link:
```bash
curl -o osrm-data/pune.osm.pbf https://download.geofabrik.de/asia/india/maharashtra-latest.osm.pbf
```

## File Size
- Maharashtra map: ~50-150 MB
- Full India map: ~1-2 GB (not recommended unless needed)

## OSRM Processing

When you start OSRM for the first time:
```bash
docker-compose -f docker-compose-osrm.yml up -d
```

OSRM will automatically:
1. Extract road network from `pune.osm.pbf` (2-5 minutes)
2. Create processed files (`*.osrm`, `*.osrm.*`)
3. Start the routing server on port 5000

Subsequent starts will use the cached processed files (10-20 seconds).

## Processed Files

After first run, you'll see:
- `pune.osrm` - Main routing data
- `pune.osrm.*` - Various index and graph files

These files are **not committed to git** (see `.gitignore`).

## Testing OSRM

After OSRM starts, test it:
```bash
curl "http://localhost:5000/route/v1/driving/73.8567,18.5204;73.8077,18.5074?overview=false"
```

You should see:
- `"distance"`: > 0 (in meters)
- `"duration"`: > 0 (in seconds)
- Pune street names in waypoints

## Troubleshooting

### Distance/Duration = 0
- OSRM has wrong map data
- Download fresh Maharashtra map
- Delete all `*.osrm*` files and restart OSRM

### OSRM Won't Start
- Check Docker logs: `docker logs osrm-pune`
- Ensure `pune.osm.pbf` exists and is valid
- Try with smaller region first

## Fallback Behavior

If OSRM fails or returns invalid data, the system automatically:
- Uses straight-line distance calculation
- Estimates duration at 40 km/h average speed
- Adds 30% for road curves and traffic

The system works without OSRM, but routes won't follow actual roads.
