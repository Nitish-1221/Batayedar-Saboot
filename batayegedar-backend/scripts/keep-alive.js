#!/usr/bin/env node

/**
 * Standalone Keep-Alive Ping Script for Render
 * Pings https://batayedar-saboot.onrender.com/api/v1/health every 10 minutes
 * to prevent the backend container from sleeping after 15 minutes of inactivity.
 */

const https = require('https');
const http = require('http');

const PING_URL = process.env.PING_URL || 'https://batayedar-saboot.onrender.com/api/v1/health';
const INTERVAL_MINUTES = parseInt(process.env.INTERVAL_MINUTES || '10', 10);
const INTERVAL_MS = INTERVAL_MINUTES * 60 * 1000;

console.log('='.repeat(60));
console.log('Batayedar Saboot — Automated Backend Keep-Alive Ping');
console.log(`Target URL:        ${PING_URL}`);
console.log(`Interval:          Every ${INTERVAL_MINUTES} minutes`);
console.log(`Process PID:       ${process.pid}`);
console.log(`Started At:        ${new Date().toISOString()}`);
console.log('='.repeat(60));

let pingCount = 0;

function pingBackend() {
  pingCount++;
  const startTime = Date.now();
  const client = PING_URL.startsWith('https://') ? https : http;

  const req = client.get(PING_URL, { timeout: 30000 }, (res) => {
    let body = '';
    res.on('data', chunk => { body += chunk; });
    res.on('end', () => {
      const durationMs = Date.now() - startTime;
      const timeStr = new Date().toLocaleTimeString();
      if (res.statusCode >= 200 && res.statusCode < 400) {
        console.log(`[${timeStr}] Ping #${pingCount} SUCCESS: HTTP ${res.statusCode} (${durationMs}ms)`);
      } else {
        console.warn(`[${timeStr}] Ping #${pingCount} WARNING: HTTP ${res.statusCode} (${durationMs}ms)`);
      }
    });
  });

  req.on('timeout', () => {
    req.destroy();
    console.error(`[${new Date().toLocaleTimeString()}] Ping #${pingCount} TIMEOUT after 30s`);
  });

  req.on('error', (err) => {
    console.error(`[${new Date().toLocaleTimeString()}] Ping #${pingCount} ERROR: ${err.message}`);
  });
}

// Initial ping immediately
pingBackend();

// Recurring schedule every 10 minutes
setInterval(pingBackend, INTERVAL_MS);

// Handle process termination cleanly
process.on('SIGINT', () => {
  console.log('\nStopping keep-alive ping. Exiting...');
  process.exit(0);
});

