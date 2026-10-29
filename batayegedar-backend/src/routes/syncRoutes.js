const express = require('express');
const router = express.Router();
const syncController = require('../controllers/syncController');
const { authenticateToken, requireRole } = require('../middleware/auth');
const { validateRequest } = require('../middleware/validation');
const { syncPushSchema, syncPullSchema } = require('../validators/syncValidator');

router.use(authenticateToken);
router.use(requireRole('BATAYDAR'));

router.post('/push', validateRequest(syncPushSchema), syncController.pushSync);
router.get('/pull', validateRequest(syncPullSchema), syncController.pullSync);

module.exports = router;
