const request = require('supertest');
const crypto = require('crypto');
const app = require('../src/app');
const storageService = require('../src/services/storageService');
const User = require('../src/models/User');
require('./setup');

describe('Evidence Verification & Offline Synchronization', () => {
  let token;
  let fieldId;
  let recordId;

  // Real JPEG header (FF D8 FF E0 ...)
  const sampleJpegBuffer = Buffer.from([
    0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01,
    0x01, 0x01, 0x00, 0x60, 0x00, 0x60, 0x00, 0x00, 0xff, 0xdb, 0x00, 0x43,
    0x00, 0x08, 0x06, 0x06, 0x07, 0x06, 0x05, 0x08, 0x07, 0x07, 0x07, 0x09
  ]);
  const sampleJpegSha256 = crypto.createHash('sha256').update(sampleJpegBuffer).digest('hex');

  beforeEach(async () => {
    const regRes = await request(app).post('/api/v1/auth/register').send({
      fullName: 'Devendra Prasad',
      phone: '+919444455555',
      password: 'StrongPassword1!',
      village: 'Samastipur',
      district: 'Samastipur',
      state: 'Bihar'
    });
    token = regRes.body.data.tokens.accessToken;

    const fRes = await request(app)
      .post('/api/v1/fields')
      .set('Authorization', `Bearer ${token}`)
      .send({
        fieldName: 'South Plot',
        village: 'Samastipur',
        district: 'Samastipur',
        state: 'Bihar'
      });
    fieldId = fRes.body.data.field.fieldId;

    const rRes = await request(app)
      .post('/api/v1/crop-records')
      .set('Authorization', `Bearer ${token}`)
      .send({
        fieldId,
        cropType: 'Mustard (Sarson)',
        cultivationStartDate: '2026-10-15T00:00:00Z'
      });
    recordId = rRes.body.data.record.recordId;
  });

  describe('Evidence Upload & Verification Workflow', () => {
    it('should generate an upload URL and create evidence in PENDING_UPLOAD status', async () => {
      const res = await request(app)
        .post('/api/v1/evidence/upload-url')
        .set('Authorization', `Bearer ${token}`)
        .send({
          recordId,
          latitude: 25.8643,
          longitude: 85.7821,
          reportedGpsAccuracyMeters: 8.5,
          clientCapturedAtUtc: new Date().toISOString()
        });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.evidenceId).toBeDefined();
      expect(res.body.data.uploadUrl).toBeDefined();
      expect(res.body.data.gpsAccuracyAcceptable).toBe(true);
    });

    it('should complete upload verification: calculate server SHA-256 and confirm hash match', async () => {
      // Step 1: Request upload URL
      const urlRes = await request(app)
        .post('/api/v1/evidence/upload-url')
        .set('Authorization', `Bearer ${token}`)
        .send({
          recordId,
          latitude: 25.8643,
          longitude: 85.7821,
          reportedGpsAccuracyMeters: 6.2,
          clientCapturedAtUtc: new Date().toISOString()
        });

      const { evidenceId, objectKey } = urlRes.body.data;

      // Step 2: Simulate writing binary photograph into storage vault
      await storageService.saveLocalFile(objectKey, sampleJpegBuffer);

      // Step 3: Complete upload notification with client checksum
      const completeRes = await request(app)
        .post(`/api/v1/evidence/${evidenceId}/complete`)
        .set('Authorization', `Bearer ${token}`)
        .send({
          clientReportedChecksum: sampleJpegSha256
        });

      expect(completeRes.status).toBe(200);
      expect(completeRes.body.success).toBe(true);
      expect(completeRes.body.data.evidence.validationStatus).toBe('VERIFIED');
      expect(completeRes.body.data.evidence.sha256Checksum).toBe(sampleJpegSha256);
      expect(completeRes.body.data.evidence.hashMatch).toBe(true);
    });

    it('should reject evidence completion if file was corrupted or spoofed (non-image bytes)', async () => {
      const urlRes = await request(app)
        .post('/api/v1/evidence/upload-url')
        .set('Authorization', `Bearer ${token}`)
        .send({
          recordId,
          latitude: 25.8643,
          longitude: 85.7821,
          reportedGpsAccuracyMeters: 10.0,
          clientCapturedAtUtc: new Date().toISOString()
        });

      const { evidenceId, objectKey } = urlRes.body.data;

      // Corrupted file (plain text instead of valid image magic bytes)
      const corruptedBuffer = Buffer.from('NOT_AN_IMAGE_FILE_RANDOM_TEXT_HERE');
      await storageService.saveLocalFile(objectKey, corruptedBuffer);

      const completeRes = await request(app)
        .post(`/api/v1/evidence/${evidenceId}/complete`)
        .set('Authorization', `Bearer ${token}`)
        .send({
          clientReportedChecksum: '1111111111111111111111111111111111111111111111111111111111111111'
        });

      expect(completeRes.status).toBe(400);
      expect(completeRes.body.error.code).toBe('EVIDENCE_VERIFICATION_FAILED');
    });
  });

  describe('Offline Synchronization (Batch Push & Pull)', () => {
    it('should successfully push a batch of offline records with idempotency', async () => {
      const batchFieldId = crypto.randomUUID();
      const batchRecordId = crypto.randomUUID();
      const batchEvidenceId = crypto.randomUUID();

      const pushPayload = {
        idempotencyKey: 'mobile-sync-req-12345',
        fields: [
          {
            fieldId: batchFieldId,
            fieldName: 'Offline Parcel 1',
            village: 'Samastipur',
            district: 'Samastipur',
            state: 'Bihar',
            areaAcres: 1.2
          }
        ],
        cropRecords: [
          {
            recordId: batchRecordId,
            fieldId: batchFieldId,
            cropType: 'Gram (Chana)',
            cultivationStartDate: '2026-10-20T00:00:00Z'
          }
        ],
        evidenceList: [
          {
            evidenceId: batchEvidenceId,
            recordId: batchRecordId,
            latitude: 25.86,
            longitude: 85.78,
            reportedGpsAccuracyMeters: 5.0,
            clientCapturedAtUtc: new Date().toISOString(),
            clientReportedChecksum: sampleJpegSha256
          }
        ]
      };

      // Push first time
      const res1 = await request(app)
        .post('/api/v1/sync/push')
        .set('Authorization', `Bearer ${token}`)
        .send(pushPayload);

      expect(res1.status).toBe(200);
      expect(res1.body.data.syncedFieldIds).toContain(batchFieldId);
      expect(res1.body.data.syncedRecordIds).toContain(batchRecordId);

      // Push duplicate second time (Idempotent: should succeed safely without creating duplicate entries)
      const res2 = await request(app)
        .post('/api/v1/sync/push')
        .set('Authorization', `Bearer ${token}`)
        .send(pushPayload);

      expect(res2.status).toBe(200);

      // Verify records are retrievable via pull
      const pullRes = await request(app)
        .get('/api/v1/sync/pull')
        .set('Authorization', `Bearer ${token}`);

      expect(pullRes.status).toBe(200);
      expect(pullRes.body.data.fields.some(f => f.fieldId === batchFieldId)).toBe(true);
    });
  });

  describe('Landlord Confirmation Workflow', () => {
    it('should create confirmation request and record verbal response', async () => {
      // Pre-register landlord in the system
      await User.create({
        userId: 'landlord-suresh-123',
        fullName: 'Suresh Chandra Sharma',
        phone: '+919876500000',
        role: 'LANDLORD'
      });

      // Step 1: Create confirmation request
      const reqRes = await request(app)
        .post(`/api/v1/crop-records/${recordId}/confirmation-requests`)
        .set('Authorization', `Bearer ${token}`)
        .send({
          landlordName: 'Suresh Chandra Sharma',
          landlordPhone: '+919876500000',
          verificationMethod: 'DIRECT_VERBAL',
          notes: 'Spoke with landlord in person at the panchayat office'
        });

      expect(reqRes.status).toBe(201);
      const confirmationId = reqRes.body.data.confirmation.confirmationId;
      expect(confirmationId).toBeDefined();

      // Step 2: Record response
      const respondRes = await request(app)
        .post(`/api/v1/confirmations/${confirmationId}/respond`)
        .set('Authorization', `Bearer ${token}`)
        .send({
          status: 'CONFIRMED_VERBAL',
          witnessName: 'Mukhiya Ji',
          notes: 'Confirmed 50-50 crop share arrangement for Rabi season'
        });

      expect(respondRes.status).toBe(200);
      expect(respondRes.body.data.confirmation.status).toBe('CONFIRMED_VERBAL');
      expect(respondRes.body.data.confirmation.witnessName).toBe('Mukhiya Ji');
    });
  });

  describe('Storage Vault Security & Authorization Checks', () => {
    it('should reject local binary upload if upload token is missing or forged', async () => {
      const urlRes = await request(app)
        .post('/api/v1/evidence/upload-url')
        .set('Authorization', `Bearer ${token}`)
        .send({
          recordId,
          latitude: 25.8643,
          longitude: 85.7821,
          reportedGpsAccuracyMeters: 7.0,
          clientCapturedAtUtc: new Date().toISOString()
        });

      const { evidenceId } = urlRes.body.data;

      // Missing token
      const noTokenRes = await request(app)
        .post(`/api/v1/evidence/upload-binary?evidenceId=${evidenceId}`)
        .set('Content-Type', 'image/jpeg')
        .send(sampleJpegBuffer);

      expect(noTokenRes.status).toBe(400);

      // Forged token
      const forgedTokenRes = await request(app)
        .post(`/api/v1/evidence/upload-binary?evidenceId=${evidenceId}&token=forged-fake-token-12345`)
        .set('Content-Type', 'image/jpeg')
        .send(sampleJpegBuffer);

      expect(forgedTokenRes.status).toBe(403);
    });

    it('should reject unauthorized or path-traversal attempts on evidence streaming endpoint', async () => {
      // Unauthenticated and without token
      const unauthRes = await request(app)
        .get('/api/v1/evidence/file/evidence%2Ffarmer1%2Frec1%2Fevd1.jpg');

      expect(unauthRes.status).toBe(404);

      // Path traversal attack attempt
      const traversalRes = await request(app)
        .get('/api/v1/evidence/file/..%2F..%2Fpackage.json')
        .set('Authorization', `Bearer ${token}`);

      expect([400, 404]).toContain(traversalRes.status);
    });

    it('should support deleting a crop record and prevent accessing deleted record', async () => {
      const delRes = await request(app)
        .delete(`/api/v1/crop-records/${recordId}`)
        .set('Authorization', `Bearer ${token}`);

      expect(delRes.status).toBe(200);
      expect(delRes.body.success).toBe(true);

      const getRes = await request(app)
        .get(`/api/v1/crop-records/${recordId}`)
        .set('Authorization', `Bearer ${token}`);

      expect(getRes.status).toBe(404);
    });

    it('should reject cross-tenant sync push referencing another farmer\'s field', async () => {
      // Register second farmer
      const reg2 = await request(app).post('/api/v1/auth/register').send({
        fullName: 'Ramkishan Yadav',
        phone: '+919666677777',
        password: 'StrongPassword2!',
        village: 'Darbhanga',
        district: 'Darbhanga',
        state: 'Bihar'
      });
      const token2 = reg2.body.data.tokens.accessToken;

      // Farmer 2 attempts to push a crop record referencing fieldId owned by Farmer 1
      const maliciousPush = {
        idempotencyKey: 'malicious-push-001',
        cropRecords: [
          {
            recordId: crypto.randomUUID(),
            fieldId: fieldId, // Field owned by Farmer 1!
            cropType: 'Wheat',
            cultivationStartDate: '2026-10-25T00:00:00Z'
          }
        ]
      };

      const pushRes = await request(app)
        .post('/api/v1/sync/push')
        .set('Authorization', `Bearer ${token2}`)
        .send(maliciousPush);

      expect(pushRes.status).toBe(200);
      // Malicious record should NOT be in syncedRecordIds
      expect(pushRes.body.data.syncedRecordIds.length).toBe(0);
    });
  });
});
