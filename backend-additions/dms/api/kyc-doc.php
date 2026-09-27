<?php
declare(strict_types=1);

/**
 * Serves ONE KYC document by its database id, after checking that the
 * caller is either the document's owner or an admin. Never returns a
 * directory listing, never accepts a raw path, and logs every access.
 *
 * Usage: GET /api/kyc-doc.php?id=123
 * Auth:  Authorization: Bearer <token>  OR an existing admin web session.
 */

require __DIR__ . '/bootstrap.php';
require __DIR__ . '/../includes/bearer_auth.php';

$u = require_login_or_bearer();

$id = (int)($_GET['id'] ?? 0);
if ($id <= 0) { http_response_code(400); exit('Bad request'); }

$st = db()->prepare('SELECT * FROM agent_kyc_documents WHERE id = ? LIMIT 1');
$st->execute([$id]);
$doc = $st->fetch();
if (!$doc) { http_response_code(404); exit('Not found'); }

$isOwner = strtolower($u['username']) === strtolower($doc['username']);
$isAdmin = strtolower((string)($u['role'] ?? '')) === 'admin';
if (!$isOwner && !$isAdmin) { http_response_code(403); exit('Forbidden'); }

if (!is_file($doc['storage_path'])) { http_response_code(410); exit('File missing'); }

db()->prepare(
  'INSERT INTO agent_kyc_access_log (accessed_by, subject_username, doc_type, action, ip_address)
   VALUES (?, ?, ?, \'view\', ?)'
)->execute([$u['username'], $doc['username'], $doc['doc_type'], $_SERVER['REMOTE_ADDR'] ?? '']);

header('Content-Type: ' . ($doc['mime_type'] ?: 'application/octet-stream'));
header('Content-Disposition: inline; filename="' . preg_replace('/[^A-Za-z0-9._-]/', '_', (string)$doc['original_name']) . '"');
header('X-Content-Type-Options: nosniff');
header('Cache-Control: no-store');
readfile($doc['storage_path']);
