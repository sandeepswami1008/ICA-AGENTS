<?php
declare(strict_types=1);

/**
 * ICA-Agents mobile API — additive, standalone file.
 * Deployed as dms/api/mobile.php so the existing dms/api/index.php is never
 * touched. Reuses the same DB, session helpers and OTP mail flow as the web
 * portal (see require list below) — nothing about the web login changes.
 *
 * Base URL for the app:  https://dms.icgroup-india.com/api/mobile.php
 * Routing: ?r=<route>  (same convention as the existing DMS API)
 */

error_reporting(E_ALL);
ini_set('display_errors', '0');
set_exception_handler(function ($e) {
  http_response_code(500);
  header('Content-Type: application/json; charset=utf-8');
  echo json_encode(['ok' => false, 'error' => 'Server error: ' . $e->getMessage()]);
  exit;
});

require __DIR__ . '/bootstrap.php';        // db(), json_ok/json_error, session, config
require __DIR__ . '/mail_otp.php';         // generate_otp_code/store_login_otp/verify_login_otp/send_otp_email
require __DIR__ . '/../includes/crypto.php';
require __DIR__ . '/../includes/bearer_auth.php';
require __DIR__ . '/../includes/registration_helpers.php';

$route = strtolower(trim((string)($_GET['r'] ?? ''), '/'));
$method = $_SERVER['REQUEST_METHOD'];

function mobile_body(): array {
  $raw = file_get_contents('php://input');
  $j = $raw ? json_decode($raw, true) : null;
  return is_array($j) ? $j : $_POST;
}

function client_ip(): string {
  return (string)($_SERVER['HTTP_X_FORWARDED_FOR'] ?? $_SERVER['REMOTE_ADDR'] ?? '');
}

