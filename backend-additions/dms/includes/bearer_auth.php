<?php
declare(strict_types=1);

/**
 * Long-lived bearer token auth for the mobile app, layered ON TOP of the
 * existing session/OTP login — it does not change how the web portals work.
 *
 * Flow:
 *   1. App calls the existing username/password + OTP endpoints as normal.
 *   2. On the final successful step, call issue_mobile_token($username) and
 *      return the raw token to the app ONCE. Only its SHA-256 hash is stored.
 *   3. Every later app request sends:  Authorization: Bearer <token>
 *      current_user_via_bearer() looks it up, checks expiry/revocation,
 *      and returns the same shape of user array current_user() returns,
 *      so existing route handlers barely need to change (see PATCH-NOTES.md).
 */

const MOBILE_TOKEN_TTL_DAYS = 90;

function issue_mobile_token(string $username, string $deviceLabel = ''): string {
  $token = bin2hex(random_bytes(32)); // 64 hex chars, returned to the app once
  $hash = hash('sha256', $token);
  $expires = (new DateTime('+' . MOBILE_TOKEN_TTL_DAYS . ' days'))->format('Y-m-d H:i:s');
  $st = db()->prepare(
    'INSERT INTO mobile_auth_tokens (username, token_hash, device_label, expires_at) VALUES (?, ?, ?, ?)'
  );
  $st->execute([$username, $hash, $deviceLabel, $expires]);
  return $token;
}

function revoke_mobile_token(string $token): void {
  $hash = hash('sha256', $token);
  $st = db()->prepare('UPDATE mobile_auth_tokens SET revoked = 1 WHERE token_hash = ?');
  $st->execute([$hash]);
}

function revoke_all_mobile_tokens(string $username): void {
  $st = db()->prepare('UPDATE mobile_auth_tokens SET revoked = 1 WHERE username = ?');
  $st->execute([$username]);
}

/** Extracts "Bearer xxx" from the Authorization header, tolerant of proxies. */
function bearer_token_from_request(): ?string {
  $hdr = $_SERVER['HTTP_AUTHORIZATION']
    ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION']
    ?? '';
  if ($hdr === '' && function_exists('apache_request_headers')) {
    foreach (apache_request_headers() as $k => $v) {
      if (strcasecmp($k, 'Authorization') === 0) { $hdr = $v; break; }
    }
  }
  if (preg_match('/Bearer\s+([A-Za-z0-9]+)/i', $hdr, $m)) return $m[1];
  return null;
}

/**
 * Validates the bearer token and returns a user array shaped like the
 * session-based current_user(), or null if absent/invalid/expired/revoked.
 * Also updates last_used_at (cheap, single UPDATE) for session hygiene.
 */
function current_user_via_bearer(): ?array {
  $token = bearer_token_from_request();
  if (!$token) return null;
  $hash = hash('sha256', $token);
  $st = db()->prepare(
    'SELECT t.username, t.expires_at, t.revoked, u.name, u.role, u.email, u.active, u.approval_status
     FROM mobile_auth_tokens t
     JOIN users u ON u.username = t.username
     WHERE t.token_hash = ? LIMIT 1'
  );
  $st->execute([$hash]);
  $row = $st->fetch();
  if (!$row) return null;
  if ((int)$row['revoked'] === 1) return null;
  if (strtotime($row['expires_at']) < time()) return null;
  if ((int)$row['active'] !== 1) return null;
  if (($row['approval_status'] ?? 'approved') !== 'approved') return null;

  db()->prepare('UPDATE mobile_auth_tokens SET last_used_at = NOW() WHERE token_hash = ?')
      ->execute([$hash]);

  $role = strtolower((string)($row['role'] ?? 'agent'));
  return [
    'username' => $row['username'],
    'name' => $row['name'],
    'role' => $role,
    'email' => $row['email'],
    'auth_via' => 'bearer',
  ];
}

/**
 * Drop-in replacement for require_login() that accepts EITHER a web session
 * OR a mobile bearer token — use this in any route the app also needs to
 * hit through the same index.php (see PATCH-NOTES.md for the one-line swap).
 */
function require_login_or_bearer(): array {
  $u = current_user_via_bearer();
  if ($u) return $u;
  if (function_exists('current_user')) {
    $u = current_user();
    if ($u) return $u;
  }
  json_error('Not authenticated', 401);
  exit; // unreachable, keeps static analysis happy
}
