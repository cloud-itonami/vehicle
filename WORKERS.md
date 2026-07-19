# Vehicle Manufacturing Workers

This file lists the worker task types used by the vehicle manufacturing package
BPMN. It is a contract for LangServer/LangServer workers and container-backed heavy jobs;
it is not a deployment manifest.

## Task Types

| Task type | Worker pool | Responsibility |
|---|---|---|
| `automotive.package.profile` | `langserver` | Normalize vehicle program, model code, plant/line, vehicle kind, and required evidence groups. |
| `robotics.product.package.validate` | `langserver` | Validate the package manifest, required file groups, checksums, and references. |
| `automotive.file.catalog` | `cad-container` | Classify CAD, PLM, CAM, PCB, software, QA, compliance, and service files. |
| `automotive.ebom.mbom.align` | `tsukuru-worker` | Compare EBOM, MBOM, routing, supplier, and plant-specific material views. |
| `automotive.supply.process.link` | `tsukuru-worker` | Connect material requirements, suppliers, intermediate parts, people, responsible roles, and patent evidence to the package graph. |
| `automotive.material.procurement.plan` | `supplier-worker` | Plan steel, aluminum, resin, battery materials, semiconductors, harness, tires, glass, and logistics procurement. |
| `automotive.intermediate.process.plan` | `tsukuru-worker` | Plan stamping, casting, machining, molding, welding, coating, cell/module/pack, ECU, harness, seat, and subassembly flows. |
| `automotive.routing.plan` | `tsukuru-worker` | Build process routing for body, paint, powertrain, final assembly, EOL, and rework. |
| `automotive.tooling.plan` | `tsukuru-worker` | Register die, mold, fixture, jig, gauge, torque tool, robot program, and calibration needs. |
| `automotive.line.balance` | `tsukuru-worker` | Check takt time, station work content, buffers, and bottleneck risk. |
| `automotive.quality.gate` | `quality-worker` | Evaluate APQP, PPAP, DFMEA/PFMEA, control plan, MSA/SPC, EOL, and homologation evidence. |
| `automotive.eol.plan` | `quality-worker` | Plan VIN bind, ECU flashing, ADAS calibration, battery test, leak/NVH, dyno, and audit trail. |
| `automotive.software.release` | `quality-worker` | Bind firmware, ECU config, SBOM, calibration, cybersecurity, OTA, and rollback evidence. |
| `automotive.dpp.export` | `supplier-worker` | Export battery passport, digital product passport, repairability, recycling, and service data. |
| `automotive.rfq.export` | `supplier-worker` | Build RFQ package for tier suppliers, EMS, tooling suppliers, logistics, and validation labs. |

## File Groups

| Group | Preferred formats |
|---|---|
| CAD / PLM | STEP AP242, JT, native CAD optional, PLM export JSON/CSV |
| Drawings / GD&T | PDF, DXF/DWG, QIF, STEP PMI |
| CAM / tooling | G-code, robot program archive, fixture setup sheet, die/mold CAD |
| EBOM / MBOM / routing | CSV, JSON, PLM/MES export, B2MML where available |
| PCB / harness | IPC-2581, Gerber X2/X3, ODB++, IPC-D-356, harness XML/KBL |
| Software / calibration | AUTOSAR ARXML, A2L, DBC, ODX, CDD, SREC/HEX, SBOM CycloneDX/SPDX |
| Process / quality | BPMN, APQP, PPAP, DFMEA, PFMEA, control plan, MSA, SPC, 8D |
| EOL / compliance | measurement CSV, QIF, EOL JSON, homologation PDF, DPP JSON-LD |

## Graph Links

The automotive extension connects the package graph to canonical entity tables.

| Edge table | Source | Target |
|---|---|---|
| `edge_automotive_package_requires_material` | vehicle package | material requirement |
| `edge_automotive_material_supplied_by` | material requirement | `vertex_legal_entity` / LEI entity |
| `edge_automotive_process_uses_material` | manufacturing process | material requirement |
| `edge_automotive_process_produces_intermediate` | manufacturing process | intermediate part |
| `edge_automotive_intermediate_feeds_process` | intermediate part | downstream process |
| `edge_automotive_responsible_party` | package/process/gate | responsibility assignment / person |
| `edge_automotive_process_performed_by` | process | craftsperson, skilled worker, engineer, or legal entity |
| `edge_automotive_package_references_patent` | package | `vertex_patent` |

Verified real-entity examples for seed/import tests should use official source
URLs. Examples checked during this update:

| Entity | LEI | Source |
|---|---|---|
| DENSO CORPORATION | `549300RYPA10CQM3QK38` | GLEIF API |
| SUBARU CORPORATION | `549300N244BVAEE6HH86` | GLEIF API |
| Hyundai Motor Co / Kia Corp patent example | `US12325411B2` | Google Patents |

The dry-run seed fixture is
`80-data/schemas/automotive-manufacturing-graph-seed.json`. It includes
verified source URLs and public patent inventor names so importer tests can
exercise LEI, person, responsibility, and patent edges without fabricating
identity data.

## BPMN

The vehicle-specific process is
`etzhayyim-root/00-contracts/bpmn/com/etzhayyim/robotics/manufactureVehicleProductPackage.bpmn`.
It runs profile normalization, generic package validation, file cataloging,
routing, quality gate evaluation, EOL planning, and audit emission.
