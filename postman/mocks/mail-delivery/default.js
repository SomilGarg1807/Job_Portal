// Local stand-in for the three documented mail endpoints. Never sends email.
const http = require('http');
http.createServer((req, res) => {
  const scenario = req.headers['x-mail-case'] || 'success';
  const reply = (status, body) => {
    res.writeHead(status, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(body));
  };
  if (req.method !== 'POST') return reply(501, { error: 'Not modeled' });
  if (req.url === '/token') {
    if (scenario === 'expired' || scenario === 'both-fail')
      return reply(400, { error: 'invalid_grant', error_description: 'private-provider-detail' });
    if (scenario === 'missing-token') return reply(200, {});
    return reply(200, { access_token: 'fake-access-token', expires_in: 3600, token_type: 'Bearer' });
  }
  if (req.url === '/gmail/v1/users/me/messages/send') {
    if (scenario === 'gmail-down') return reply(503, { error: { message: 'private-provider-detail' } });
    if (scenario === 'html-error') {
      res.writeHead(502, { 'Content-Type': 'text/html' });
      return res.end('<html>private-provider-detail</html>');
    }
    if (scenario === 'timeout') return setTimeout(() => reply(200, { id: 'late' }), 12000);
    return reply(200, { id: 'gmail-message', threadId: 'gmail-thread' });
  }
  if (req.url === '/emails') {
    if (scenario === 'both-fail') return reply(403, { name: 'validation_error', message: 'private-provider-detail' });
    return reply(200, { id: 'resend-message' });
  }
  return reply(501, { error: 'Not modeled' });
}).listen(process.env.PORT || 4500);
