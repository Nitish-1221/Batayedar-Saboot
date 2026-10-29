const request = require('supertest');
const app = require('../src/app');
const { createTestFirebaseToken } = require('../src/config/firebase');
const jwt = require('jsonwebtoken');
const config = require('../src/config');
require('./setup');

describe('Firebase Authentication & Token Verification Suite', () => {
  const bataydarUid = 'firebase-uid-farmer-001';
  const landlordUid = 'firebase-uid-landlord-001';

  let bataydarIdToken;
  let landlordIdToken;

  beforeEach(async () => {
    bataydarIdToken = createTestFirebaseToken({
      uid: bataydarUid,
      email: 'farmer.ramesh@batayedar.org',
      role: 'BATAYDAR'
    });

    landlordIdToken = createTestFirebaseToken({
      uid: landlordUid,
      email: 'landlord.singh@batayedar.org',
      role: 'LANDLORD'
    });
  });

  describe('1. Registration with Firebase ID Token', () => {
    it('should register a new Bataydar farmer using verified Firebase ID token', async () => {
      const res = await request(app)
        .post('/api/v1/auth/register')
        .set('Authorization', `Bearer ${bataydarIdToken}`)
        .send({
          fullName: 'Ramesh Yadav',
          phone: '+919810010001',
          role: 'BATAYDAR',
          village: 'Hasanpur',
          district: 'Samastipur',
          state: 'Bihar'
        });

      expect(res.status).toBe(201);
      expect(res.body.success).toBe(true);
      expect(res.body.data.user.userId).toBe(bataydarUid);
      expect(res.body.data.user.role).toBe('BATAYDAR');
      expect(res.body.data.user.email).toBe('farmer.ramesh@batayedar.org');
    });

    it('should register a new Landlord using verified Firebase ID token', async () => {
      const res = await request(app)
        .post('/api/v1/auth/register')
        .set('Authorization', `Bearer ${landlordIdToken}`)
        .send({
          fullName: 'Virendra Singh',
          phone: '+919820020002',
          role: 'LANDLORD',
          village: 'Hasanpur',
          district: 'Samastipur',
          state: 'Bihar'
        });

      expect(res.status).toBe(201);
      expect(res.body.success).toBe(true);
      expect(res.body.data.user.userId).toBe(landlordUid);
      expect(res.body.data.user.role).toBe('LANDLORD');
    });

    it('should reject duplicate registration with same Firebase UID or phone', async () => {
      await request(app)
        .post('/api/v1/auth/register')
        .set('Authorization', `Bearer ${bataydarIdToken}`)
        .send({
          fullName: 'Ramesh Yadav',
          phone: '+919810010001',
          role: 'BATAYDAR'
        });

      const duplicateRes = await request(app)
        .post('/api/v1/auth/register')
        .set('Authorization', `Bearer ${bataydarIdToken}`)
        .send({
          fullName: 'Ramesh Yadav',
          phone: '+919810010001',
          role: 'BATAYDAR'
        });

      expect(duplicateRes.status).toBe(409);
      expect(duplicateRes.body.error.code).toBe('PHONE_ALREADY_REGISTERED');
    });
  });

  describe('2. Login and Authoritative Role Resolution', () => {
    beforeEach(async () => {
      // Register both users
      await request(app)
        .post('/api/v1/auth/register')
        .set('Authorization', `Bearer ${bataydarIdToken}`)
        .send({
          fullName: 'Ramesh Yadav',
          phone: '+919810010001',
          role: 'BATAYDAR'
        });

      await request(app)
        .post('/api/v1/auth/register')
        .set('Authorization', `Bearer ${landlordIdToken}`)
        .send({
          fullName: 'Virendra Singh',
          phone: '+919820020002',
          role: 'LANDLORD'
        });
    });

    it('should return authoritative role and profile for logged-in user via GET /api/v1/auth/me', async () => {
      const res = await request(app)
        .get('/api/v1/auth/me')
        .set('Authorization', `Bearer ${bataydarIdToken}`);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.user.userId).toBe(bataydarUid);
      expect(res.body.data.user.role).toBe('BATAYDAR');
    });

    it('should reject login if user requests BATAYDAR but is registered as LANDLORD', async () => {
      const res = await request(app)
        .post('/api/v1/auth/login')
        .send({
          idToken: landlordIdToken,
          role: 'BATAYDAR'
        });

      expect(res.status).toBe(401);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('ROLE_MISMATCH');
    });

    it('should reject login if user requests LANDLORD but is registered as BATAYDAR', async () => {
      const res = await request(app)
        .post('/api/v1/auth/login')
        .send({
          idToken: bataydarIdToken,
          role: 'LANDLORD'
        });

      expect(res.status).toBe(401);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('ROLE_MISMATCH');
    });
  });

  describe('3. Role Authorization Enforced by Firebase ID Tokens', () => {
    beforeEach(async () => {
      await request(app)
        .post('/api/v1/auth/register')
        .set('Authorization', `Bearer ${bataydarIdToken}`)
        .send({
          fullName: 'Ramesh Yadav',
          phone: '+919810010001',
          role: 'BATAYDAR'
        });

      await request(app)
        .post('/api/v1/auth/register')
        .set('Authorization', `Bearer ${landlordIdToken}`)
        .send({
          fullName: 'Virendra Singh',
          phone: '+919820020002',
          role: 'LANDLORD'
        });
    });

    it('should allow Bataydar to access crop records but forbid Landlord', async () => {
      // Bataydar access
      const bataydarRes = await request(app)
        .get('/api/v1/crop-records')
        .set('Authorization', `Bearer ${bataydarIdToken}`);
      expect(bataydarRes.status).toBe(200);

      // Landlord access to crop records must be rejected (403 FORBIDDEN_ROLE)
      const landlordRes = await request(app)
        .post('/api/v1/crop-records')
        .set('Authorization', `Bearer ${landlordIdToken}`)
        .send({
          cropName: 'Wheat',
          season: 'Rabi 2026'
        });
      expect(landlordRes.status).toBe(403);
      expect(landlordRes.body.error.code).toBe('FORBIDDEN_ROLE');
    });

    it('should allow Landlord to access landlord portal but forbid Bataydar', async () => {
      // Landlord access
      const landlordRes = await request(app)
        .get('/api/v1/landlord/dashboard')
        .set('Authorization', `Bearer ${landlordIdToken}`);
      expect(landlordRes.status).toBe(200);

      // Bataydar access must be forbidden (403 FORBIDDEN_ROLE)
      const bataydarRes = await request(app)
        .get('/api/v1/landlord/dashboard')
        .set('Authorization', `Bearer ${bataydarIdToken}`);
      expect(bataydarRes.status).toBe(403);
      expect(bataydarRes.body.error.code).toBe('FORBIDDEN_ROLE');
    });
  });

  describe('4. Token Rejection & Security Edge Cases', () => {
    it('should reject expired Firebase ID tokens with 401 TOKEN_EXPIRED', async () => {
      const expiredToken = jwt.sign(
        {
          uid: 'expired-user',
          sub: 'expired-user',
          exp: Math.floor(Date.now() / 1000) - 300 // expired 5 mins ago
        },
        config.jwt.accessSecret,
        {
          issuer: config.jwt.issuer,
          audience: config.jwt.audience
        }
      );

      const res = await request(app)
        .get('/api/v1/auth/me')
        .set('Authorization', `Bearer ${expiredToken}`);

      expect(res.status).toBe(401);
      expect(res.body.error.code).toBe('TOKEN_EXPIRED');
    });

    it('should reject malformed or tampered tokens with 401 INVALID_TOKEN', async () => {
      const res = await request(app)
        .get('/api/v1/auth/me')
        .set('Authorization', 'Bearer this.is.an.invalid.token');

      expect(res.status).toBe(401);
      expect(res.body.error.code).toBe('INVALID_TOKEN');
    });

    it('should reject requests without Authorization header with 401 UNAUTHORIZED', async () => {
      const res = await request(app).get('/api/v1/auth/me');

      expect(res.status).toBe(401);
      expect(res.body.error.code).toBe('UNAUTHORIZED');
    });
  });
});
