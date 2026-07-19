# etzhayyim-project-vehicle

Vehicle ownership registry for vehicle.etzhayyim.com — vehicle registration, ownership transfer, inspection records. SQL graph: `(:VehicleOwner)-[:OWNS]->(:Vehicle)`, `(:InspectionRecord)-[:INSPECTS]->(:Vehicle)`.

## Architecture

```
Browser → vehicle.etzhayyim.com (appview mode)
       → API → /etzhayyim.vehicle.v1.VehicleCommandService/... + /etzhayyim.vehicle.v1.VehicleQueryService/...
                  ↓
           App: etzhayyim-wasm-vehicle-vh1cl3rk
             ├─ register_vehicle / update_vehicle / change_status
             ├─ register_owner / transfer_ownership
             ├─ record_inspection / check_inspection_status
             └─ SQL graph → Vehicle / VehicleOwner / OwnershipRecord / InspectionRecord
```

## Component

| Component | Folder | Role |
|---|---|---|
| vehicle-api | `wasm/etzhayyim-wasm-vehicle-vh1cl3rk/` | XRPC API (appview, zero frontend) |

## SQL Graph

| Label | Purpose | Key Properties |
|---|---|---|
| `:Vehicle` | Vehicle record | vehicle_id, vin, plate_number, make, model, year, status |
| `:VehicleOwner` | Owner entity | owner_id, owner_type, name, identifier |
| `:OwnershipRecord` | Ownership transfer record | record_id, vehicle_id, owner_id, acquired_at, disposed_at |
| `:InspectionRecord` | Inspection result | inspection_id, vehicle_id, result, inspection_date, expiry_date |

## Edges

| Edge | From | To | Properties |
|---|---|---|---|
| `OWNS` | VehicleOwner | Vehicle | acquired_at, disposed_at, ownership_type |
| `INSPECTS` | InspectionRecord | Vehicle | — |

## DID Architecture

| DID | Purpose |
|---|---|
| `did:web:vehicle.etzhayyim.com` | Primary (controller) |
| `did:web:vehicle.etzhayyim.com:vehicle:{vin}` | Per-vehicle DID |
| `did:web:vehicle.etzhayyim.com:owner:{owner_id}` | Per-owner DID |

## Build & Deploy

```bash
cd 60-apps/etzhayyim-project-vehicle/wasm/etzhayyim-wasm-vehicle-vh1cl3rk
etzhayyim build
etzhayyim deploy --smoke-url https://vh1cl3rk.etzhayyim.com/health
```
