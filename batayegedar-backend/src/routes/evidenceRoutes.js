const express = require('express');
const router = express.Router();
const evidenceController = require('../controllers/evidenceController');
const { authenticateToken, requireRole } = require('../middleware/auth');
const { validateRequest } = require('../middleware/validation');
const {
  requestUploadUrlSchema,
  completeUploadSchema,
  evidenceIdParamSchema
} = require('../validators/evidenceValidator');

// Local binary upload endpoint (for local dev mode)
router.post('/upload-binary', evidenceController.uploadLocalBinary);
router.get('/file/:objectKey', evidenceController.streamEvidenceFile);

// Authenticated evidence endpoints
router.post('/upload-url', authenticateToken, requireRole('BATAYDAR'), validateRequest(requestUploadUrlSchema), evidenceController.requestUploadUrl);
router.post('/:evidenceId/complete', authenticateToken, requireRole('BATAYDAR'), validateRequest(completeUploadSchema), evidenceController.completeUpload);
router.get('/:evidenceId', authenticateToken, validateRequest(evidenceIdParamSchema), evidenceController.getEvidenceById);
router.get('/:evidenceId/download-url', authenticateToken, validateRequest(evidenceIdParamSchema), evidenceController.getDownloadUrl);
router.delete('/:evidenceId', authenticateToken, requireRole('BATAYDAR'), validateRequest(evidenceIdParamSchema), evidenceController.deleteEvidence);

module.exports = router;
