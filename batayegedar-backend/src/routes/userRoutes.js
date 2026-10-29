const express = require('express');
const router = express.Router();
const userController = require('../controllers/userController');
const { authenticateToken } = require('../middleware/auth');
const { validateRequest } = require('../middleware/validation');
const { updateProfileSchema } = require('../validators/authValidator');

router.use(authenticateToken);

router.get('/me', userController.getProfile);
router.patch('/me', validateRequest(updateProfileSchema), userController.updateProfile);
router.delete('/me', userController.requestAccountDeletion);

module.exports = router;
