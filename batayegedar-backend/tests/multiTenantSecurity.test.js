const request = require('supertest');
const app = require('../src/app');
require('./setup');

describe('Multi-Tenant Data Isolation & Security Boundaries', () => {
  let farmerAToken;
  let farmerBToken;
  let farmerAFieldId;
  let farmerARecordId;

  beforeEach(async () => {
    // Register Farmer A
    const resA = await request(app).post('/api/v1/auth/register').send({
      fullName: 'Farmer A (Arjun)',
      phone: '+919111111111',
      password: 'Password123!',
      village: 'Village Alpha',
      district: 'Patna',
      state: 'Bihar'
    });
    farmerAToken = resA.body.data.tokens.accessToken;

    // Register Farmer B
    const resB = await request(app).post('/api/v1/auth/register').send({
      fullName: 'Farmer B (Bhim)',
      phone: '+919222222222',
      password: 'Password123!',
      village: 'Village Beta',
      district: 'Gaya',
      state: 'Bihar'
    });
    farmerBToken = resB.body.data.tokens.accessToken;

    // Farmer A creates a field
    const fieldRes = await request(app)
      .post('/api/v1/fields')
      .set('Authorization', `Bearer ${farmerAToken}`)
      .send({
        fieldName: "Farmer A's Wheat Field",
        village: 'Village Alpha',
        district: 'Patna',
        state: 'Bihar',
        areaAcres: 2.0
      });
    farmerAFieldId = fieldRes.body.data.field.fieldId;

    // Farmer A creates a crop record
    const cropRes = await request(app)
      .post('/api/v1/crop-records')
      .set('Authorization', `Bearer ${farmerAToken}`)
      .send({
        fieldId: farmerAFieldId,
        cropType: 'Wheat',
        cultivationStartDate: '2026-10-01T00:00:00Z',
        notes: 'Private notes of Farmer A'
      });
    farmerARecordId = cropRes.body.data.record.recordId;
  });

  it('Farmer B should NOT be able to view Farmer A field details (403 Forbidden)', async () => {
    const res = await request(app)
      .get(`/api/v1/fields/${farmerAFieldId}`)
      .set('Authorization', `Bearer ${farmerBToken}`);

    expect(res.status).toBe(403);
    expect(res.body.success).toBe(false);
    expect(res.body.error.code).toBe('FORBIDDEN');
  });

  it('Farmer B should NOT be able to update Farmer A field (403 Forbidden)', async () => {
    const res = await request(app)
      .patch(`/api/v1/fields/${farmerAFieldId}`)
      .set('Authorization', `Bearer ${farmerBToken}`)
      .send({ fieldName: 'Hijacked by B' });

    expect(res.status).toBe(403);
    expect(res.body.success).toBe(false);
  });

  it('Farmer B should NOT be able to delete Farmer A field (403 Forbidden)', async () => {
    const res = await request(app)
      .delete(`/api/v1/fields/${farmerAFieldId}`)
      .set('Authorization', `Bearer ${farmerBToken}`);

    expect(res.status).toBe(403);
    expect(res.body.success).toBe(false);
  });

  it('Farmer B should NOT be able to view Farmer A crop record (403 Forbidden)', async () => {
    const res = await request(app)
      .get(`/api/v1/crop-records/${farmerARecordId}`)
      .set('Authorization', `Bearer ${farmerBToken}`);

    expect(res.status).toBe(403);
    expect(res.body.success).toBe(false);
  });

  it('Farmer B should NOT be able to create a crop record on Farmer A field', async () => {
    const res = await request(app)
      .post('/api/v1/crop-records')
      .set('Authorization', `Bearer ${farmerBToken}`)
      .send({
        fieldId: farmerAFieldId, // Field belongs to A!
        cropType: 'Paddy',
        cultivationStartDate: '2026-10-01T00:00:00Z'
      });

    expect(res.status).toBe(403);
    expect(res.body.success).toBe(false);
    expect(res.body.error.code).toBe('FORBIDDEN');
  });

  it('Farmer B should NOT be able to request evidence upload on Farmer A record', async () => {
    const res = await request(app)
      .post('/api/v1/evidence/upload-url')
      .set('Authorization', `Bearer ${farmerBToken}`)
      .send({
        recordId: farmerARecordId,
        latitude: 25.5941,
        longitude: 85.1376,
        reportedGpsAccuracyMeters: 5.0,
        clientCapturedAtUtc: new Date().toISOString()
      });

    expect(res.status).toBe(403);
    expect(res.body.success).toBe(false);
  });
});
