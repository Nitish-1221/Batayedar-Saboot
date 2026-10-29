const express = require('express');
const router = express.Router();
const landlordController = require('../controllers/landlordController');
const { authenticateToken } = require('../middleware/auth');
const { validateRequest } = require('../middleware/validation');
const { respondLandlordConfirmationSchema } = require('../validators/landlordValidator');

router.use(authenticateToken);

router.post(
  '/:confirmationId/respond',
  validateRequest(respondLandlordConfirmationSchema),
  landlordController.respondConfirmation
);

module.exports = router;
