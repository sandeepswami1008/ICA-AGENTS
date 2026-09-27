<?php
declare(strict_types=1);

/**
 * Username rule (per your spec): lowercase full name, spaces stripped.
 * "Sandeep Kumar Swami" -> "sandeepkumarswami"
 * On collision, append the next free number: sandeepkumarswami2, ...3, etc.
 */
function generate_username_from_name(string $fullName): string {
  $base = strtolower(trim($fullName));
  $base = preg_replace('/[^a-z0-9]/', '', $base) ?? '';
  if ($base === '') $base = 'agent';

  $candidate = $base;
  $suffix = 1;
  $st = db()->prepare('SELECT 1 FROM users WHERE username = ? LIMIT 1');
  while (true) {
    $st->execute([$candidate]);
    if (!$st->fetch()) return $candidate;
    $suffix++;
    $candidate = $base . $suffix;
  }
}

/** Basic Indian mobile number check: 10 digits, optionally with +91. */
function validate_mobile_number(string $mobile): bool {
  $digits = preg_replace('/\D/', '', $mobile) ?? '';
  if (strlen($digits) === 12 && strpos($digits, '91') === 0) $digits = substr($digits, 2);
  return (bool)preg_match('/^[6-9]\d{9}$/', $digits);
}

function normalize_mobile_number(string $mobile): string {
  $digits = preg_replace('/\D/', '', $mobile) ?? '';
  if (strlen($digits) === 12 && strpos($digits, '91') === 0) $digits = substr($digits, 2);
  return $digits;
}

function validate_email_address(string $email): bool {
  return (bool)filter_var(trim($email), FILTER_VALIDATE_EMAIL);
}
