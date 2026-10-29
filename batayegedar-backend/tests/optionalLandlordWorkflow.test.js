const request = require('supertest');
const crypto = require('crypto');
const app = require('../src/app');
const storageService = require('../src/services/storageService');
const User = require('../src/models/User');
const Field = require('../src/models/Field');
const CropRecord = require('../src/models/CropRecord');
const Evidence = require('../src/models/Evidence');
const LandlordConfirmation = require('../src/models/LandlordConfirmation');
require('./setup');

describe('Optional Landlord Details and Confirmation Workflow Suite', () => {
  const bataydar = {
    fullName: 'Mohan Kisan',
    phone: '+919800000010',
    password: 'Password123!',
    role: 'BATAYDAR',
    village: 'Siwan Village',
    district: 'Siwan',
    state: 'Bihar'
  };

  const landlord = {
    fullName: 'Rameshwar Zamindar',
    phone: '+919800000020',
    password: 'Password123!',
    role: 'LANDLORD',
    village: 'Siwan Village',
    district: 'Siwan',
    state: 'Bihar'
  };

  let bataydarToken = '';
  let landlordToken = '';
  let fieldId = '';

  const sampleJpeg = Buffer.from([
    0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01,
    0x01, 0x01, 0x00, 0x60, 0x00, 0x60, 0x00, 0x00, 0xff, 0xdb, 0x00, 0x43,
    0x00, 0x08, 0x06, 0x06, 0x07, 0x06, 0x05, 0x08, 0x07, 0x07, 0x07, 0x09
  ]);
  const sampleJpegSha256 = crypto.createHash('sha256').update(sampleJpeg).digest('hex');

  beforeEach(async () => {
    // 1. Register Bataydar
    const resBataydar = await request(app)
      .post('/api/v1/auth/register')
      .send(bataydar);
    bataydarToken = resBataydar.body.data.tokens.accessToken;

    // 2. Register Landlord
    const resLandlord = await request(app)
      .post('/api/v1/auth/register')
      .send(landlord);
    landlordToken = resLandlord.body.data.tokens.accessToken;

    // 3. Create a Field for Bataydar
    fieldId = 'field-' + crypto.randomUUID();
    await Field.create({
      fieldId,
      farmerId: resBataydar.body.data.user.userId,
      fieldName: 'Uttari Talab Khet',
      village: 'Siwan Village',
      district: 'Siwan',
      state: 'Bihar',
      areaAcres: 3.5
    });
  });

  describe('1. Saving Crop Record Without Landlord Details', () => {
    it('should save a crop record with empty landlord fields and set status to NOT_REQUESTED', async () => {
      const recordId = crypto.randomUUID();
      const res = await request(app)
        .post('/api/v1/crop-records')
        .set('Authorization', `Bearer ${bataydarToken}`)
        .send({
          recordId,
          fieldId,
          cropType: 'Paddy / Rice (धान)',
          cultivationStartDate: '2026-06-20',
          notes: 'Monsoon cultivation',
          landlordName: '',
          landlordPhone: ''
        });

      expect(res.status).toBe(201);
      expect(res.body.success).toBe(true);
      expect(res.body.data.record.landlordName).toBe('');
      expect(res.body.data.record.landlordPhone).toBe('');
      expect(res.body.data.record.landlordConfirmationStatus).toBe('NOT_REQUESTED');

      // Verify persisted in MongoDB
      const saved = await CropRecord.findOne({ recordId });
      expect(saved).not.toBeNull();
      expect(saved.landlordConfirmationStatus).toBe('NOT_REQUESTED');
    });

    it('should NOT create any LandlordConfirmation document when record is saved without requesting confirmation', async () => {
      const recordId = crypto.randomUUID();
      await request(app)
        .post('/api/v1/crop-records')
        .set('Authorization', `Bearer ${bataydarToken}`)
        .send({
          recordId,
          fieldId,
          cropType: 'Wheat (गेहूँ)',
          cultivationStartDate: '2026-11-01',
          landlordName: 'Optional Landowner',
          landlordPhone: '+919999999999'
        });

      // No confirmation requested yet
      const confirmations = await LandlordConfirmation.find({ recordId });
      expect(confirmations.length).toBe(0);
    });
  });

  describe('2. Capturing & Persisting Evidence Without Landlord Details', () => {
    it('should capture and verify GPS photographic evidence for a record that has no landlord details', async () => {
      const recordId = crypto.randomUUID();
      await request(app)
        .post('/api/v1/crop-records')
        .set('Authorization', `Bearer ${bataydarToken}`)
        .send({
          recordId,
          fieldId,
          cropType: 'Mustard (सरसों)',
          cultivationStartDate: '2026-10-15',
          landlordName: '',
          landlordPhone: ''
        });

      // Request upload URL
      const urlRes = await request(app)
        .post('/api/v1/evidence/upload-url')
        .set('Authorization', `Bearer ${bataydarToken}`)
        .send({
          recordId,
          latitude: 25.7711,
          longitude: 84.3512,
          reportedGpsAccuracyMeters: 8.5,
          clientCapturedAtUtc: new Date().toISOString()
        });

      expect(urlRes.status).toBe(200);
      const { evidenceId, objectKey } = urlRes.body.data;

      // Save binary photo into storage vault
      await storageService.saveLocalFile(objectKey, sampleJpeg);

      // Complete evidence upload
      const completeRes = await request(app)
        .post(`/api/v1/evidence/${evidenceId}/complete`)
        .set('Authorization', `Bearer ${bataydarToken}`)
        .send({
          clientReportedChecksum: sampleJpegSha256
        });

      expect(completeRes.status).toBe(200);
      expect(completeRes.body.success).toBe(true);
      expect(completeRes.body.data.evidence.validationStatus).toBe('VERIFIED');
      expect(completeRes.body.data.evidence.recordId).toBe(recordId);

      // Verify evidence persisted in MongoDB
      const savedEvidence = await Evidence.findOne({ recordId });
      expect(savedEvidence).not.toBeNull();
      expect(savedEvidence.validationStatus).toBe('VERIFIED');
    });
  });

  describe('3. Offline Synchronization Without Landlord Details', () => {
    it('should push crop records and evidence without landlord details to MongoDB', async () => {
      const offlineRecordId = 'rec-offline-' + crypto.randomUUID().slice(0, 8);
      const offlineEvidenceId = 'evd-offline-' + crypto.randomUUID().slice(0, 8);

      const syncPayload = {
        idempotencyKey: 'offline-sync-' + Date.now(),
        fields: [
          {
            fieldId,
            fieldName: 'Uttari Talab Khet',
            village: 'Siwan Village',
            district: 'Siwan',
            state: 'Bihar',
            areaAcres: 3.5
          }
        ],
        cropRecords: [
          {
            recordId: offlineRecordId,
            fieldId,
            cropType: 'Sugarcane (गन्ना)',
            cultivationStartDate: '2026-03-01T00:00:00.000Z',
            notes: 'Planted without landlord participation',
            landlordName: '',
            landlordPhone: ''
          }
        ],
        evidenceList: [
          {
            evidenceId: offlineEvidenceId,
            recordId: offlineRecordId,
            latitude: 25.772,
            longitude: 84.352,
            reportedGpsAccuracyMeters: 6.2,
            clientCapturedAtUtc: new Date().toISOString(),
            notes: 'Field germination evidence'
          }
        ]
      };

      const res = await request(app)
        .post('/api/v1/sync/push')
        .set('Authorization', `Bearer ${bataydarToken}`)
        .send(syncPayload);

      expect(res.status).toBe(200);
      expect(res.body.data.syncedRecordIds).toContain(offlineRecordId);
      expect(res.body.data.syncedEvidenceIds).toContain(offlineEvidenceId);

      // Verify persisted in MongoDB
      const recordInDb = await CropRecord.findOne({ recordId: offlineRecordId });
      expect(recordInDb).not.toBeNull();
      expect(recordInDb.landlordConfirmationStatus).toBe('NOT_REQUESTED');
      expect(recordInDb.cropType).toBe('Sugarcane (गन्ना)');

      const evidenceInDb = await Evidence.findOne({ evidenceId: offlineEvidenceId });
      expect(evidenceInDb).not.toBeNull();
      expect(evidenceInDb.latitude).toBe(25.772);
    });

    it('should pull records and evidence regardless of landlord confirmation status', async () => {
      const res = await request(app)
        .get('/api/v1/sync/pull')
        .set('Authorization', `Bearer ${bataydarToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(Array.isArray(res.body.data.cropRecords)).toBe(true);
    });
  });

  describe('4. Optional Landlord Confirmation Request Workflow', () => {
    it('should link only to a registered landlord and create a REQUESTED confirmation record', async () => {
      const recordId = crypto.randomUUID();
      await request(app)
        .post('/api/v1/crop-records')
        .set('Authorization', `Bearer ${bataydarToken}`)
        .send({
          recordId,
          fieldId,
          cropType: 'Potato (आलू)',
          cultivationStartDate: '2026-10-20'
        });

      // Request confirmation for the registered landlord
      const res = await request(app)
        .post(`/api/v1/crop-records/${recordId}/confirmation-requests`)
        .set('Authorization', `Bearer ${bataydarToken}`)
        .send({
          landlordName: landlord.fullName,
          landlordPhone: landlord.phone,
          notes: 'Please acknowledge the potato sowing on North plot',
          verificationMethod: 'DIRECT_VERBAL'
        });

      expect(res.status).toBe(201);
      expect(res.body.success).toBe(true);
      expect(res.body.data.confirmation.status).toBe('REQUESTED');
      expect(res.body.data.confirmation.landlordPhone).toBe(landlord.phone);

      // Check crop record was updated
      const updatedCrop = await CropRecord.findOne({ recordId });
      expect(updatedCrop.landlordConfirmationStatus).toBe('REQUESTED');

      // Landlord reviews request in landlord portal
      const portalRes = await request(app)
        .get('/api/v1/landlord/requests')
        .set('Authorization', `Bearer ${landlordToken}`);

      expect(portalRes.status).toBe(200);
      expect(portalRes.body.data.requests.length).toBeGreaterThan(0);

      const reqItem = portalRes.body.data.requests.find(r => r.recordId === recordId);
      expect(reqItem).toBeDefined();

      // Landlord responds with CONFIRMED_VERBAL
      const respondRes = await request(app)
        .post(`/api/v1/landlord/requests/${reqItem.confirmationId}/respond`)
        .set('Authorization', `Bearer ${landlordToken}`)
        .send({
          status: 'CONFIRMED_VERBAL',
          notes: 'Confirmed in presence of village mukhiya.'
        });

      expect(respondRes.status).toBe(200);
      expect(respondRes.body.data.confirmation.status).toBe('CONFIRMED_VERBAL');

      // Verify crop record updated to CONFIRMED_VERBAL
      const confirmedCrop = await CropRecord.findOne({ recordId });
      expect(confirmedCrop.landlordConfirmationStatus).toBe('CONFIRMED_VERBAL');
    });

    it('should return 404 LANDLORD_NOT_REGISTERED when landlord phone does not match any registered landlord', async () => {
      const recordId = crypto.randomUUID();
      await request(app)
        .post('/api/v1/crop-records')
        .set('Authorization', `Bearer ${bataydarToken}`)
        .send({
          recordId,
          fieldId,
          cropType: 'Pulses / Dal (दालें)',
          cultivationStartDate: '2026-09-01'
        });

      const res = await request(app)
        .post(`/api/v1/crop-records/${recordId}/confirmation-requests`)
        .set('Authorization', `Bearer ${bataydarToken}`)
        .send({
          landlordName: 'Unregistered Landowner',
          landlordPhone: '+919999000099',
          notes: 'Please confirm'
        });

      expect(res.status).toBe(404);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('LANDLORD_NOT_REGISTERED');
      expect(res.body.error.message).toContain('No registered landlord account found');
      expect(res.body.error.message).toContain('You can still save and export your cultivation evidence without a landlord.');

      // Verify crop record is STILL VALID and active in MongoDB
      const cropRecord = await CropRecord.findOne({ recordId });
      expect(cropRecord).not.toBeNull();
      expect(cropRecord.status).toBe('ACTIVE');
      expect(cropRecord.cropType).toBe('Pulses / Dal (दालें)');
    });
  });

  describe('5. GPS Accuracy, Freshness, and Multi-Tenant Security Retention', () => {
    it('should flag evidence if GPS accuracy exceeds 20m threshold', async () => {
      const recordId = crypto.randomUUID();
      await request(app)
        .post('/api/v1/crop-records')
        .set('Authorization', `Bearer ${bataydarToken}`)
        .send({
          recordId,
          fieldId,
          cropType: 'Paddy',
          cultivationStartDate: '2026-06-01'
        });

      const urlRes = await request(app)
        .post('/api/v1/evidence/upload-url')
        .set('Authorization', `Bearer ${bataydarToken}`)
        .send({
          recordId,
          latitude: 25.7711,
          longitude: 84.3512,
          reportedGpsAccuracyMeters: 45.0, // > 20m threshold
          clientCapturedAtUtc: new Date().toISOString()
        });

      expect(urlRes.status).toBe(200);
      expect(urlRes.body.data.gpsAccuracyAcceptable).toBe(false);

      const { evidenceId, objectKey } = urlRes.body.data;
      await storageService.saveLocalFile(objectKey, sampleJpeg);

      const completeRes = await request(app)
        .post(`/api/v1/evidence/${evidenceId}/complete`)
        .set('Authorization', `Bearer ${bataydarToken}`)
        .send({
          clientReportedChecksum: sampleJpegSha256
        });

      expect(completeRes.status).toBe(200);
      expect(completeRes.body.data.evidence.validationStatus).toBe('FLAGGED_INACCURATE');
    });

    it('should prevent cross-tenant landlord response for records not assigned to the landlord', async () => {
      // Register a second landlord
      const otherLandlordRes = await request(app)
        .post('/api/v1/auth/register')
        .send({
          fullName: 'Stranger Landlord',
          phone: '+919800000099',
          password: 'Password123!',
          role: 'LANDLORD',
          village: 'Other Village',
          district: 'Gaya',
          state: 'Bihar'
        });
      const otherLandlordToken = otherLandlordRes.body.data.tokens.accessToken;

      const recordId = crypto.randomUUID();
      await request(app)
        .post('/api/v1/crop-records')
        .set('Authorization', `Bearer ${bataydarToken}`)
        .send({
          recordId,
          fieldId,
          cropType: 'Maize (मक्का)',
          cultivationStartDate: '2026-05-01'
        });

      // Request confirmation for the FIRST landlord
      const confRes = await request(app)
        .post(`/api/v1/crop-records/${recordId}/confirmation-requests`)
        .set('Authorization', `Bearer ${bataydarToken}`)
        .send({
          landlordName: landlord.fullName,
          landlordPhone: landlord.phone
        });

      const confirmationId = confRes.body.data.confirmation.confirmationId;

      // Other landlord attempts to respond to this confirmation request
      const unauthorizedRes = await request(app)
        .post(`/api/v1/landlord/requests/${confirmationId}/respond`)
        .set('Authorization', `Bearer ${otherLandlordToken}`)
        .send({
          status: 'CONFIRMED_VERBAL',
          notes: 'Malicious unauthorized response'
        });

      expect(unauthorizedRes.status).toBe(403);
      expect(unauthorizedRes.body.error.code).toBe('FORBIDDEN_REQUEST');
    });
  });
});
