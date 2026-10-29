const User = require('../models/User');
const { recordAudit } = require('../services/auditService');
const tokenService = require('../services/tokenService');

async function getProfile(req, res, next) {
  try {
    const user = await User.findOne({ userId: req.farmerId });
    if (!user) {
      return res.status(404).json({
        success: false,
        error: { code: 'USER_NOT_FOUND', message: 'Farmer profile not found' }
      });
    }

    res.status(200).json({
      success: true,
      data: {
        user: user.toSafeObject()
      }
    });
  } catch (err) {
    next(err);
  }
}

async function updateProfile(req, res, next) {
  try {
    const { fullName, village, district, state, preferredLanguage } = req.body;
    const user = await User.findOne({ userId: req.farmerId });

    if (!user) {
      return res.status(404).json({
        success: false,
        error: { code: 'USER_NOT_FOUND', message: 'Farmer profile not found' }
      });
    }

    if (fullName !== undefined) user.fullName = fullName;
    if (village !== undefined) user.village = village;
    if (district !== undefined) user.district = district;
    if (state !== undefined) user.state = state;
    if (preferredLanguage !== undefined) user.preferredLanguage = preferredLanguage;

    await user.save();

    await recordAudit({
      action: 'PROFILE_UPDATED',
      actorId: user.userId,
      resourceType: 'USER',
      resourceId: user.userId,
      details: { updatedFields: Object.keys(req.body) },
      req
    });

    res.status(200).json({
      success: true,
      message: 'Profile updated successfully',
      data: {
        user: user.toSafeObject()
      }
    });
  } catch (err) {
    next(err);
  }
}

async function requestAccountDeletion(req, res, next) {
  try {
    const user = await User.findOne({ userId: req.farmerId });
    if (!user) {
      return res.status(404).json({
        success: false,
        error: { code: 'USER_NOT_FOUND', message: 'Farmer profile not found' }
      });
    }

    user.accountStatus = 'DELETION_REQUESTED';
    user.deletionRequestedAt = new Date();
    await user.save();

    // Revoke all tokens
    await tokenService.revokeAllUserTokens(user.userId, 'ACCOUNT_DELETION_REQUESTED');

    await recordAudit({
      action: 'ACCOUNT_DELETION_REQUESTED',
      actorId: user.userId,
      resourceType: 'USER',
      resourceId: user.userId,
      details: { notice: 'Subject to statutory evidentiary retention requirements' },
      req
    });

    res.status(200).json({
      success: true,
      message: 'Account deletion requested. Your sessions have been terminated. Data retention policies apply for audit compliance.'
    });
  } catch (err) {
    next(err);
  }
}

module.exports = {
  getProfile,
  updateProfile,
  requestAccountDeletion
};
