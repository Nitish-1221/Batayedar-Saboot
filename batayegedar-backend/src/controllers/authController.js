const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const User = require('../models/User');
const tokenService = require('../services/tokenService');
const { recordAudit } = require('../services/auditService');
const { verifyFirebaseIdToken, setUserRole } = require('../config/firebase');

const SALT_ROUNDS = 12;

async function register(req, res, next) {
  try {
    const { fullName, phone, password, email, role, village, district, state, preferredLanguage, idToken } = req.body;

    const authHeader = req.headers['authorization'];
    const token = idToken || (authHeader && authHeader.startsWith('Bearer ') ? authHeader.split(' ')[1] : null);

    let firebaseUid = null;
    let verifiedEmail = email;

    if (token) {
      try {
        const decoded = await verifyFirebaseIdToken(token);
        firebaseUid = decoded.uid || decoded.sub;
        if (decoded.email) verifiedEmail = decoded.email;
      } catch (tokenErr) {
        if (idToken) {
          return res.status(401).json({
            success: false,
            error: {
              code: 'INVALID_FIREBASE_TOKEN',
              message: 'Firebase ID token verification failed: ' + tokenErr.message
            }
          });
        }
      }
    }

    const existingUser = await User.findOne({
      $or: [
        { phone },
        ...(firebaseUid ? [{ firebaseUid }, { userId: firebaseUid }] : [])
      ]
    });

    if (existingUser) {
      return res.status(409).json({
        success: false,
        error: {
          code: 'PHONE_ALREADY_REGISTERED',
          message: 'An account with this phone number or Firebase UID already exists.'
        }
      });
    }

    let passwordHash = undefined;
    if (password) {
      passwordHash = await bcrypt.hash(password, SALT_ROUNDS);
    }

    const userId = firebaseUid || crypto.randomUUID();
    const userRole = role === 'LANDLORD' ? 'LANDLORD' : 'BATAYDAR';

    const user = new User({
      userId,
      firebaseUid: firebaseUid || undefined,
      fullName,
      phone,
      email: verifiedEmail || undefined,
      passwordHash,
      role: userRole,
      village: village || '',
      district: district || '',
      state: state || '',
      preferredLanguage: preferredLanguage || 'en',
      accountStatus: 'ACTIVE'
    });

    await user.save();

    // Set server-verified custom role claims via Firebase Admin
    if (firebaseUid) {
      try {
        await setUserRole(firebaseUid, userRole);
      } catch (claimErr) {
        console.warn('[Firebase] Could not set custom claims:', claimErr.message);
      }
    }

    if (userRole === 'LANDLORD') {
      const LandlordConfirmation = require('../models/LandlordConfirmation');
      await LandlordConfirmation.updateMany(
        { landlordPhone: phone.trim() },
        { $set: { landlordId: user.userId } }
      );
    }

    const tokens = await tokenService.generateAuthTokens(
      user,
      req.headers['user-agent'] || '',
      req.ip
    );

    if (token) {
      tokens.accessToken = token;
    }

    await recordAudit({
      action: 'USER_REGISTERED',
      actorId: user.userId,
      resourceType: 'USER',
      resourceId: user.userId,
      details: { phone: user.phone, role: user.role, village: user.village },
      req
    });

    res.status(201).json({
      success: true,
      message: `${userRole === 'LANDLORD' ? 'Landlord' : 'Farmer'} account registered successfully`,
      data: {
        user: user.toSafeObject(),
        tokens
      }
    });
  } catch (err) {
    next(err);
  }
}

async function login(req, res, next) {
  try {
    const { phone, email, password, role, idToken } = req.body;

    const authHeader = req.headers['authorization'];
    const token = idToken || (authHeader && authHeader.startsWith('Bearer ') ? authHeader.split(' ')[1] : null);

    let user;

    if (token) {
      let decoded;
      try {
        decoded = await verifyFirebaseIdToken(token);
      } catch (tokenErr) {
        return res.status(401).json({
          success: false,
          error: {
            code: 'INVALID_CREDENTIALS',
            message: 'Firebase token verification failed: ' + tokenErr.message
          }
        });
      }

      const uid = decoded.uid || decoded.sub;
      user = await User.findOne({
        $or: [
          { firebaseUid: uid },
          { userId: uid },
          ...(decoded.email ? [{ email: decoded.email.toLowerCase() }] : [])
        ]
      });

      if (!user) {
        return res.status(404).json({
          success: false,
          error: {
            code: 'USER_NOT_FOUND',
            message: 'User profile not found. Please complete registration.'
          }
        });
      }
    } else {
      const identifier = phone ? phone.trim() : (email ? email.trim() : '');
      user = await User.findOne({
        $or: [
          { phone: identifier },
          { email: identifier.toLowerCase() }
        ]
      });

      if (!user) {
        return res.status(401).json({
          success: false,
          error: {
            code: 'INVALID_CREDENTIALS',
            message: 'Invalid phone number, email, or password.'
          }
        });
      }

      if (!user.passwordHash) {
        return res.status(401).json({
          success: false,
          error: {
            code: 'FIREBASE_AUTH_REQUIRED',
            message: 'This account was created with Firebase. Please sign in using Firebase Authentication.'
          }
        });
      }

      const isMatch = await bcrypt.compare(password, user.passwordHash);
      if (!isMatch) {
        await recordAudit({
          action: 'LOGIN_FAILED',
          actorId: user.userId,
          resourceType: 'AUTH',
          resourceId: user.userId,
          details: { reason: 'Incorrect password' },
          req
        });

        return res.status(401).json({
          success: false,
          error: {
            code: 'INVALID_CREDENTIALS',
            message: 'Invalid phone number or password.'
          }
        });
      }
    }

    if (role && user.role !== role) {
      return res.status(401).json({
        success: false,
        error: {
          code: 'ROLE_MISMATCH',
          message: `Account is registered as ${user.role}. Please use ${user.role === 'LANDLORD' ? 'Landlord' : 'Bataydar'} login.`
        }
      });
    }

    if (user.accountStatus === 'SUSPENDED' || user.accountStatus === 'DELETED') {
      return res.status(403).json({
        success: false,
        error: {
          code: 'ACCOUNT_INACTIVE',
          message: `Your account is ${user.accountStatus.toLowerCase()}. Please contact support.`
        }
      });
    }

    const tokens = await tokenService.generateAuthTokens(
      user,
      req.headers['user-agent'] || '',
      req.ip
    );

    if (token) {
      tokens.accessToken = token;
    }

    await recordAudit({
      action: 'LOGIN_SUCCESS',
      actorId: user.userId,
      resourceType: 'AUTH',
      resourceId: user.userId,
      details: {},
      req
    });

    res.status(200).json({
      success: true,
      message: 'Login successful',
      data: {
        user: user.toSafeObject(),
        tokens
      }
    });
  } catch (err) {
    next(err);
  }
}

