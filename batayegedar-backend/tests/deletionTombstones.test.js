const request = require('supertest');
const app = require('../src/app');
const { createTestFirebaseToken } = require('../src/config/firebase');
const Field = require('../src/models/Field');
const CropRecord = require('../src/models/CropRecord');
const Evidence = require('../src/models/Evidence');
const DeletionTombstone = require('../src/models/DeletionTombstone');
const User = require('../src/models/User');
require('./setup');

describe('Permanent Deletion & Server-Side Tombstones Suite', () => {
  const farmerA = {
    uid: 'firebase-uid-farmer-del-01',
    phone: '+919876543201',
    role: 'BATAYDAR',
    email: 'farmer.del1@bataydar.org',
    fullName: 'Ramesh Singh',
    village: 'Pupri',
    district: 'Sitamarhi',
    state: 'Bihar'
  };

  const farmerB = {
    uid: 'firebase-uid-farmer-del-02',
    phone: '+919876543202',
    role: 'BATAYDAR',
    email: 'farmer.del2@bataydar.org',
    fullName: 'Suresh Kumar',
    village: 'Pupri',
    district: 'Sitamarhi',
    state: 'Bihar'
  };

  let tokenA;
  let tokenB;

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

    // Seed Field and CropRecord for Farmer A
    await Field.create({
      fieldId: 'field-del-1',
      farmerId: farmerA.uid,
      firebaseUid: farmerA.uid,
      fieldName: 'North Plot',
      village: 'Pupri',
      district: 'Sitamarhi',
      state: 'Bihar'
    });

    await CropRecord.create({
      recordId: 'rec-del-1',
      fieldId: 'field-del-1',
      farmerId: farmerA.uid,
      firebaseUid: farmerA.uid,
      cropType: 'Wheat',
      cultivationStartDate: new Date('2026-10-15'),
      status: 'ACTIVE'
    });

    // Seed Evidence for Farmer A
    await Evidence.create({
      evidenceId: 'ev-del-101',
      recordId: 'rec-del-1',
      farmerId: farmerA.uid,
      firebaseUid: farmerA.uid,
      s3ObjectKey: `evidence/${farmerA.uid}/rec-del-1/ev-del-101.jpg`,
      s3Bucket: 'bataydar-evidence',
      cloudinaryPublicId: 'bataydar_test/ev-del-101',
      cloudinaryUrl: 'https://res.cloudinary.com/demo/image/upload/v1/bataydar_test/ev-del-101.jpg',
      storageProvider: 'cloudinary',
      latitude: 26.1234,
      longitude: 85.1234,
      reportedGpsAccuracyMeters: 4.5,
      clientCapturedAtUtc: new Date(),
      validationStatus: 'VERIFIED',
      isDeleted: false
    });
  });

  test('1. DELETE /api/v1/evidence/:evidenceId marks deleted and establishes tombstone', async () => {
    const res = await request(app)
      .delete('/api/v1/evidence/ev-del-101')
      .set('Authorization', `Bearer ${tokenA}`);

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);

    // Verify MongoDB Evidence marked isDeleted
    const ev = await Evidence.findOne({ evidenceId: 'ev-del-101' });
    expect(ev.isDeleted).toBe(true);
    expect(ev.deletedAt).toBeDefined();

    // Verify DeletionTombstone exists
    const tombstone = await DeletionTombstone.findOne({ recordUuid: 'ev-del-101' });
    expect(tombstone).toBeDefined();
    expect(tombstone.recordType).toBe('EVIDENCE');
    expect(tombstone.firebaseUid).toBe(farmerA.uid);
  });

  test('2. POST /api/v1/sync/deletions batch processes deletions and records tombstones', async () => {
    // Seed a second evidence
    await Evidence.create({
      evidenceId: 'ev-del-102',
      recordId: 'rec-del-1',
      farmerId: farmerA.uid,
      firebaseUid: farmerA.uid,
      s3ObjectKey: `evidence/${farmerA.uid}/rec-del-1/ev-del-102.jpg`,
      s3Bucket: 'bataydar-evidence',
      cloudinaryPublicId: 'bataydar_test/ev-del-102',
      storageProvider: 'cloudinary',
      latitude: 26.1235,
      longitude: 85.1235,
      reportedGpsAccuracyMeters: 3.5,
      clientCapturedAtUtc: new Date(),
      validationStatus: 'VERIFIED',
      isDeleted: false
    });

    const res = await request(app)
      .post('/api/v1/sync/deletions')
      .set('Authorization', `Bearer ${tokenA}`)
      .send({
        deletedEvidenceIds: ['ev-del-101', 'ev-del-102']
      });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.data.confirmedDeletedEvidenceIds).toEqual(
      expect.arrayContaining(['ev-del-101', 'ev-del-102'])
    );

    const tombstones = await DeletionTombstone.find({ firebaseUid: farmerA.uid });
    expect(tombstones.length).toBe(2);
  });

  test('3. Restore Data excludes permanently deleted records and returns tombstones', async () => {
    // Delete ev-del-101
    await request(app)
      .delete('/api/v1/evidence/ev-del-101')
      .set('Authorization', `Bearer ${tokenA}`);

    // Call restore endpoint
    const restoreRes = await request(app)
      .get('/api/v1/sync/restore')
      .set('Authorization', `Bearer ${tokenA}`);

    expect(restoreRes.status).toBe(200);
    expect(restoreRes.body.success).toBe(true);

    const data = restoreRes.body.data;
    // Deleted evidence must NOT appear in evidenceList
    const returnedEvidenceIds = data.evidenceList.map(e => e.evidenceId);
    expect(returnedEvidenceIds).not.toContain('ev-del-101');

    // Deleted IDs must be reported in deletedEvidenceIds
    expect(data.deletedEvidenceIds).toContain('ev-del-101');
  });

  test('4. pushSync rejects resurrecting deleted evidence that matches a server tombstone', async () => {
    // Delete ev-del-101
    await request(app)
      .delete('/api/v1/evidence/ev-del-101')
      .set('Authorization', `Bearer ${tokenA}`);

    // Older device attempts to push ev-del-101
    const pushRes = await request(app)
      .post('/api/v1/sync/push')
      .set('Authorization', `Bearer ${tokenA}`)
      .send({
        evidenceList: [{
          evidenceId: 'ev-del-101',
          recordId: 'rec-del-1',
          latitude: 26.1234,
          longitude: 85.1234,
          reportedGpsAccuracyMeters: 4.5,
          clientCapturedAtUtc: new Date().toISOString()
        }]
      });

    expect(pushRes.status).toBe(200);
    // Synced evidence IDs must NOT include the tombstoned ID
    expect(pushRes.body.data.syncedEvidenceIds).not.toContain('ev-del-101');

    // Evidence in DB must still be deleted
    const ev = await Evidence.findOne({ evidenceId: 'ev-del-101' });
    expect(ev.isDeleted).toBe(true);
  });

  test('5. uploadDirect rejects upload for evidence matching a deletion tombstone with 410', async () => {
    // Delete ev-del-101
    await request(app)
      .delete('/api/v1/evidence/ev-del-101')
      .set('Authorization', `Bearer ${tokenA}`);

    const uploadRes = await request(app)
      .post('/api/v1/sync/upload')
      .set('Authorization', `Bearer ${tokenA}`)
      .field('recordId', 'rec-del-1')
      .field('evidenceId', 'ev-del-101')
      .field('latitude', '26.1234')
      .field('longitude', '85.1234')
      .field('reportedGpsAccuracyMeters', '4.0')
      .attach('photo', sampleJpeg, 'test.jpg');

    expect(uploadRes.status).toBe(410);
    expect(uploadRes.body.error.code).toBe('EVIDENCE_DELETED');
  });

  test('6. Account Isolation: Farmer B cannot delete Farmer A evidence', async () => {
    const res = await request(app)
      .delete('/api/v1/evidence/ev-del-101')
      .set('Authorization', `Bearer ${tokenB}`);

    expect(res.status).toBe(403);

    // Ev-del-101 must remain intact and active
    const ev = await Evidence.findOne({ evidenceId: 'ev-del-101' });
    expect(ev.isDeleted).toBe(false);
  });
});