// ---------------------------------------------------------------------------
// 1. Self-registration — creates a PENDING agent with basic details only.
//    KYC documents are uploaded separately via kyc/upload (multipart).
//    Per your point 7: pending agents cannot log in until an admin approves.
// ---------------------------------------------------------------------------
if ($route === 'register' && $method === 'POST') {
  $b = mobile_body();
  $name = trim((string)($b['name'] ?? ''));
  $mobile = trim((string)($b['mobile'] ?? ''));
  $email = trim((string)($b['email'] ?? ''));
  $pan = strtoupper(trim((string)($b['pan'] ?? '')));
  $aadhaar = trim((string)($b['aadhaar'] ?? ''));
  $bankAccount = trim((string)($b['bank_account'] ?? ''));
  $bankIfsc = strtoupper(trim((string)($b['bank_ifsc'] ?? '')));
  $bankHolder = trim((string)($b['bank_holder_name'] ?? ''));
  $password = (string)($b['password'] ?? '');

  if ($name === '' || $mobile === '' || $email === '' || $pan === '' || $aadhaar === '' || $password === '') {
    json_error('Name, mobile, email, PAN, Aadhaar and password are required', 422);
  }
  if (!validate_mobile_number($mobile)) json_error('Enter a valid 10-digit mobile number', 422);
  if (!validate_email_address($email)) json_error('Enter a valid email address', 422);
  if (!validate_pan($pan)) json_error('Enter a valid PAN (e.g. ABCDE1234F)', 422);
  if (!validate_aadhaar($aadhaar)) json_error('Enter a valid 12-digit Aadhaar number', 422);
  if ($bankIfsc !== '' && !validate_ifsc($bankIfsc)) json_error('Enter a valid IFSC code', 422);
  if (strlen($password) < 8) json_error('Password must be at least 8 characters', 422);

  // Duplicate contact checks (PAN/Aadhaar compared as ciphertext lookups
  // are not possible directly, so we index on last4 + do a decrypt check
  // only against the small candidate set — cheap and never logs plaintext).
  $panLast4 = kyc_last4($pan);
  $candidates = db()->prepare('SELECT username, pan_enc FROM users WHERE pan_last4 = ?');
  $candidates->execute([$panLast4]);
  foreach ($candidates->fetchAll() as $row) {
    if (kyc_decrypt($row['pan_enc']) === $pan) {
      json_error('An account already exists with this PAN. Contact admin.', 409);
    }
  }

  $username = generate_username_from_name($name);

  $st = db()->prepare(
    'INSERT INTO users
       (username, password_hash, name, role, email, mobile_number,
        pan_enc, pan_last4, aadhaar_enc, aadhaar_last4,
        bank_account_enc, bank_ifsc, bank_account_last4, bank_holder_name,
        registration_source, approval_status, active)
     VALUES (?, ?, ?, \'agent\', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, \'self\', \'pending\', 1)'
  );
  $st->execute([
    $username,
    password_hash($password, PASSWORD_DEFAULT),
    $name,
    $email,
    normalize_mobile_number($mobile),
    kyc_encrypt($pan), $panLast4,
    kyc_encrypt($aadhaar), kyc_last4($aadhaar),
    $bankAccount !== '' ? kyc_encrypt($bankAccount) : null,
    $bankIfsc !== '' ? $bankIfsc : null,
    $bankAccount !== '' ? kyc_last4($bankAccount) : null,
    $bankHolder !== '' ? $bankHolder : null,
  ]);

  json_ok([
    'username' => $username,
    'status' => 'pending',
    'message' => 'Registration received. An admin will review your KYC and approve your account before you can sign in.',
  ], 201);
}

// ---------------------------------------------------------------------------
// 2. KYC document upload (multipart/form-data): fields `username`, `doc_type`,
//    file field `file`. Allowed only while the account is still pending, or
//    by an already-authenticated agent replacing their own document.
// ---------------------------------------------------------------------------
if ($route === 'kyc/upload' && $method === 'POST') {
  $username = strtolower(trim((string)($_POST['username'] ?? '')));
  $docType = strtolower(trim((string)($_POST['doc_type'] ?? '')));
  $allowedTypes = ['pan', 'aadhaar_front', 'aadhaar_back', 'photo'];
  if ($username === '' || !in_array($docType, $allowedTypes, true)) {
    json_error('username and a valid doc_type are required', 422);
  }
  if (empty($_FILES['file']) || $_FILES['file']['error'] !== UPLOAD_ERR_OK) {
    json_error('File upload failed', 422);
  }

  // Authorization: either the account is still pending (first-time KYC
  // during registration) or the caller is the authenticated owner/admin.
  $st = db()->prepare('SELECT approval_status FROM users WHERE username = ? LIMIT 1');
  $st->execute([$username]);
  $row = $st->fetch();
  if (!$row) json_error('Unknown user', 404);

  $caller = current_user_via_bearer();
  $isOwnerOrAdmin = $caller && ($caller['username'] === $username || $caller['role'] === 'admin');
  if ($row['approval_status'] !== 'pending' && !$isOwnerOrAdmin) {
    json_error('Not authorized to upload documents for this account', 403);
  }

  $file = $_FILES['file'];
  $allowedMime = ['image/jpeg', 'image/png', 'application/pdf'];
  $mime = mime_content_type($file['tmp_name']) ?: '';
  if (!in_array($mime, $allowedMime, true)) json_error('Only JPG, PNG or PDF files are allowed', 422);
  if ($file['size'] > 8 * 1024 * 1024) json_error('File too large (max 8MB)', 422);

  // Stored OUTSIDE the public webroot — never a guessable/public URL.
  // Adjust KYC_STORAGE_ROOT in config.php to a directory above public_html.
  global $config;
  $storageRoot = rtrim((string)($config['kyc_storage_root'] ?? (__DIR__ . '/../../kyc-private')), '/');
  if (!is_dir($storageRoot)) mkdir($storageRoot, 0750, true);
  $ext = $mime === 'application/pdf' ? 'pdf' : ($mime === 'image/png' ? 'png' : 'jpg');
  $filename = bin2hex(random_bytes(16)) . '.' . $ext;
  $destDir = $storageRoot . '/' . preg_replace('/[^a-z0-9]/', '', $username);
  if (!is_dir($destDir)) mkdir($destDir, 0750, true);
  $dest = $destDir . '/' . $filename;
  if (!move_uploaded_file($file['tmp_name'], $dest)) json_error('Could not save file', 500);

  db()->prepare(
    'INSERT INTO agent_kyc_documents (username, doc_type, storage_path, original_name, mime_type)
     VALUES (?, ?, ?, ?, ?)'
  )->execute([$username, $docType, $dest, $file['name'], $mime]);

  json_ok(['message' => 'Document uploaded', 'doc_type' => $docType]);
}

// ---------------------------------------------------------------------------
// 3. Mobile login — same username/password + email-OTP flow as the web
//    portal, but the final step returns a bearer token instead of a cookie.
//    Blocks pending/rejected self-registered agents with a clear message.
// ---------------------------------------------------------------------------
if ($route === 'auth/login' && $method === 'POST') {
  $b = mobile_body();
  $username = strtolower(trim((string)($b['username'] ?? '')));
  $password = (string)($b['password'] ?? '');
  if ($username === '' || $password === '') json_error('Username and password required', 422);

  $st = db()->prepare('SELECT * FROM users WHERE LOWER(username) = ? LIMIT 1');
  $st->execute([$username]);
  $row = $st->fetch();
  if (!$row || !password_verify($password, $row['password_hash'])) {
    json_error('Invalid username or password', 401);
  }
  if ((int)$row['active'] !== 1) json_error('Account is disabled. Contact admin.', 403);
  if (($row['approval_status'] ?? 'approved') === 'pending') {
    json_error('Your registration is awaiting admin approval.', 403);
  }
  if (($row['approval_status'] ?? 'approved') === 'rejected') {
    json_error('Your registration was not approved. Contact admin. Reason: ' . (string)($row['rejection_reason'] ?? ''), 403);
  }
  $email = trim((string)($row['email'] ?? ''));
  if ($email === '') json_error('No email on file for OTP. Contact admin.', 400);

  $otp = generate_otp_code();
  store_login_otp($username, $otp);
  $sent = send_otp_email($email, (string)($row['name'] ?? ''), $otp);
  if (!$sent) json_error('Could not send OTP email. Try again shortly.', 503);

  json_ok(['message' => 'OTP sent to your registered email', 'username' => $username]);
}

if ($route === 'auth/verify-otp' && $method === 'POST') {
  $b = mobile_body();
  $username = strtolower(trim((string)($b['username'] ?? '')));
  $otp = preg_replace('/\s+/', '', (string)($b['otp'] ?? ''));
  $deviceLabel = trim((string)($b['device_label'] ?? 'Android'));
  if ($username === '' || $otp === '') json_error('Username and OTP required', 422);
  if (!verify_login_otp($username, $otp)) json_error('Invalid or expired OTP', 401);

  $st = db()->prepare('SELECT username, name, role, email FROM users WHERE LOWER(username) = ? LIMIT 1');
  $st->execute([$username]);
  $row = $st->fetch();
  if (!$row) json_error('User not found', 404);

  $token = issue_mobile_token($row['username'], $deviceLabel);
  json_ok([
    'token' => $token,
    'expires_in_days' => MOBILE_TOKEN_TTL_DAYS,
    'user' => ['username' => $row['username'], 'name' => $row['name'], 'role' => strtolower((string)$row['role'])],
  ]);
}

if ($route === 'auth/logout' && $method === 'POST') {
  $token = bearer_token_from_request();
  if ($token) revoke_mobile_token($token);
  json_ok(['message' => 'Logged out']);
}

// ---------------------------------------------------------------------------
// 4. Admin: list pending agents, approve or reject.
// ---------------------------------------------------------------------------
if ($route === 'admin/agents/pending' && $method === 'GET') {
  $u = require_login_or_bearer();
  if ($u['role'] !== 'admin') json_error('Admin only', 403);
  $rows = db()->query(
    "SELECT username, name, email, mobile_number, pan_last4, aadhaar_last4, created_at
     FROM users WHERE approval_status = 'pending' ORDER BY created_at ASC"
  )->fetchAll();
  json_ok(['agents' => $rows]);
}

if ($route === 'admin/agents/approve' && $method === 'POST') {
  $u = require_login_or_bearer();
  if ($u['role'] !== 'admin') json_error('Admin only', 403);
  $b = mobile_body();
  $username = strtolower(trim((string)($b['username'] ?? '')));
  if ($username === '') json_error('username required', 422);
  db()->prepare(
    "UPDATE users SET approval_status = 'approved', approved_by = ?, approved_at = NOW()
     WHERE username = ? AND approval_status = 'pending'"
  )->execute([$u['username'], $username]);
  db()->prepare('INSERT INTO agent_kyc_access_log (accessed_by, subject_username, action, ip_address) VALUES (?, ?, \'approve\', ?)')
    ->execute([$u['username'], $username, client_ip()]);
  json_ok(['message' => 'Agent approved']);
}

if ($route === 'admin/agents/reject' && $method === 'POST') {
  $u = require_login_or_bearer();
  if ($u['role'] !== 'admin') json_error('Admin only', 403);
  $b = mobile_body();
  $username = strtolower(trim((string)($b['username'] ?? '')));
  $reason = trim((string)($b['reason'] ?? ''));
  if ($username === '') json_error('username required', 422);
  db()->prepare(
    "UPDATE users SET approval_status = 'rejected', approved_by = ?, approved_at = NOW(), rejection_reason = ?
     WHERE username = ? AND approval_status = 'pending'"
  )->execute([$u['username'], $reason, $username]);
  db()->prepare('INSERT INTO agent_kyc_access_log (accessed_by, subject_username, action, ip_address) VALUES (?, ?, \'reject\', ?)')
    ->execute([$u['username'], $username, client_ip()]);
  json_ok(['message' => 'Agent rejected']);
}

// ---------------------------------------------------------------------------
// 5. Agent's own masked profile (never returns raw PAN/Aadhaar/bank number).
// ---------------------------------------------------------------------------
if ($route === 'profile/me' && $method === 'GET') {
  $u = require_login_or_bearer();
  $st = db()->prepare('SELECT * FROM users WHERE username = ? LIMIT 1');
  $st->execute([$u['username']]);
  $row = $st->fetch();
  if (!$row) json_error('User not found', 404);

  db()->prepare('INSERT INTO agent_kyc_access_log (accessed_by, subject_username, action, ip_address) VALUES (?, ?, \'view\', ?)')
    ->execute([$u['username'], $u['username'], client_ip()]);

  json_ok([
    'username' => $row['username'],
    'name' => $row['name'],
    'role' => $row['role'],
    'email' => $row['email'],
    'mobile' => $row['mobile_number'],
    'pan_masked' => mask_pan(kyc_decrypt($row['pan_enc'])),
    'aadhaar_masked' => mask_aadhaar(kyc_decrypt($row['aadhaar_enc'])),
    'bank_account_masked' => mask_account(kyc_decrypt($row['bank_account_enc'])),
    'bank_ifsc' => $row['bank_ifsc'],
    'bank_holder_name' => $row['bank_holder_name'],
    'approval_status' => $row['approval_status'],
  ]);
}

json_error('Unknown mobile API route: ' . $route, 404);