async function getMe(req, res, next) {
  try {
    if (!req.user) {
      return res.status(401).json({
        success: false,
        error: {
          code: 'UNAUTHORIZED',
          message: 'Authentication required'
        }
      });
    }

    res.status(200).json({
      success: true,
      data: {
        user: req.user.toSafeObject()
      }
    });
  } catch (err) {
    next(err);
  }
}

async function refresh(req, res, next) {
  try {
    const { refreshToken } = req.body;

    const tokenHash = tokenService.hashToken(refreshToken);
    const RefreshToken = require('../models/RefreshToken');
    const existingToken = await RefreshToken.findOne({ tokenHash });

    if (!existingToken) {
      return res.status(401).json({
        success: false,
        error: {
          code: 'INVALID_REFRESH_TOKEN',
          message: 'Invalid refresh token.'
        }
      });
    }

    const user = await User.findOne({ userId: existingToken.userId });
    if (!user || user.accountStatus !== 'ACTIVE') {
      return res.status(401).json({
        success: false,
        error: {
          code: 'USER_INACTIVE',
          message: 'Farmer account is not active.'
        }
      });
    }

    const newTokens = await tokenService.rotateRefreshToken(
      refreshToken,
      user,
      req.headers['user-agent'] || '',
      req.ip
    );

    await recordAudit({
      action: 'TOKEN_ROTATED',
      actorId: user.userId,
      resourceType: 'AUTH',
      resourceId: user.userId,
      details: {},
      req
    });

    res.status(200).json({
      success: true,
      message: 'Token rotated successfully',
      data: {
        tokens: newTokens
      }
    });
  } catch (err) {
    if (err.statusCode) {
      return res.status(err.statusCode).json({
        success: false,
        error: {
          code: 'TOKEN_REFRESH_FAILED',
          message: err.message
        }
      });
    }
    next(err);
  }
}

async function logout(req, res, next) {
  try {
    const { refreshToken } = req.body;
    if (refreshToken) {
      await tokenService.revokeRefreshToken(refreshToken);
    }

    if (req.user) {
      await recordAudit({
        action: 'LOGOUT',
        actorId: req.user.userId,
        resourceType: 'AUTH',
        resourceId: req.user.userId,
        details: {},
        req
      });
    }

    res.status(200).json({
      success: true,
      message: 'Logged out successfully and token revoked.'
    });
  } catch (err) {
    next(err);
  }
}

async function changePassword(req, res, next) {
  try {
    const { currentPassword, newPassword } = req.body;
    const user = await User.findOne({ userId: req.farmerId });

    if (!user) {
      return res.status(404).json({
        success: false,
        error: { code: 'USER_NOT_FOUND', message: 'User not found' }
      });
    }

    const isMatch = await bcrypt.compare(currentPassword, user.passwordHash);
    if (!isMatch) {
      return res.status(400).json({
        success: false,
        error: {
          code: 'INCORRECT_CURRENT_PASSWORD',
          message: 'Current password does not match.'
        }
      });
    }

    user.passwordHash = await bcrypt.hash(newPassword, SALT_ROUNDS);
    await user.save();

    await tokenService.revokeAllUserTokens(user.userId, 'PASSWORD_CHANGED');

    await recordAudit({
      action: 'PASSWORD_CHANGED',
      actorId: user.userId,
      resourceType: 'AUTH',
      resourceId: user.userId,
      details: {},
      req
    });

    res.status(200).json({
      success: true,
      message: 'Password changed successfully. Please log in again with your new credentials.'
    });
  } catch (err) {
    next(err);
  }
}

module.exports = {
  register,
  login,
  getMe,
  refresh,
  logout,
  changePassword
};
