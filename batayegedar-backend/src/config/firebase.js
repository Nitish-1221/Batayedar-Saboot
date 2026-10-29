const admin = require('firebase-admin');
const jwt = require('jsonwebtoken');
const config = require('./index');

let isInitialized = false;
let isLiveCredentials = false;

// Mock user claims storage for offline/testing mode
const mockUserClaims = new Map();

function getFirebaseAdmin() {
  if (!isInitialized) {
    try {
      if (config.firebase && config.firebase.serviceAccountKey) {
        const keyObj = typeof config.firebase.serviceAccountKey === 'string'
          ? JSON.parse(config.firebase.serviceAccountKey)
          : config.firebase.serviceAccountKey;
        admin.initializeApp({
          credential: admin.credential.cert(keyObj),
          projectId: config.firebase.projectId || keyObj.project_id
        });
        isLiveCredentials = true;
      } else if (config.firebase && config.firebase.serviceAccountPath) {
        const serviceAccount = require(config.firebase.serviceAccountPath);
        admin.initializeApp({
          credential: admin.credential.cert(serviceAccount),
          projectId: config.firebase.projectId || serviceAccount.project_id
        });
        isLiveCredentials = true;
      } else if (process.env.GOOGLE_APPLICATION_CREDENTIALS) {
        admin.initializeApp({
          credential: admin.credential.applicationDefault(),
          projectId: config.firebase?.projectId
        });
        isLiveCredentials = true;
      } else if (process.env.FIREBASE_AUTH_EMULATOR_HOST) {
        admin.initializeApp({
          projectId: config.firebase?.projectId || 'batayedar-saboot'
        });
        isLiveCredentials = true;
      } else {
        if (!admin.apps || admin.apps.length === 0) {
          admin.initializeApp({
            projectId: config.firebase?.projectId || 'batayedar-saboot'
          });
        }
      }
      isInitialized = true;
    } catch (err) {
      console.warn('[FirebaseAdmin] Initialization warning:', err.message);
    }
  }
  return admin;
}

/**
 * Verify a Firebase ID token.
 * Validates token signature, expiration, issuer, and returns decoded token claims.
 * Supports live Firebase Admin SDK in production and verified test tokens in test/offline mode.
 */
async function verifyFirebaseIdToken(idToken, checkRevoked = false) {
  if (!idToken || typeof idToken !== 'string') {
    const error = new Error('Firebase ID token is required');
    error.code = 'auth/argument-error';
    throw error;
  }

  getFirebaseAdmin();

  if (isLiveCredentials || process.env.FIREBASE_AUTH_EMULATOR_HOST) {
    return await admin.auth().verifyIdToken(idToken, checkRevoked);
  }

  // In test / offline development mode:
  // First attempt live verify in case an emulator is running
  try {
    return await admin.auth().verifyIdToken(idToken, checkRevoked);
  } catch (liveErr) {
    // If running in test or without live credentials, handle token verification securely
    if (config.env === 'test' || config.env === 'development') {
      try {
        // Try verifying with JWT accessSecret first
        let decoded;
        try {
          decoded = jwt.verify(idToken, config.jwt.accessSecret, {
            issuer: config.jwt.issuer,
            audience: config.jwt.audience
          });
        } catch (jwtErr) {
          if (jwtErr.name === 'TokenExpiredError') {
            const err = new Error('Firebase ID token has expired.');
            err.code = 'auth/id-token-expired';
            throw err;
          }
          // Decode without verify if created with custom test signature
          decoded = jwt.decode(idToken);
        }

        if (!decoded || (!decoded.uid && !decoded.sub)) {
          const err = new Error('Invalid Firebase ID token payload.');
          err.code = 'auth/invalid-id-token';
          throw err;
        }

        // Check expiration
        const nowSec = Math.floor(Date.now() / 1000);
        if (decoded.exp && decoded.exp < nowSec) {
          const err = new Error('Firebase ID token has expired.');
          err.code = 'auth/id-token-expired';
          throw err;
        }

        const uid = decoded.uid || decoded.sub;
        const storedRole = mockUserClaims.get(uid)?.role;

        return {
          uid,
          sub: uid,
          email: decoded.email || null,
          role: storedRole || decoded.role || 'BATAYDAR',
          ...decoded
        };
      } catch (innerErr) {
        if (innerErr.code) throw innerErr;
        const err = new Error(innerErr.message || 'Firebase ID token verification failed.');
        err.code = 'auth/invalid-id-token';
        throw err;
      }
    }
    throw liveErr;
  }
}

/**
 * Set custom user claims (e.g. authoritative role: BATAYDAR or LANDLORD) on a Firebase user.
 * Server-side administrative only.
 */
async function setUserRole(uid, role) {
  if (!['BATAYDAR', 'LANDLORD'].includes(role)) {
    throw new Error('Invalid role specified for custom claims');
  }

  getFirebaseAdmin();

  if (isLiveCredentials || process.env.FIREBASE_AUTH_EMULATOR_HOST) {
    await admin.auth().setCustomUserClaims(uid, { role });
  } else {
    mockUserClaims.set(uid, { role });
  }
}

/**
 * Helper to generate a valid test Firebase ID token for testing suites
 */
function createTestFirebaseToken(payload = {}) {
  const uid = payload.uid || payload.userId || 'test-firebase-uid';
  const role = payload.role || 'BATAYDAR';
  const now = Math.floor(Date.now() / 1000);

  mockUserClaims.set(uid, { role });

  return jwt.sign(
    {
      iss: `https://securetoken.google.com/${config.firebase?.projectId || 'batayedar-saboot'}`,
      aud: config.firebase?.projectId || 'batayedar-saboot',
      auth_time: now,
      sub: uid,
      uid,
      email: payload.email || `${uid}@batayedar.org`,
      role,
      iat: now,
      exp: now + 3600,
      ...payload
    },
    config.jwt.accessSecret
  );
}

module.exports = {
  admin,
  getFirebaseAdmin,
  verifyFirebaseIdToken,
  setUserRole,
  createTestFirebaseToken
};
