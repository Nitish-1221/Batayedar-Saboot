const request = require('supertest');
const mongoose = require('mongoose');
const app = require('../src/app');
const { isDbConnected } = require('../src/config/db');
const { createTestFirebaseToken } = require('../src/config/firebase');
const Field = require('../src/models/Field');
const CropRecord = require('../src/models/CropRecord');
const Evidence = require('../src/models/Evidence');
const LandlordConfirmation = require('../src/models/LandlordConfirmation');
const User = require('../src/models/User');
require('./setup');

describe('MongoDB Atlas & Sync Persistence Suite', () => {
  const farmerA = {
    uid: 'farmer-uuid-a-111',
    phone: '+919811111111',
    role: 'BATAYDAR'
  };

  const farmerB = {
    uid: 'farmer-uuid-b-222',
    phone: '+919822222222',
    role: 'BATAYDAR'
  };

  let tokenA;
  let tokenB;

  beforeEach(async () => {
    tokenA = createTestFirebaseToken({ uid: farmerA.uid, email: 'farmerA@test.com', role: 'BATAYDAR' });
    tokenB = createTestFirebaseToken({ uid: farmerB.uid, email: 'farmerB@test.com', role: 'BATAYDAR' });

    // Register Farmer A
    await request(app)
      .post('/api/v1/auth/register')
      .set('Authorization', `Bearer ${tokenA}`)
      .send({
        fullName: 'Farmer A (Ramesh)',
        phone: farmerA.phone,
        role: 'BATAYDAR',
        village: 'Kisanpur',
        district: 'Patna',
        state: 'Bihar'
      });

    // Register Farmer B
    await request(app)
      .post('/api/v1/auth/register')
      .set('Authorization', `Bearer ${tokenB}`)
      .send({
        fullName: 'Farmer B (Suresh)',
        phone: farmerB.phone,
        role: 'BATAYDAR',
        village: 'Kisanpur',
        district: 'Patna',
        state: 'Bihar'
      });
  });

  describe('1. Database Connectivity and Health Verification', () => {
    it('should report database connected and healthy on GET /api/v1/health', async () => {
      const res = await request(app).get('/api/v1/health');

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('ok');
      expect(res.body.database.status).toBe('CONNECTED');
      expect(isDbConnected()).toBe(true);
    });
  });

  describe('2. Persistence Across All Schemas in MongoDB', () => {
    it('should persist User, Field, CropRecord, Evidence, and LandlordConfirmation in MongoDB', async () => {
      // 1. Verify User persisted
      const savedUser = await User.findOne({ userId: farmerA.uid });
      expect(savedUser).not.null;
      expect(savedUser.fullName).toBe('Farmer A (Ramesh)');
      expect(savedUser.role).toBe('BATAYDAR');

      // 2. Create and verify Field
      const field = await Field.create({
        fieldId: 'field-uuid-001',
        farmerId: farmerA.uid,
        fieldName: 'South Plot (Dakshin Khet)',
        village: 'Kisanpur',
        district: 'Patna',
        state: 'Bihar',
        areaAcres: 2.5
      });
      const savedField = await Field.findOne({ fieldId: 'field-uuid-001' });
      expect(savedField).not.null;
      expect(savedField.fieldName).toBe('South Plot (Dakshin Khet)');

      // 3. Create and verify CropRecord
      const cropRecord = await CropRecord.create({
        recordId: 'record-uuid-001',
        farmerId: farmerA.uid,
        fieldId: field.fieldId,
        cropType: 'Wheat (गेहूँ)',
        cultivationStartDate: new Date('2026-10-01'),
        status: 'ACTIVE'
      });
      const savedCrop = await CropRecord.findOne({ recordId: 'record-uuid-001' });
      expect(savedCrop).not.null;
      expect(savedCrop.cropType).toBe('Wheat (गेहूँ)');

      // 4. Create and verify Evidence metadata
      const evidence = await Evidence.create({
        evidenceId: 'evidence-uuid-001',
        recordId: cropRecord.recordId,
        farmerId: farmerA.uid,
        s3ObjectKey: `evidence/${farmerA.uid}/${cropRecord.recordId}/evidence-uuid-001.jpg`,
        s3Bucket: 'local',
        mimeType: 'image/jpeg',
        latitude: 25.5941,
        longitude: 85.1376,
        reportedGpsAccuracyMeters: 5.2,
        clientCapturedAtUtc: new Date(),
        clientReportedChecksum: 'abc123sha256',
        validationStatus: 'VERIFIED'
      });
      const savedEvidence = await Evidence.findOne({ evidenceId: 'evidence-uuid-001' });
      expect(savedEvidence).not.null;
      expect(savedEvidence.reportedGpsAccuracyMeters).toBe(5.2);

      // 5. Create and verify LandlordConfirmation
      const confirmation = await LandlordConfirmation.create({
        confirmationId: 'conf-uuid-001',
        recordId: cropRecord.recordId,
        farmerId: farmerA.uid,
        landlordName: 'Thakur Virendra Singh',
        landlordPhone: '+919900000001',
        status: 'REQUESTED'
      });
      const savedConf = await LandlordConfirmation.findOne({ confirmationId: 'conf-uuid-001' });
      expect(savedConf).not.null;
      expect(savedConf.status).toBe('REQUESTED');
    });
  });

  describe('3. Retryable Synchronization Idempotency & Deduplication', () => {
    it('should not create duplicate records when retryable sync push is received multiple times', async () => {
      const syncBatch = {
        idempotencyKey: 'retry-batch-key-999',
        fields: [
          {
            fieldId: 'idemp-field-01',
            fieldName: 'North Plot',
            village: 'Kisanpur',
            district: 'Patna',
            state: 'Bihar',
            areaAcres: 1.5
          }
        ],
        cropRecords: [
          {
            recordId: 'idemp-crop-01',
            fieldId: 'idemp-field-01',
            cropType: 'Paddy / Rice (धान)',
            cultivationStartDate: '2026-06-15T00:00:00.000Z',
            notes: 'Monsoon sowing'
          }
        ]
      };

      // Push 1
      const res1 = await request(app)
        .post('/api/v1/sync/push')
        .set('Authorization', `Bearer ${tokenA}`)
        .send(syncBatch);

      expect(res1.status).toBe(200);
      expect(res1.body.data.syncedFieldIds).toContain('idemp-field-01');
      expect(res1.body.data.syncedRecordIds).toContain('idemp-crop-01');

      // Verify count in MongoDB = 1
      const countFieldsAfterFirst = await Field.countDocuments({ fieldId: 'idemp-field-01' });
      const countCropsAfterFirst = await CropRecord.countDocuments({ recordId: 'idemp-crop-01' });
      expect(countFieldsAfterFirst).toBe(1);
      expect(countCropsAfterFirst).toBe(1);

      // Push 2 (Retry simulation with same IDs)
      const res2 = await request(app)
        .post('/api/v1/sync/push')
        .set('Authorization', `Bearer ${tokenA}`)
        .send(syncBatch);

      expect(res2.status).toBe(200);
      expect(res2.body.data.syncedFieldIds).toContain('idemp-field-01');
      expect(res2.body.data.syncedRecordIds).toContain('idemp-crop-01');

      // Verify count in MongoDB is STILL 1 (no duplicates created)
      const countFieldsAfterRetry = await Field.countDocuments({ fieldId: 'idemp-field-01' });
      const countCropsAfterRetry = await CropRecord.countDocuments({ recordId: 'idemp-crop-01' });
      expect(countFieldsAfterRetry).toBe(1);
      expect(countCropsAfterRetry).toBe(1);
    });
  });

  describe('4. Strict Persistence Requirement Before Sync Confirmation', () => {
    it('should NOT confirm sync for crop records linked to nonexistent fields', async () => {
      const syncBatchInvalid = {
        idempotencyKey: 'invalid-field-link-key',
        cropRecords: [
          {
            recordId: 'orphan-crop-01',
            fieldId: 'non-existent-field-id',
            cropType: 'Maize',
            cultivationStartDate: '2026-07-01T00:00:00.000Z'
          }
        ]
      };

      const res = await request(app)
        .post('/api/v1/sync/push')
        .set('Authorization', `Bearer ${tokenA}`)
        .send(syncBatchInvalid);

      expect(res.status).toBe(200);
      // Orphan record was not persisted, so it must NOT be in syncedRecordIds
      expect(res.body.data.syncedRecordIds).not.toContain('orphan-crop-01');

      const orphanInDb = await CropRecord.findOne({ recordId: 'orphan-crop-01' });
      expect(orphanInDb).toBeNull();
    });
  });

  describe('5. Cross-User Multi-Tenant Isolation During Sync', () => {
    it('should reject Farmer B trying to claim or link records to Farmer A field', async () => {
      // 1. Farmer A creates field
      await Field.create({
        fieldId: 'field-farmer-a-only',
        farmerId: farmerA.uid,
        fieldName: 'Farmer A Field',
        village: 'Kisanpur',
        district: 'Patna',
        state: 'Bihar'
      });

      // 2. Farmer B attempts to sync a crop record claiming Farmer A's field
      const maliciousBatch = {
        cropRecords: [
          {
            recordId: 'malicious-crop-01',
            fieldId: 'field-farmer-a-only',
            cropType: 'Sugarcane',
            cultivationStartDate: '2026-08-01T00:00:00.000Z'
          }
        ]
      };

      const res = await request(app)
        .post('/api/v1/sync/push')
        .set('Authorization', `Bearer ${tokenB}`)
        .send(maliciousBatch);

      expect(res.status).toBe(200);
      // Malicious crop record must NOT be confirmed as synced
      expect(res.body.data.syncedRecordIds).not.toContain('malicious-crop-01');

      // Must not exist in database
      const savedInDb = await CropRecord.findOne({ recordId: 'malicious-crop-01' });
      expect(savedInDb).toBeNull();
    });
  });
});
