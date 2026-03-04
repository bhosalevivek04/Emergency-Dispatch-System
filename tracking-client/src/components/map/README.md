# Map Components

This directory contains reusable map components for the Emergency Dispatch System using react-leaflet.

## Components

### MapComponent
Base map component that initializes a Leaflet map with OpenStreetMap tiles.

**Props:**
- `center` (array): Initial map center coordinates [lat, lng]. Default: [18.5204, 73.8567]
- `zoom` (number): Initial zoom level. Default: 13
- `onMapClick` (function): Callback when map is clicked, receives (lat, lng)
- `children` (node): Child components (markers, polylines, etc.)
- `style` (object): Custom styles for the map container

**Example:**
```jsx
<MapComponent 
  center={[18.5204, 73.8567]} 
  zoom={13}
  onMapClick={(lat, lng) => console.log(lat, lng)}
>
  {/* Add markers and other map elements here */}
</MapComponent>
```

### EmergencyMarker
Displays emergency locations as red pins with priority badges.

**Props:**
- `emergency` (object): Emergency data
  - `id` (string): Emergency ID
  - `latitude` (number): Emergency latitude
  - `longitude` (number): Emergency longitude
  - `priority` (string): Priority level (HIGH/MEDIUM/LOW)
  - `status` (string): Emergency status
  - `assignedAmbulanceId` (string, optional): Assigned ambulance ID
- `onClick` (function): Callback when marker is clicked, receives emergency object

**Example:**
```jsx
<EmergencyMarker 
  emergency={{
    id: 'EMG001',
    latitude: 18.5204,
    longitude: 73.8567,
    priority: 'HIGH',
    status: 'PENDING'
  }}
  onClick={(emergency) => console.log(emergency)}
/>
```

### AmbulanceMarker
Displays ambulance locations with status-based coloring and smooth animation.

**Props:**
- `ambulance` (object): Ambulance data
  - `id` (string): Ambulance ID
  - `latitude` (number): Ambulance latitude
  - `longitude` (number): Ambulance longitude
  - `status` (string): Status (AVAILABLE/ASSIGNED/ON_ROUTE)
  - `speed` (number, optional): Current speed in km/h
  - `assignedEmergencyId` (string, optional): Assigned emergency ID
- `onClick` (function): Callback when marker is clicked, receives ambulance object

**Colors:**
- Green: AVAILABLE
- Yellow: ASSIGNED
- Red: ON_ROUTE

**Example:**
```jsx
<AmbulanceMarker 
  ambulance={{
    id: 'AMB001',
    latitude: 18.5204,
    longitude: 73.8567,
    status: 'AVAILABLE',
    speed: 45.5
  }}
  onClick={(ambulance) => console.log(ambulance)}
/>
```

### RoutePolyline
Draws a dashed line from ambulance to emergency location.

**Props:**
- `ambulancePosition` (object): Ambulance position
  - `latitude` (number): Ambulance latitude
  - `longitude` (number): Ambulance longitude
- `emergencyPosition` (object): Emergency position
  - `latitude` (number): Emergency latitude
  - `longitude` (number): Emergency longitude
- `color` (string): Line color. Default: '#3b82f6'
- `weight` (number): Line width. Default: 4
- `opacity` (number): Line opacity. Default: 0.7
- `dashArray` (string): Dash pattern. Default: '10, 10'

**Example:**
```jsx
<RoutePolyline 
  ambulancePosition={{ latitude: 18.5204, longitude: 73.8567 }}
  emergencyPosition={{ latitude: 18.5304, longitude: 73.8667 }}
  color="#3b82f6"
/>
```

## Usage Example

Complete example showing all components together:

```jsx
import { 
  MapComponent, 
  EmergencyMarker, 
  AmbulanceMarker, 
  RoutePolyline 
} from './components/map';

function Dashboard() {
  const emergencies = [...]; // Your emergency data
  const ambulances = [...]; // Your ambulance data

  return (
    <MapComponent 
      center={[18.5204, 73.8567]} 
      zoom={13}
      onMapClick={(lat, lng) => console.log('Clicked:', lat, lng)}
    >
      {emergencies.map(emergency => (
        <EmergencyMarker 
          key={emergency.id}
          emergency={emergency}
          onClick={(e) => console.log('Emergency clicked:', e)}
        />
      ))}
      
      {ambulances.map(ambulance => (
        <AmbulanceMarker 
          key={ambulance.id}
          ambulance={ambulance}
          onClick={(a) => console.log('Ambulance clicked:', a)}
        />
      ))}
      
      {/* Show route for assigned ambulances */}
      {ambulances
        .filter(a => a.assignedEmergencyId)
        .map(ambulance => {
          const emergency = emergencies.find(e => e.id === ambulance.assignedEmergencyId);
          return emergency ? (
            <RoutePolyline
              key={`route-${ambulance.id}`}
              ambulancePosition={ambulance}
              emergencyPosition={emergency}
            />
          ) : null;
        })
      }
    </MapComponent>
  );
}
```

## Features

- **Smooth Animation**: AmbulanceMarker includes smooth position transitions using requestAnimationFrame
- **Status-Based Styling**: Both markers use color coding to indicate priority/status
- **Interactive Popups**: Click markers to see detailed information
- **Map Click Events**: Capture map clicks for creating new emergencies
- **Customizable**: All components accept style and behavior props
