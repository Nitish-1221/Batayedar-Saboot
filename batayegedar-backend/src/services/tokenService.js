const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const config = require('../config');
const RefreshToken = require('../models/RefreshToken');

function hashToken(rawToken) {
  return crypto.createHash('sha256').update(rawToken).digest('hex');
}

function generateAccessToken(user) {
  const payload = {
    sub: user.userId,
    phone: user.phone,
    name: user.fullName,
    role: user.role || 'BATAYDAR'
  };

  return jwt.sign(payload, config.jwt.accessSecret, {
    expiresIn: config.jwt.accessExpiry,
    issuer: config.jwt.issuer,
    audience: config.jwt.audience
  });
}

async function createRefreshToken(user, familyId, deviceInfo = '', ipAddress = '') {
  const tokenFamily = familyId || crypto.randomUUID();
  const rawRefreshToken = crypto.randomBytes(40).toString('hex');
  const tokenHash = hashToken(rawRefreshToken);

  // 7 days expiration date
  const expiresAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);

  const tokenRecord = new RefreshToken({
    tokenHash,
    userId: user.userId,
    familyId: tokenFamily,
    isRevoked: false,
    expiresAt,
    deviceInfo,
    ipAddress
  });

  await tokenRecord.save();

  return {
    rawRefreshToken,
    familyId: tokenFamily,
    expiresAt
  };
}

async function generateAuthTokens(user, deviceInfo = '', ipAddress = '') {
  const accessToken = generateAccessToken(user);
  const { rawRefreshToken, familyId, expiresAt } = await createRefreshToken(
    user,
    null,
    deviceInfo,
    ipAddress
  );

  return {
    accessToken,
    refreshToken: rawRefreshToken,
    tokenType: 'Bearer',
    expiresIn: config.jwt.accessExpiry,
    refreshTokenExpiresAt: expiresAt
  };
}

async function rotateRefreshToken(rawRefreshToken, user, deviceInfo = '', ipAddress = '') {
  if (!rawRefreshToken) {
    throw new Error('Refresh token is required');
  }

  const tokenHash = hashToken(rawRefreshToken);
  const existingRecord = await RefreshToken.findOne({ tokenHash });

  if (!existingRecord) {
    const error = new Error('Invalid refresh token');
    error.statusCode = 401;
    throw error;
  }

  // Check for Token Theft / Reuse of previously rotated/revoked token
  if (existingRecord.isRevoked) {
    // Revoke all tokens belonging to this family immediately!
    await RefreshToken.updateMany(
      { familyId: existingRecord.familyId },
      { isRevoked: true, revokedReason: 'THEFT_REUSE_DETECTED' }
    );

    const error = new Error('Refresh token reuse detected. Access revoked for security.');
    error.statusCode = 401;
    throw error;
  }

  // Check if expired
  if (new Date() > existingRecord.expiresAt) {
    existingRecord.isRevoked = true;
    existingRecord.revokedReason = 'EXPIRED';
    await existingRecord.save();

    const error = new Error('Refresh token expired. Please log in again.');
    error.statusCode = 401;
    throw error;
  }

  // Revoke current token
  existingRecord.isRevoked = true;
  existingRecord.revokedReason = 'ROTATED';
  await existingRecord.save();

  // Generate new access token and rotated refresh token with the same familyId
  const accessToken = generateAccessToken(user);
  const { rawRefreshToken: newRefreshToken, expiresAt } = await createRefreshToken(
    user,
    existingRecord.familyId,
    deviceInfo,
    ipAddress
  );

  return {
    accessToken,
    refreshToken: newRefreshToken,
    tokenType: 'Bearer',
    expiresIn: config.jwt.accessExpiry,
    refreshTokenExpiresAt: expiresAt
  };
}

async function revokeRefreshToken(rawRefreshToken) {
  if (!rawRefreshToken) return false;
  const tokenHash = hashToken(rawRefreshToken);
  const record = await RefreshToken.findOne({ tokenHash });
  if (record) {
    record.isRevoked = true;
    record.revokedReason = 'LOGOUT';
    await record.save();
    return true;
  }
  return false;
}

async function revokeAllUserTokens(userId, reason = 'USER_REVOKED') {
  await RefreshToken.updateMany(
    { userId, isRevoked: false },
    { isRevoked: true, revokedReason: reason }
  );
}

module.exports = {
  hashToken,
  generateAccessToken,
  generateAuthTokens,
  rotateRefreshToken,
  revokeRefreshToken,
  revokeAllUserTokens
};
