const express = require('express');
const router = express.Router();
const multer = require('multer');
const syncController = require('../controllers/syncController');
const { authenticateToken, requireRole } = require('../middleware/auth');
const { validateRequest } = require('../middleware/validation');
const { syncPushSchema, syncPullSchema, syncRestoreSchema } = require('../validators/syncValidator');

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 15 * 1024 * 1024 }
});

router.use(authenticateToken);
router.use(requireRole('BATAYDAR'));

// Restore: restore user account data from cloud
router.get('/restore', validateRequest(syncRestoreSchema), syncController.restoreUserData);

// Changes / Pull
router.get('/changes', validateRequest(syncPullSchema), syncController.getChanges);
router.get('/pull', validateRequest(syncPullSchema), syncController.pullSync);

// Farmer only: push and upload
router.post('/push', validateRequest(syncPushSchema), syncController.pushSync);
router.post('/upload', upload.single('photo'), syncController.uploadDirect);

module.exports = router;
