const express = require('express');
const router = express.Router();
const cropRecordController = require('../controllers/cropRecordController');
const evidenceController = require('../controllers/evidenceController');
const landlordController = require('../controllers/landlordController');
const { authenticateToken, requireRole } = require('../middleware/auth');
const { validateRequest } = require('../middleware/validation');
const {
  createCropRecordSchema,
  updateCropRecordSchema,
  recordIdParamSchema
} = require('../validators/cropRecordValidator');
const { requestLandlordConfirmationSchema } = require('../validators/landlordValidator');

router.use(authenticateToken);
router.use(requireRole('BATAYDAR'));

router.post('/', validateRequest(createCropRecordSchema), cropRecordController.createCropRecord);
router.get('/', cropRecordController.getCropRecords);
router.get('/:recordId', validateRequest(recordIdParamSchema), cropRecordController.getCropRecordById);
router.patch('/:recordId', validateRequest(updateCropRecordSchema), cropRecordController.updateCropRecord);
router.delete('/:recordId', validateRequest(recordIdParamSchema), cropRecordController.deleteCropRecord);

// Sub-resources for a crop record
router.get('/:recordId/evidence', validateRequest(recordIdParamSchema), evidenceController.getRecordEvidence);
router.post(
  '/:recordId/confirmation-requests',
  validateRequest(requestLandlordConfirmationSchema),
  landlordController.requestConfirmation
);
router.get('/:recordId/confirmations', validateRequest(recordIdParamSchema), landlordController.getRecordConfirmations);

module.exports = router;
