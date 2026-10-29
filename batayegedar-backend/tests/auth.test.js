const request = require('supertest');
const app = require('../src/app');
require('./setup');

describe('Authentication & Authorization Suite', () => {
  const testFarmer = {
    fullName: 'Mohan Singh',
    phone: '+919876543210',
    password: 'SecurePassword123',
    village: 'Kisanpur',
    district: 'Patna',
    state: 'Bihar'
  };

  describe('POST /api/v1/auth/register', () => {
    it('should register a new farmer and return safe user data with JWT tokens', async () => {
      const res = await request(app)
        .post('/api/v1/auth/register')
        .send(testFarmer);

      expect(res.status).toBe(201);
      expect(res.body.success).toBe(true);
      expect(res.body.data.user.phone).toBe(testFarmer.phone);
      expect(res.body.data.user.passwordHash).toBeUndefined();
      expect(res.body.data.tokens.accessToken).toBeDefined();
      expect(res.body.data.tokens.refreshToken).toBeDefined();
    });

    it('should reject registration if phone number is already registered', async () => {
      await request(app).post('/api/v1/auth/register').send(testFarmer);

      const duplicateRes = await request(app)
        .post('/api/v1/auth/register')
        .send(testFarmer);

      expect(duplicateRes.status).toBe(409);
      expect(duplicateRes.body.success).toBe(false);
      expect(duplicateRes.body.error.code).toBe('PHONE_ALREADY_REGISTERED');
    });

    it('should reject registration if password is too short', async () => {
      const res = await request(app)
        .post('/api/v1/auth/register')
        .send({ ...testFarmer, phone: '+919999999999', password: '123' });

      expect(res.status).toBe(400);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('VALIDATION_ERROR');
    });
  });

  describe('POST /api/v1/auth/login', () => {
    beforeEach(async () => {
      await request(app).post('/api/v1/auth/register').send(testFarmer);
    });

    it('should login with valid phone and password', async () => {
      const res = await request(app)
        .post('/api/v1/auth/login')
        .send({
          phone: testFarmer.phone,
          password: testFarmer.password
        });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.tokens.accessToken).toBeDefined();
    });

    it('should reject login with incorrect password', async () => {
      const res = await request(app)
        .post('/api/v1/auth/login')
        .send({
          phone: testFarmer.phone,
          password: 'WrongPassword'
        });

      expect(res.status).toBe(401);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('INVALID_CREDENTIALS');
    });
  });

  describe('Refresh Token Rotation & Revocation', () => {
    let refreshToken;
    let accessToken;

    beforeEach(async () => {
      const regRes = await request(app).post('/api/v1/auth/register').send(testFarmer);
      refreshToken = regRes.body.data.tokens.refreshToken;
      accessToken = regRes.body.data.tokens.accessToken;
    });

    it('should rotate refresh token and issue new tokens', async () => {
      const res = await request(app)
        .post('/api/v1/auth/refresh')
        .send({ refreshToken });

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.tokens.accessToken).toBeDefined();
      expect(res.body.data.tokens.refreshToken).toBeDefined();
      expect(res.body.data.tokens.refreshToken).not.toBe(refreshToken);
    });

    it('should detect reuse of a previously rotated token and revoke the token family', async () => {
      // First rotation
      const rotRes = await request(app)
        .post('/api/v1/auth/refresh')
        .send({ refreshToken });
      expect(rotRes.status).toBe(200);
      const newRefreshToken = rotRes.body.data.tokens.refreshToken;

      // Attacker or rogue client attempts to reuse old rotated refresh token
      const reuseRes = await request(app)
        .post('/api/v1/auth/refresh')
        .send({ refreshToken });

      expect(reuseRes.status).toBe(401);

      // Even the newly rotated token should now be invalidated because the entire family was compromised!
      const compromisedRes = await request(app)
        .post('/api/v1/auth/refresh')
        .send({ refreshToken: newRefreshToken });

      expect(compromisedRes.status).toBe(401);
    });

    it('should revoke token on logout', async () => {
      const logoutRes = await request(app)
        .post('/api/v1/auth/logout')
        .set('Authorization', `Bearer ${accessToken}`)
        .send({ refreshToken });

      expect(logoutRes.status).toBe(200);

      // Attempting to refresh after logout should fail
      const refreshAfterLogout = await request(app)
        .post('/api/v1/auth/refresh')
        .send({ refreshToken });

      expect(refreshAfterLogout.status).toBe(401);
    });
  });

  describe('Authorization on Protected Endpoints', () => {
    it('should reject access to /api/v1/users/me without Authorization header', async () => {
      const res = await request(app).get('/api/v1/users/me');
      expect(res.status).toBe(401);
      expect(res.body.error.code).toBe('UNAUTHORIZED');
    });

    it('should allow access to /api/v1/users/me with valid Bearer token', async () => {
      const reg = await request(app).post('/api/v1/auth/register').send(testFarmer);
      const token = reg.body.data.tokens.accessToken;

      const res = await request(app)
        .get('/api/v1/users/me')
        .set('Authorization', `Bearer ${token}`);

      expect(res.status).toBe(200);
      expect(res.body.data.user.phone).toBe(testFarmer.phone);
    });
  });
});
