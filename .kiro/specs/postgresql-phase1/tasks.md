# Phase 1: PostgreSQL Basic Integration - Tasks

## Goal
Add PostgreSQL persistence to emergency-service with basic dual-write pattern (no outbox yet).

## Timeline
2 weeks (10 working days)

---

## Week 1: Setup & Emergency Service

### Day 1-2: Environment & Dependencies
- [x] 1. PostgreSQL container running
- [x] 2. Add JPA dependencies to emergency-service pom.xml
- [x] 3. Configure application.yml with PostgreSQL connection
- [x] 4. Test database connection on startup

### Day 3-4: Entity & Repository
- [x] 5. Create Emergency entity class with JPA annotations
- [x] 6. Create EmergencyRepository interface
- [x] 7. Write unit test for repository
- [x] 8. Verify entity can be saved to database

### Day 5: Service Integration
- [x] 9. Update EmergencyService to save to PostgreSQL (dual-write pattern)
- [x] 10. Keep existing Kafka publishing
- [x] 11. Test emergency creation end-to-end (all tests passing)
- [ ] 12. Verify data in both PostgreSQL and Redis (ready to test manually)

---

## Week 2: Expand to Other Services

### Day 6-7: Ambulance Service
- [x] 13. Add JPA dependencies to ambulance-service
- [x] 14. Create Ambulance entity
- [x] 15. Create AmbulanceRepository
- [x] 16. Update service to persist ambulance data
- [ ] 17. Test ambulance state changes persist

### Day 8: Dispatch Service
- [x] 18. Add JPA dependencies to dispatch-service
- [x] 19. Create AssignmentHistory entity
- [x] 20. Create AssignmentHistoryRepository
- [x] 21. Record assignments to database
- [x] 22. Test assignment history is saved

### Day 9: Integration Testing
- [ ] 23. Create end-to-end integration test
- [ ] 24. Test: Create emergency → Assign ambulance → Verify in DB
- [ ] 25. Test: Query historical data from PostgreSQL
- [ ] 26. Verify Redis still works for real-time dispatch

### Day 10: Documentation & Demo
- [ ] 27. Update README with PostgreSQL setup
- [ ] 28. Document database schema
- [ ] 29. Create demo script showing PostgreSQL integration
- [ ] 30. Record 5-minute demo video

---

## Success Criteria

### Functional
- [ ] Emergency creation saves to PostgreSQL
- [ ] Ambulance state changes persist to PostgreSQL
- [ ] Assignment history recorded in PostgreSQL
- [ ] Can query historical data
- [ ] Redis still works for real-time operations

### Performance
- [ ] Dispatch latency still < 2 seconds
- [ ] No performance degradation
- [ ] PostgreSQL writes don't block dispatch

### Testing
- [ ] All existing tests still pass
- [ ] New integration test passes
- [ ] Manual end-to-end test works

---

## Out of Scope (Phase 2)
- ⏸️ Outbox pattern
- ⏸️ Foreign key constraints
- ⏸️ Optimistic locking (@Version)
- ⏸️ Complex queries
- ⏸️ Analytics dashboards

---

## Notes
- Keep it simple: basic dual-write is acceptable for Phase 1
- Focus on getting it working, not perfect
- Document issues for Phase 2
- Test continuously
