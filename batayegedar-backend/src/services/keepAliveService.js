const https = require('https');
const http = require('http');

let timer = null;

/**
 * Auto-ping keep-alive service for Render / cloud deployments.
 * Prevents the free tier container from sleeping after 15 minutes of inactivity
 * by issuing an external HTTP GET request every 10 minutes.
 */
function startKeepAlive({
  url = process.env.PING_URL || (process.env.RENDER_EXTERNAL_URL ? `${process.env.RENDER_EXTERNAL_URL.replace(/\/+$/, '')}/api/v1/health` : 'https://batayedar-saboot.onrender.com/api/v1/health'),
  intervalMs = 10 * 60 * 1000 // 10 minutes (600,000 ms)
} = {}) {
  if (timer) {
    clearInterval(timer);
  }

  const ping = () => {
    try {
      const client = url.startsWith('https://') ? https : http;
      const req = client.get(url, { timeout: 15000 }, (res) => {
        let body = '';
        res.on('data', chunk => { body += chunk; });
        res.on('end', () => {
          if (res.statusCode >= 200 && res.statusCode < 400) {
            console.log(`[KeepAlive] Ping successful to ${url} (status: ${res.statusCode}) at ${new Date().toISOString()}`);
          } else {
            console.warn(`[KeepAlive] Ping returned status ${res.statusCode} from ${url}`);
          }
        });
      });

      req.on('timeout', () => {
        req.destroy();
        console.warn(`[KeepAlive] Ping request timed out after 15s to ${url}`);
      });

      req.on('error', (err) => {
        console.warn(`[KeepAlive] Ping request error: ${err.message}`);
      });
    } catch (err) {
      console.warn(`[KeepAlive] Failed executing ping: ${err.message}`);
    }
  };

  // Initial ping after 30 seconds of startup
  setTimeout(ping, 30 * 1000);

  // Periodic ping every 10 minutes
  timer = setInterval(ping, intervalMs);
  console.log(`[KeepAlive] Auto-ping service started. Target: ${url}, Interval: ${intervalMs / 60000} minutes`);
}

function stopKeepAlive() {
  if (timer) {
    clearInterval(timer);
    timer = null;
    console.log('[KeepAlive] Auto-ping service stopped.');
  }
}

module.exports = {
  startKeepAlive,
  stopKeepAlive
};
