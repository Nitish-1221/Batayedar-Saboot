const crypto = require('crypto');
const AuditLog = require('../models/AuditLog');

async function recordAudit({ action, actorId, resourceType, resourceId, details, req }) {
  try {
    const ipAddress = req?.ip || req?.headers?.['x-forwarded-for'] || 'unknown';
    const userAgent = req?.headers?.['user-agent'] || 'unknown';

    // Sanitize details to guarantee sensitive fields are never logged
    const sanitizedDetails = { ...details };
    delete sanitizedDetails.password;
    delete sanitizedDetails.passwordHash;
    delete sanitizedDetails.token;
    delete sanitizedDetails.refreshToken;
    delete sanitizedDetails.accessToken;

    const logEntry = new AuditLog({
      logId: crypto.randomUUID(),
      action,
      actorId: actorId || 'SYSTEM',
      resourceType,
      resourceId: resourceId || 'N/A',
      details: sanitizedDetails,
      ipAddress: String(ipAddress),
      userAgent: String(userAgent),
      timestamp: new Date()
    });

    await logEntry.save();
    return logEntry;
  } catch (err) {
    console.error('AuditLog writing failed:', err.message);
    // Non-blocking: Do not crash caller for audit failure
    return null;
  }
}

module.exports = {
  recordAudit
};
