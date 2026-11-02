const request = require('supertest');
const crypto = require('crypto');
const app = require('../src/app');
const { createTestFirebaseToken } = require('../src/config/firebase');
const Field = require('../src/models/Field');
const CropRecord = require('../src/models/CropRecord');
const Evidence = require('../src/models/Evidence');
const LandlordConfirmation = require('../src/models/LandlordConfirmation');
const User = require('../src/models/User');
require('./setup');

describe('Cloud Backup & Restore Integration Suite', () => {
  const farmerA = {
    uid: 'firebase-uid-farmer-alpha-101',
    phone: '+919876000001',
    role: 'BATAYDAR',
    email: 'farmer.alpha@bataydar.org',
    fullName: 'Shyam Kumar',
    village: 'Siwan',
    district: 'Siwan',
    state: 'Bihar'
  };

  const farmerB = {
    uid: 'firebase-uid-farmer-beta-202',
    phone: '+919876000002',
    role: 'BATAYDAR',
    email: 'farmer.beta@bataydar.org',
    fullName: 'Ram Lal',
    village: 'Patna',
    district: 'Patna',
    state: 'Bihar'
  };

  let tokenA;
  let tokenB;

  // A minimal valid 1x1 JPEG buffer
  const sampleJpeg = Buffer.from([
    0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01,
    0x01, 0x01, 0x00, 0x48, 0x00, 0x48, 0x00, 0x00, 0xff, 0xdb, 0x00, 0x43,
    0x00, 0x08, 0x06, 0x06, 0x07, 0x06, 0x05, 0x08, 0x07, 0x07, 0x07, 0x09,
    0x09, 0x08, 0x0a, 0x0c, 0x14, 0x0d, 0x0c, 0x0b, 0x0b, 0x0c, 0x19, 0x12,
    0x13, 0x0f, 0x14, 0x1d, 0x1a, 0x1f, 0x1e, 0x1d, 0x1a, 0x1c, 0x1c, 0x20,
    0x24, 0x2e, 0x27, 0x20, 0x22, 0x2c, 0x23, 0x1c, 0x1c, 0x28, 0x37, 0x29,
    0x2c, 0x30, 0x31, 0x34, 0x34, 0x34, 0x1f, 0x27, 0x39, 0x3d, 0x38, 0x32,
    0x3c, 0x2e, 0x33, 0x34, 0x32, 0xff, 0xc0, 0x00, 0x0b, 0x08, 0x00, 0x01,
    0x00, 0x01, 0x01, 0x01, 0x11, 0x00, 0xff, 0xc4, 0x00, 0x1f, 0x00, 0x00,
    0x01, 0x05, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
    0x09, 0x0a, 0x0b, 0xff, 0xda, 0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x3f,
    0x00, 0xbf, 0x00, 0xff, 0xd9
  ]);

  beforeEach(async () => {
    tokenA = createTestFirebaseToken({ uid: farmerA.uid, email: farmerA.email, role: 'BATAYDAR' });
    tokenB = createTestFirebaseToken({ uid: farmerB.uid, email: farmerB.email, role: 'BATAYDAR' });

    // Register Farmer A
    await request(app)
      .post('/api/v1/auth/register')
      .set('Authorization', `Bearer ${tokenA}`)
      .send({
        fullName: farmerA.fullName,
        phone: farmerA.phone,
        role: 'BATAYDAR',
        village: farmerA.village,
        district: farmerA.district,
        state: farmerA.state
      });

    // Register Farmer B
    await request(app)
      .post('/api/v1/auth/register')
      .set('Authorization', `Bearer ${tokenB}`)
      .send({
        fullName: farmerB.fullName,
        phone: farmerB.phone,
        role: 'BATAYDAR',
        village: farmerB.village,
        district: farmerB.district,
        state: farmerB.state
      });
  });

  describe('1. Account-Based Data Ownership & Sync Push', () => {
    it('associates uploaded fields, crop records, and evidence with Firebase UID', async () => {
      const fieldId = 'field-alpha-001';
      const recordId = 'crop-record-alpha-001';
      const evidenceId = 'evidence-alpha-001';

      const pushRes = await request(app)
        .post('/api/v1/sync/push')
        .set('Authorization', `Bearer ${tokenA}`)
        .send({
          idempotencyKey: 'sync-push-001',
          fields: [{
            fieldId,
            fieldName: 'North Field (उत्तर खेत)',
            village: 'Siwan',
            district: 'Siwan',
            state: 'Bihar',
            areaAcres: 2.5,
            surveyKhasraNumber: '102/4'
          }],
          cropRecords: [{
            recordId,
            fieldId,
            cropType: 'Wheat (गेहूँ)',
            cultivationStartDate: '2026-10-01T00:00:00.000Z',
            notes: 'Planted with organic fertilizer',
            landlordName: 'Landlord Mishra',
            landlordPhone: '+919988776655'
          }],
          evidenceList: [{
            evidenceId,
            recordId,
            latitude: 26.2183,
            longitude: 84.3567,
            reportedGpsAccuracyMeters: 4.2,
            clientCapturedAtUtc: '2026-10-15T08:30:00.000Z',
            clientReportedChecksum: 'dummy-checksum-sha256',
            notes: 'Healthy saplings germination stage'
          }]
        });

      expect(pushRes.status).toBe(200);
      expect(pushRes.body.success).toBe(true);
      expect(pushRes.body.data.syncedFieldIds).toContain(fieldId);
      expect(pushRes.body.data.syncedRecordIds).toContain(recordId);
      expect(pushRes.body.data.syncedEvidenceIds).toContain(evidenceId);

      // Verify MongoDB document ownership
      const fieldDoc = await Field.findOne({ fieldId });
      expect(fieldDoc).not.toBeNull();
      expect(fieldDoc.firebaseUid).toBe(farmerA.uid);

      const recordDoc = await CropRecord.findOne({ recordId });
      expect(recordDoc).not.toBeNull();
      expect(recordDoc.firebaseUid).toBe(farmerA.uid);

      const evidenceDoc = await Evidence.findOne({ evidenceId });
      expect(evidenceDoc).not.toBeNull();
      expect(evidenceDoc.firebaseUid).toBe(farmerA.uid);
    });
  });

  describe('2. Direct Evidence Upload (POST /api/v1/sync/upload)', () => {
    it('successfully uploads photo buffer, computes SHA-256, and links to crop record', async () => {
      const fieldId = 'field-upload-001';
      const recordId = 'record-upload-001';
      const evidenceId = 'evidence-photo-upload-001';

      // First create field & crop record
      await request(app)
        .post('/api/v1/sync/push')
        .set('Authorization', `Bearer ${tokenA}`)
        .send({
          fields: [{
            fieldId,
            fieldName: 'Riverbank Field',
            village: 'Siwan',
            district: 'Siwan',
            state: 'Bihar',
            areaAcres: 1.5
          }],
          cropRecords: [{
            recordId,
            fieldId,
            cropType: 'Paddy / Rice (धान)',
            cultivationStartDate: '2026-07-01T00:00:00.000Z'
          }]
        });

      const clientSha256 = crypto.createHash('sha256').update(sampleJpeg).digest('hex');

      const uploadRes = await request(app)
        .post('/api/v1/sync/upload')
        .set('Authorization', `Bearer ${tokenA}`)
        .field('recordId', recordId)
        .field('evidenceId', evidenceId)
        .field('latitude', '26.2201')
        .field('longitude', '84.3582')
        .field('reportedGpsAccuracyMeters', '3.5')
        .field('capturedAtUtc', new Date().toISOString())
        .field('clientReportedChecksum', clientSha256)
        .field('notes', 'Direct photo upload test')
        .attach('photo', sampleJpeg, 'evidence.jpg');

      expect(uploadRes.status).toBe(201);
      expect(uploadRes.body.success).toBe(true);
      expect(uploadRes.body.data.evidenceId).toBe(evidenceId);
      expect(uploadRes.body.data.validationStatus).toBe('VERIFIED');
      expect(uploadRes.body.data.sha256Checksum).toBe(clientSha256);
      expect(uploadRes.body.data.photoUrl).toBeDefined();

      // Check crop record updated
      const updatedCrop = await CropRecord.findOne({ recordId });
      expect(updatedCrop.hasEvidence).toBe(true);
    });
  });

  describe('3. Account Cloud Restore (GET /api/v1/sync/restore)', () => {
    it('restores all fields, crop records, evidence photos, and user profile after app reinstall / data clear', async () => {
      const fieldId = 'field-restore-001';
      const recordId = 'record-restore-001';
      const evidenceId = 'evidence-restore-001';

      // 1. User records and syncs data to cloud
      await request(app)
        .post('/api/v1/sync/push')
        .set('Authorization', `Bearer ${tokenA}`)
        .send({
          fields: [{
            fieldId,
            fieldName: 'Family Heritage Farm',
            village: 'Siwan',
            district: 'Siwan',
            state: 'Bihar',
            areaAcres: 4.0,
            surveyKhasraNumber: '78/2'
          }],
          cropRecords: [{
            recordId,
            fieldId,
            cropType: 'Mustard (सरसों)',
            cultivationStartDate: '2026-10-10T00:00:00.000Z',
            notes: 'Yellow flowers in bloom',
            landlordName: 'Optional Landlord',
            landlordPhone: '+919123456789'
          }]
        });

      // Upload evidence photo
      await request(app)
        .post('/api/v1/sync/upload')
        .set('Authorization', `Bearer ${tokenA}`)
        .field('recordId', recordId)
        .field('evidenceId', evidenceId)
        .field('latitude', '26.2195')
        .field('longitude', '84.3571')
        .field('reportedGpsAccuracyMeters', '4.0')
        .field('capturedAtUtc', '2026-10-20T10:00:00.000Z')
        .field('notes', 'Full bloom snapshot')
        .attach('photo', sampleJpeg, 'crop_photo.jpg');

      // 2. Simulate User clearing local app data, reinstalling, and logging in again
      // Calling GET /api/v1/sync/restore
      const restoreRes = await request(app)
        .get('/api/v1/sync/restore')
        .set('Authorization', `Bearer ${tokenA}`);

      expect(restoreRes.status).toBe(200);
      expect(restoreRes.body.success).toBe(true);
      expect(restoreRes.body.data).toBeDefined();

      const { profile, fields, cropRecords, evidenceList, totalCounts } = restoreRes.body.data;

      // Profile restored
      expect(profile.phone).toBe(farmerA.phone);
      expect(profile.fullName).toBe(farmerA.fullName);
      expect(profile.village).toBe(farmerA.village);

      // Fields restored
      expect(fields.length).toBe(1);
      expect(fields[0].fieldId).toBe(fieldId);
      expect(fields[0].fieldName).toBe('Family Heritage Farm');

      // Crop records restored
      expect(cropRecords.length).toBe(1);
      expect(cropRecords[0].recordId).toBe(recordId);
      expect(cropRecords[0].cropType).toBe('Mustard (सरसों)');

      // Evidence restored with accessible download URL
      expect(evidenceList.length).toBe(1);
      expect(evidenceList[0].evidenceId).toBe(evidenceId);
      expect(evidenceList[0].photoUrl).toBeDefined();
      expect(evidenceList[0].photoUrl.length).toBeGreaterThan(0);
      expect(evidenceList[0].validationStatus).toBe('VERIFIED');

      expect(totalCounts.fields).toBe(1);
      expect(totalCounts.cropRecords).toBe(1);
      expect(totalCounts.evidenceList).toBe(1);
    });
  });

  describe('4. Multi-Tenant Isolation & Cross-User Security', () => {
    it('Farmer B cannot access Farmer A\'s restored data or evidence', async () => {
      // Farmer A creates private records
      await request(app)
        .post('/api/v1/sync/push')
        .set('Authorization', `Bearer ${tokenA}`)
        .send({
          fields: [{
            fieldId: 'field-secret-farmer-a',
            fieldName: 'Secret Plot A',
            village: 'Siwan',
            district: 'Siwan',
            state: 'Bihar'
          }],
          cropRecords: [{
            recordId: 'record-secret-farmer-a',
            fieldId: 'field-secret-farmer-a',
            cropType: 'Maize (मक्का)',
            cultivationStartDate: '2026-06-01T00:00:00.000Z'
          }]
        });

      // Farmer B calls restore
      const farmerBRestore = await request(app)
        .get('/api/v1/sync/restore')
        .set('Authorization', `Bearer ${tokenB}`);

      expect(farmerBRestore.status).toBe(200);
      expect(farmerBRestore.body.data.fields).toHaveLength(0);
      expect(farmerBRestore.body.data.cropRecords).toHaveLength(0);
      expect(farmerBRestore.body.data.evidenceList).toHaveLength(0);
      expect(farmerBRestore.body.data.profile.phone).toBe(farmerB.phone);
      expect(farmerBRestore.body.data.profile.fullName).toBe(farmerB.fullName);
    });
  });

  describe('5. Incremental Changes & Conflict Resolution', () => {
    it('pulls only incremental changes since timestamp', async () => {
      const past = new Date(Date.now() - 60000).toISOString();

      await request(app)
        .post('/api/v1/sync/push')
        .set('Authorization', `Bearer ${tokenA}`)
        .send({
          fields: [{
            fieldId: 'field-changes-001',
            fieldName: 'Incremental Field',
            village: 'Siwan',
            district: 'Siwan',
            state: 'Bihar'
          }],
          cropRecords: [{
            recordId: 'record-changes-001',
            fieldId: 'field-changes-001',
            cropType: 'Gram / Chana (चना)',
            cultivationStartDate: '2026-10-01T00:00:00.000Z'
          }]
        });

      const changesRes = await request(app)
        .get(`/api/v1/sync/changes?since=${encodeURIComponent(past)}`)
        .set('Authorization', `Bearer ${tokenA}`);

      expect(changesRes.status).toBe(200);
      expect(changesRes.body.data.fields.length).toBeGreaterThanOrEqual(1);
      expect(changesRes.body.data.cropRecords.length).toBeGreaterThanOrEqual(1);
    });

    it('does not overwrite newer server data with older local client data', async () => {
      const fieldId = 'field-conflict-001';

      // 1. Initial push
      await request(app)
        .post('/api/v1/sync/push')
        .set('Authorization', `Bearer ${tokenA}`)
        .send({
          fields: [{
            fieldId,
            fieldName: 'Server Newer Name',
            village: 'Siwan',
            district: 'Siwan',
            state: 'Bihar'
          }]
        });

      // 2. Fetch server document and check its updatedAt
      const serverField = await Field.findOne({ fieldId });
      expect(serverField.fieldName).toBe('Server Newer Name');

      // 3. Client tries to push older data (dated 1 day in the past)
      const olderTimestamp = new Date(Date.now() - 86400000).toISOString();
      await request(app)
        .post('/api/v1/sync/push')
        .set('Authorization', `Bearer ${tokenA}`)
        .send({
          fields: [{
            fieldId,
            fieldName: 'Older Stale Name',
            village: 'Siwan',
            district: 'Siwan',
            state: 'Bihar',
            updatedAt: olderTimestamp
          }]
        });

      // 4. Verify server data was preserved and NOT overwritten
      const preservedField = await Field.findOne({ fieldId });
      expect(preservedField.fieldName).toBe('Server Newer Name');
    });
  });
});

