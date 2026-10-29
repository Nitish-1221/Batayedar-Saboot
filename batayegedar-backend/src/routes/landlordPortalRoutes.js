const express = require('express');
const router = express.Router();
const landlordPortalController = require('../controllers/landlordPortalController');
const { authenticateToken, requireRole } = require('../middleware/auth');
const { validateRequest } = require('../middleware/validation');
const {
  respondLandlordConfirmationSchema,
  confirmationIdParamSchema
} = require('../validators/landlordValidator');

// Strict role-based authorization: Only authenticated LANDLORD accounts can access this portal
router.use(authenticateToken);
router.use(requireRole('LANDLORD'));

router.get('/dashboard', landlordPortalController.getDashboard);
router.get('/requests', landlordPortalController.getRequests);
router.get(
  '/requests/:confirmationId',
  validateRequest(confirmationIdParamSchema),
  landlordPortalController.getRequestDetails
);
router.post(
  '/requests/:confirmationId/respond',
  validateRequest(respondLandlordConfirmationSchema),
  landlordPortalController.respondToRequest
);
router.get('/history', landlordPortalController.getHistory);

module.exports = router;

