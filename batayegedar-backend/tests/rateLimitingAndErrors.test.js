const request = require('supertest');
const app = require('../src/app');
require('./setup');

describe('Rate Limiting & Error Handling Suite', () => {
  it('should return structured 404 when endpoint does not exist', async () => {
    const res = await request(app).get('/api/v1/non-existent-route-12345');
    expect(res.status).toBe(404);
    expect(res.body.success).toBe(false);
    expect(res.body.error.code).toBe('ROUTE_NOT_FOUND');
  });

  it('should never expose database internals, stack traces, or secrets on malformed request bodies', async () => {
    const res = await request(app)
      .post('/api/v1/auth/login')
      .set('Content-Type', 'application/json')
      .send('{ "phone": "999", "password": '); // Malformed JSON

    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
    expect(res.body.error).toBeDefined();
    expect(res.body.stack).toBeUndefined();
  });

  it('should enforce rate limits on authentication endpoints when enabled', async () => {
    process.env.TEST_RATE_LIMIT = 'true';

    // Send requests exceeding the auth limit (config limit is 10)
    let rateLimited = false;
    for (let i = 0; i < 15; i++) {
      const res = await request(app)
        .post('/api/v1/auth/login')
        .send({ phone: '+919999999999', password: 'InvalidPassword' });

      if (res.status === 429) {
        rateLimited = true;
        expect(res.body.error.code).toBe('AUTH_RATE_LIMIT_EXCEEDED');
        break;
      }
    }

    delete process.env.TEST_RATE_LIMIT;
    expect(rateLimited).toBe(true);
  });
});
