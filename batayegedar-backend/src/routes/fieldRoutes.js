const express = require('express');
const router = express.Router();
const fieldController = require('../controllers/fieldController');
const { authenticateToken, requireRole } = require('../middleware/auth');
const { validateRequest } = require('../middleware/validation');
const {
  createFieldSchema,
  updateFieldSchema,
  fieldIdParamSchema
} = require('../validators/fieldValidator');

router.use(authenticateToken);
router.use(requireRole('BATAYDAR'));

router.post('/', validateRequest(createFieldSchema), fieldController.createField);
router.get('/', fieldController.getFields);
router.get('/:fieldId', validateRequest(fieldIdParamSchema), fieldController.getFieldById);
router.patch('/:fieldId', validateRequest(updateFieldSchema), fieldController.updateField);
router.delete('/:fieldId', validateRequest(fieldIdParamSchema), fieldController.deleteField);

module.exports = router;
