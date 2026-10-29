const { verifyFirebaseIdToken } = require('../config/firebase');
const User = require('../models/User');

async function authenticateToken(req, res, next) {
  try {
    const authHeader = req.headers['authorization'];
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({
        success: false,
        error: {
          code: 'UNAUTHORIZED',
          message: 'Authentication token is missing or malformed'
        }
      });
    }

    const token = authHeader.split(' ')[1];

    let decoded;
    try {
      decoded = await verifyFirebaseIdToken(token);
    } catch (authErr) {
      if (authErr.code === 'auth/id-token-expired' || authErr.name === 'TokenExpiredError') {
        return res.status(401).json({
          success: false,
          error: {
            code: 'TOKEN_EXPIRED',
            message: 'Access token has expired. Please refresh your token.'
          }
        });
      }
      if (authErr.code === 'auth/id-token-revoked') {
        return res.status(401).json({
          success: false,
          error: {
            code: 'TOKEN_REVOKED',
            message: 'Access token has been revoked. Please sign in again.'
          }
        });
      }
      return res.status(401).json({
        success: false,
        error: {
          code: 'INVALID_TOKEN',
          message: 'Access token is invalid or signature check failed.'
        }
      });
    }

    const uid = decoded.uid || decoded.sub;

    // Load user strictly from DB to verify active status and authoritative role
    let user = await User.findOne({
      $or: [
        { userId: uid },
        { firebaseUid: uid },
        ...(decoded.email ? [{ email: decoded.email.toLowerCase() }] : [])
      ]
    });

    if (user && !user.firebaseUid) {
      user.firebaseUid = uid;
      await user.save().catch(() => {});
    }

    if (!user) {
      return res.status(401).json({
        success: false,
        error: {
          code: 'USER_NOT_FOUND',
          message: 'User account associated with token was not found.'
        }
      });
    }

    if (user.accountStatus === 'SUSPENDED' || user.accountStatus === 'DELETED') {
      return res.status(403).json({
        success: false,
        error: {
          code: 'ACCOUNT_INACTIVE',
          message: `Your account is currently ${user.accountStatus.toLowerCase()}. Contact support.`
        }
      });
    }

    // Attach authenticated identity derived strictly from server-verified token and DB
    req.user = user;
    req.farmerId = user.userId;
    req.userRole = user.role || 'BATAYDAR';
    req.firebaseUid = uid;
    next();
  } catch (err) {
    next(err);
  }
}

function requireRole(...allowedRoles) {
  return (req, res, next) => {
    if (!req.user) {
      return res.status(401).json({
        success: false,
        error: {
          code: 'UNAUTHORIZED',
          message: 'Authentication required'
        }
      });
    }

    const currentRole = req.user.role || 'BATAYDAR';
    if (!allowedRoles.includes(currentRole)) {
      return res.status(403).json({
        success: false,
        error: {
          code: 'FORBIDDEN_ROLE',
          message: `Access denied. Requires role: ${allowedRoles.join(' or ')}.`
        }
      });
    }

    next();
  };
}

module.exports = {
  authenticateToken,
  requireRole
};
