const express = require('express');
const router = express.Router();
const mongoose = require('mongoose');

const authRoutes = require('./authRoutes');
const userRoutes = require('./userRoutes');
const fieldRoutes = require('./fieldRoutes');
const cropRecordRoutes = require('./cropRecordRoutes');
const evidenceRoutes = require('./evidenceRoutes');
const landlordRoutes = require('./landlordRoutes');
const landlordPortalRoutes = require('./landlordPortalRoutes');
const syncRoutes = require('./syncRoutes');

router.use('/auth', authRoutes);
router.use('/users', userRoutes);
router.use('/fields', fieldRoutes);
router.use('/crop-records', cropRecordRoutes);
router.use('/evidence', evidenceRoutes);
router.use('/confirmations', landlordRoutes);
router.use('/landlord', landlordPortalRoutes);
router.use('/sync', syncRoutes);

router.get('/health', (req, res) => {
  const isDbConnected = mongoose.connection.readyState === 1;
  const statusCode = isDbConnected ? 200 : 503;

  res.status(statusCode).json({
    status: isDbConnected ? 'ok' : 'degraded',
    service: 'batayegedar-backend',
    database: {
      status: isDbConnected ? 'CONNECTED' : 'DISCONNECTED',
      name: mongoose.connection.name || 'none'
    },
    version: '1.0.0',
    timestamp: new Date().toISOString()
  });
});

module.exports = router;
