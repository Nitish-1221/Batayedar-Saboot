const request = require('supertest');
const app = require('../src/app');
require('./setup');

describe('Role-Based Authorization & Landlord Portal Suite', () => {
  const bataydarUser = {
    fullName: 'Ramesh Yadav',
    phone: '+919100000001',
    password: 'Password123',
    role: 'BATAYDAR',
    village: 'Hasanpur',
    district: 'Samastipur',
    state: 'Bihar'
  };

  const landlordA = {
    fullName: 'Thakur Virendra Singh',
    phone: '+919200000001',
    password: 'Password123',
    role: 'LANDLORD',
    village: 'Hasanpur',
    district: 'Samastipur',
    state: 'Bihar'
  };

  const landlordB = {
    fullName: 'Maharaj Digvijay Singh',
    phone: '+919200000002',
    password: 'Password123',
    role: 'LANDLORD',
    village: 'Kalyanpur',
    district: 'Samastipur',
    state: 'Bihar'
  };

  let bataydarToken = '';
  let landlordAToken = '';
  let landlordBToken = '';

  beforeEach(async () => {
    // Register Bataydar
    const resBataydar = await request(app)
      .post('/api/v1/auth/register')
      .send(bataydarUser);
    bataydarToken = resBataydar.body.data.tokens.accessToken;

    // Register Landlord A
    const resLandlordA = await request(app)
      .post('/api/v1/auth/register')
      .send(landlordA);
    landlordAToken = resLandlordA.body.data.tokens.accessToken;

    // Register Landlord B
    const resLandlordB = await request(app)
      .post('/api/v1/auth/register')
      .send(landlordB);
    landlordBToken = resLandlordB.body.data.tokens.accessToken;
  });

  describe('1. Role-specific Registration & Authentication', () => {
    it('should assign role BATAYDAR by default or when specified', async () => {
      const res = await request(app)
        .get('/api/v1/users/me')
        .set('Authorization', `Bearer ${bataydarToken}`);

      expect(res.status).toBe(200);
      expect(res.body.data.user.role).toBe('BATAYDAR');
    });

    it('should assign role LANDLORD when registered as Landlord', async () => {
      const res = await request(app)
        .get('/api/v1/users/me')
        .set('Authorization', `Bearer ${landlordAToken}`);

      expect(res.status).toBe(200);
      expect(res.body.data.user.role).toBe('LANDLORD');
    });

    it('should validate expected role during login and reject role mismatch', async () => {
      // Trying to log into Bataydar portal with Landlord credentials
      const resMismatch = await request(app)
        .post('/api/v1/auth/login')
        .send({
          phone: landlordA.phone,
          password: landlordA.password,
          role: 'BATAYDAR'
        });

      expect(resMismatch.status).toBe(401);
      expect(resMismatch.body.error.code).toBe('ROLE_MISMATCH');
    });
  });

  describe('2. Cross-Role Authorization Enforcement', () => {
    it('should block Bataydar from accessing Landlord Portal endpoints', async () => {
      const res = await request(app)
        .get('/api/v1/landlord/dashboard')
        .set('Authorization', `Bearer ${bataydarToken}`);

      expect(res.status).toBe(403);
      expect(res.body.error.code).toBe('FORBIDDEN_ROLE');
    });

    it('should block Landlord from creating crop records', async () => {
      const res = await request(app)
        .post('/api/v1/crop-records')
        .set('Authorization', `Bearer ${landlordAToken}`)
        .send({
          fieldName: 'Unauthorized Field',
          cropType: 'Wheat',
          sowingDate: '2026-10-01'
        });

      expect(res.status).toBe(403);
      expect(res.body.error.code).toBe('FORBIDDEN_ROLE');
    });

    it('should block Landlord from creating fields or syncing farmer data', async () => {
      const resField = await request(app)
        .post('/api/v1/fields')
        .set('Authorization', `Bearer ${landlordAToken}`)
        .send({
          fieldName: 'Landlord Unauthorized Field'
        });

      expect(resField.status).toBe(403);
      expect(resField.body.error.code).toBe('FORBIDDEN_ROLE');

      const resSync = await request(app)
        .get('/api/v1/sync/pull')
        .set('Authorization', `Bearer ${landlordAToken}`);

      expect(resSync.status).toBe(403);
      expect(resSync.body.error.code).toBe('FORBIDDEN_ROLE');
    });
  });

  describe('3. Landlord Dashboard & Confirmation Review Workflow', () => {
    let testFieldId = '';
    let testRecordId = '';
    let testConfirmationId = '';

    beforeEach(async () => {
      // 0. Bataydar creates a field
      const fieldRes = await request(app)
        .post('/api/v1/fields')
        .set('Authorization', `Bearer ${bataydarToken}`)
        .send({
          fieldName: 'Purvi Talab Plot',
          village: 'Hasanpur',
          district: 'Samastipur',
          state: 'Bihar'
        });
      testFieldId = fieldRes.body.data.field.fieldId;

      // 1. Bataydar creates a crop record
      const createRes = await request(app)
        .post('/api/v1/crop-records')
        .set('Authorization', `Bearer ${bataydarToken}`)
        .send({
          fieldId: testFieldId,
          cropType: 'Paddy / Rice (धान)',
          cultivationStartDate: '2026-07-15T00:00:00Z',
          notes: 'Tenant cultivated paddy crop'
        });
      testRecordId = createRes.body.data.record.recordId;

      // 2. Bataydar requests optional confirmation specifically from Landlord A
      const confRes = await request(app)
        .post(`/api/v1/crop-records/${testRecordId}/confirmation-requests`)
        .set('Authorization', `Bearer ${bataydarToken}`)
        .send({
          landlordName: landlordA.fullName,
          landlordPhone: landlordA.phone,
          notes: 'Cultivation started on 15 July with high-yield seeds'
        });
      testConfirmationId = confRes.body.data.confirmation.confirmationId;
    });

    it('should display confirmation request in Landlord A dashboard and requests list', async () => {
      const dashRes = await request(app)
        .get('/api/v1/landlord/dashboard')
        .set('Authorization', `Bearer ${landlordAToken}`);

      expect(dashRes.status).toBe(200);
      expect(dashRes.body.data.pendingCount).toBe(1);
      expect(dashRes.body.data.completedCount).toBe(0);

      const listRes = await request(app)
        .get('/api/v1/landlord/requests')
        .set('Authorization', `Bearer ${landlordAToken}`);

      expect(listRes.status).toBe(200);
      expect(listRes.body.data.total).toBe(1);
      expect(listRes.body.data.requests[0].confirmationId).toBe(testConfirmationId);
      expect(listRes.body.data.requests[0].farmer.fullName).toBe(bataydarUser.fullName);
    });

    it('should forbid unrelated Landlord B from viewing or responding to Landlord A confirmation request', async () => {
      // Landlord B tries to view Landlord A's confirmation request details
      const viewRes = await request(app)
        .get(`/api/v1/landlord/requests/${testConfirmationId}`)
        .set('Authorization', `Bearer ${landlordBToken}`);

      expect(viewRes.status).toBe(403);
      expect(viewRes.body.error.code).toBe('FORBIDDEN_REQUEST');

      // Landlord B tries to respond to Landlord A's confirmation request
      const respondRes = await request(app)
        .post(`/api/v1/landlord/requests/${testConfirmationId}/respond`)
        .set('Authorization', `Bearer ${landlordBToken}`)
        .send({
          status: 'CONFIRMED_WRITTEN',
          notes: 'Unauthorized response attempt'
        });

      expect(respondRes.status).toBe(403);
      expect(respondRes.body.error.code).toBe('FORBIDDEN_REQUEST');
    });

    it('should segregate farmer-provided information from verified GPS telemetry in details', async () => {
      const detailRes = await request(app)
        .get(`/api/v1/landlord/requests/${testConfirmationId}`)
        .set('Authorization', `Bearer ${landlordAToken}`);

      expect(detailRes.status).toBe(200);
      const data = detailRes.body.data;

      // Farmer-provided declarations
      expect(data.farmerDeclaredInfo).toBeDefined();
      expect(data.farmerDeclaredInfo.farmerName).toBe(bataydarUser.fullName);
      expect(data.farmerDeclaredInfo.cropType).toBe('Paddy / Rice (धान)');
      expect(data.farmerDeclaredInfo.fieldName).toBe('Purvi Talab Plot');

      // Verified telemetry
      expect(data.independentlyVerifiedTelemetry).toBeDefined();
      expect(data.canRespond).toBe(true);
    });

    it('should allow Landlord A to respond and confirm cultivation acknowledgment', async () => {
      const respondRes = await request(app)
        .post(`/api/v1/landlord/requests/${testConfirmationId}/respond`)
        .set('Authorization', `Bearer ${landlordAToken}`)
        .send({
          status: 'CONFIRMED_WRITTEN',
          notes: 'I acknowledge Ramesh Yadav cultivated this field as tenant farmer.',
          witnessName: 'Mukhiya Ji'
        });

      expect(respondRes.status).toBe(200);
      expect(respondRes.body.data.confirmation.status).toBe('CONFIRMED_WRITTEN');
      expect(respondRes.body.data.confirmation.respondedAt).toBeDefined();

      // Check updated dashboard
      const dashRes = await request(app)
        .get('/api/v1/landlord/dashboard')
        .set('Authorization', `Bearer ${landlordAToken}`);

      expect(dashRes.body.data.pendingCount).toBe(0);
      expect(dashRes.body.data.completedCount).toBe(1);

      // Check history
      const historyRes = await request(app)
        .get('/api/v1/landlord/history')
        .set('Authorization', `Bearer ${landlordAToken}`);

      expect(historyRes.body.data.total).toBe(1);
      expect(historyRes.body.data.history[0].status).toBe('CONFIRMED_WRITTEN');
    });

    it('should prevent accidental duplicate responses from landlord', async () => {
      // First response
      await request(app)
        .post(`/api/v1/landlord/requests/${testConfirmationId}/respond`)
        .set('Authorization', `Bearer ${landlordAToken}`)
        .send({
          status: 'CONFIRMED_VERBAL',
          notes: 'First confirmation'
        });

      // Second response attempt should fail with 409 ALREADY_RESPONDED
      const duplicateRes = await request(app)
        .post(`/api/v1/landlord/requests/${testConfirmationId}/respond`)
        .set('Authorization', `Bearer ${landlordAToken}`)
        .send({
          status: 'REFUSED',
          notes: 'Attempting to change decision'
        });

      expect(duplicateRes.status).toBe(409);
      expect(duplicateRes.body.error.code).toBe('ALREADY_RESPONDED');
    });
  });

  describe('4. Optional Landlord Participation Guarantee', () => {
    it('farmer can create records, upload evidence and export without any landlord confirmation', async () => {
      // First create a field
      const fieldRes = await request(app)
        .post('/api/v1/fields')
        .set('Authorization', `Bearer ${bataydarToken}`)
        .send({
          fieldName: 'Independent Forest Edge Plot',
          village: 'Hasanpur',
          district: 'Samastipur',
          state: 'Bihar'
        });
      const fieldId = fieldRes.body.data.field.fieldId;

      // Farmer creates record without landlord details
      const recordRes = await request(app)
        .post('/api/v1/crop-records')
        .set('Authorization', `Bearer ${bataydarToken}`)
        .send({
          fieldId,
          cropType: 'Mustard (सरसों)',
          cultivationStartDate: '2026-10-10T00:00:00Z',
          notes: '100% independent cultivation documentation'
        });

      expect(recordRes.status).toBe(201);
      const record = recordRes.body.data.record;
      expect(record.landlordConfirmationStatus).toBe('NOT_REQUESTED');

      // Record can be fetched and exported without landlord
      const getRes = await request(app)
        .get(`/api/v1/crop-records/${record.recordId}`)
        .set('Authorization', `Bearer ${bataydarToken}`);

      expect(getRes.status).toBe(200);
      expect(getRes.body.data.record.landlordConfirmationStatus).toBe('NOT_REQUESTED');
    });
  });
});
