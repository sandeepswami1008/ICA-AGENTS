<?php
declare(strict_types=1);

/**
 * PAN / Aadhaar / bank-account field encryption.
 *
 * Key comes from an environment variable / config value, NEVER hard-coded
 * and NEVER logged. Add to dms/api/config.php (and copy the same value into
 * ems/api/config.php since both must decrypt the same data):
 *
 *   'kyc_encryption_key' => 'base64:xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx',
 *
 * Generate one with:  php -r "echo 'base64:'.base64_encode(random_bytes(32));"
 */

function kyc_encryption_key(): string {
  global $config;
  $raw = (string)($config['kyc_encryption_key'] ?? getenv('KYC_ENCRYPTION_KEY') ?? '');
  if ($raw === '') {
    throw new RuntimeException('kyc_encryption_key is not configured. Set it in api/config.php.');
  }
  if (strpos($raw, 'base64:') === 0) {
    $key = base64_decode(substr($raw, 7), true);
  } else {
    $key = $raw;
  }
  if ($key === false || strlen($key) !== 32) {
    throw new RuntimeException('kyc_encryption_key must decode to exactly 32 bytes (AES-256).');
  }
  return $key;
}

/** Encrypt a plaintext value (PAN, Aadhaar, bank account no.) for column storage. */
function kyc_encrypt(string $plaintext): string {
  $key = kyc_encryption_key();
  $iv = random_bytes(12); // GCM nonce
  $tag = '';
  $cipher = openssl_encrypt($plaintext, 'aes-256-gcm', $key, OPENSSL_RAW_DATA, $iv, $tag, '', 16);
  if ($cipher === false) throw new RuntimeException('Encryption failed');
  // store as: iv(12) + tag(16) + ciphertext, single binary blob
  return $iv . $tag . $cipher;
}

/** Decrypt a value previously produced by kyc_encrypt(). Returns null on failure. */
function kyc_decrypt(?string $blob): ?string {
  if ($blob === null || $blob === '') return null;
  try {
    $key = kyc_encryption_key();
    $iv = substr($blob, 0, 12);
    $tag = substr($blob, 12, 16);
    $cipher = substr($blob, 28);
    $plain = openssl_decrypt($cipher, 'aes-256-gcm', $key, OPENSSL_RAW_DATA, $iv, $tag);
    return $plain === false ? null : $plain;
  } catch (Throwable $e) {
    return null;
  }
}

/** Last 4 characters, for display and for the *_last4 index columns. */
function kyc_last4(string $plaintext): string {
  $clean = preg_replace('/\s+/', '', $plaintext) ?? $plaintext;
  return strtoupper(substr($clean, -4));
}

/** Mask a PAN for display: ABCDE1234F -> ABCXX1XXXF-style partial mask. */
function mask_pan(?string $pan): string {
  if (!$pan || strlen($pan) < 4) return '—';
  $last4 = strtoupper(substr($pan, -4));
  return str_repeat('X', max(0, strlen($pan) - 4)) . $last4;
}

/** Mask an Aadhaar for display: only ever show last 4, e.g. XXXX-XXXX-1234. */
function mask_aadhaar(?string $aadhaar): string {
  if (!$aadhaar) return '—';
  $digits = preg_replace('/\D/', '', $aadhaar) ?? '';
  $last4 = substr($digits, -4);
  return 'XXXX-XXXX-' . $last4;
}

/** Mask a bank account number, e.g. XXXXXXXX4321. */
function mask_account(?string $acct): string {
  if (!$acct) return '—';
  $last4 = substr($acct, -4);
  return str_repeat('X', max(0, strlen($acct) - 4)) . $last4;
}

/**
 * Basic format validation before we ever encrypt/store anything.
 * Rejecting bad input here is cheaper than cleaning it up later.
 */
function validate_pan(string $pan): bool {
  return (bool)preg_match('/^[A-Z]{5}[0-9]{4}[A-Z]$/', strtoupper(trim($pan)));
}
function validate_aadhaar(string $aadhaar): bool {
  $digits = preg_replace('/\D/', '', $aadhaar) ?? '';
  return strlen($digits) === 12;
}
function validate_ifsc(string $ifsc): bool {
  return (bool)preg_match('/^[A-Z]{4}0[A-Z0-9]{6}$/', strtoupper(trim($ifsc)));
}
