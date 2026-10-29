const express = require('express');
const router = express.Router();
const authController = require('../controllers/authController');
const { validateRequest } = require('../middleware/validation');
const { authLimiter } = require('../middleware/rateLimiter');
const { authenticateToken } = require('../middleware/auth');
const {
  registerSchema,
  loginSchema,
  refreshSchema,
  changePasswordSchema
} = require('../validators/authValidator');

router.post('/register', authLimiter, validateRequest(registerSchema), authController.register);
router.post('/login', authLimiter, validateRequest(loginSchema), authController.login);
router.get('/me', authenticateToken, authController.getMe);
router.post('/refresh', authLimiter, validateRequest(refreshSchema), authController.refresh);
router.post('/logout', authController.logout);
router.post('/change-password', authenticateToken, validateRequest(changePasswordSchema), authController.changePassword);

module.exports = router;
