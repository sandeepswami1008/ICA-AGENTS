# ICA-Agents backend additions — deployment guide

Everything here is **additive**. Nothing in your existing `dms/api/index.php`
or `ems/api/index.php` is rewritten, and the web portals will keep working
exactly as they do today, even if you deploy this and never touch the app.

## What actually changes

| File | Action |
|---|---|
| `dms/sql/2026-09-27-agent-mobile-app.sql` | Run once against the shared DB |
| `dms/includes/crypto.php` | New file — copy in as-is |
| `dms/includes/bearer_auth.php` | New file — copy in as-is |
| `dms/includes/registration_helpers.php` | New file — copy in as-is |
| `dms/api/mobile.php` | New file — this is the entire app-facing API |
| `dms/api/kyc-doc.php` | New file — protected document viewer |
| `ems/includes/bearer_auth_shim.php` | New file — copy in as-is |
| `ems/api/index.php` line 86 | **One line changed**: see below |
| `dms/api/config.php` and `ems/api/config.php` | **Add one config key** (below) |

## 1. Run the SQL migration

Run `dms/sql/2026-09-27-agent-mobile-app.sql` on the single shared database
DMS and EMS both use. It only adds columns/tables (`ADD COLUMN IF NOT
EXISTS`, `CREATE TABLE IF NOT EXISTS`) — safe to run on production. All
existing users get `approval_status = 'approved'` automatically, so nobody
currently active is locked out.

## 2. Add the encryption key to BOTH config.php files

Generate a key once:
```
php -r "echo 'base64:'.base64_encode(random_bytes(32)).PHP_EOL;"
```
Add the output as a new line in **both** `dms/api/config.php` and
`ems/api/config.php` (they must be identical — both portals decrypt the same
columns):
```php
'kyc_encryption_key' => 'base64:PASTE_THE_SAME_VALUE_HERE',
```
Also add a storage path in `dms/api/config.php`, pointing **above**
`public_html` so uploaded PAN/Aadhaar images are never web-accessible:
```php
'kyc_storage_root' => '/home/YOUR_HOSTINGER_USER/kyc-private',
```
Create that directory once via SSH/File Manager with permissions `750`.

## 3. Copy the new files into place

```
dms/includes/crypto.php
dms/includes/bearer_auth.php
dms/includes/registration_helpers.php
dms/api/mobile.php
dms/api/kyc-doc.php
ems/includes/bearer_auth_shim.php
```
(create the `includes/` folders if they don't exist yet — they sit next to
`api/`, not inside it, so they're never web-routable by accident).

## 4. The one EMS code change

`ems/api/index.php` line 86 currently reads:
```php
$u = require_login();
```
Change it to:
```php
require __DIR__ . '/../includes/bearer_auth_shim.php';
$u = require_login_or_bearer();
```
That's it. Every existing EMS route (leads, invoices, agent-invoices,
agent-payments, etc.) keeps working unchanged for the web portal's session
cookie, and now *also* accepts `Authorization: Bearer <token>` from the app.

DMS does **not** need this change — the app talks to DMS only through the
new `dms/api/mobile.php`, which has its own auth built in.

## 5. New endpoints the app calls

Base: `https://dms.icgroup-india.com/api/mobile.php?r=<route>`

| Route | Method | Purpose |
|---|---|---|
| `register` | POST | Self-registration (basic details + PAN/Aadhaar numbers), creates a **pending** agent |
| `kyc/upload` | POST (multipart) | Upload PAN/Aadhaar/photo image tied to a username |
| `auth/login` | POST | username + password → sends email OTP (same flow as web) |
| `auth/verify-otp` | POST | username + otp → returns a 90-day bearer token |
| `auth/logout` | POST | Revokes the current token |
| `admin/agents/pending` | GET | Admin: list agents awaiting approval |
| `admin/agents/approve` | POST | Admin: approve a pending agent |
| `admin/agents/reject` | POST | Admin: reject with a reason |
| `profile/me` | GET | Agent's own profile, PAN/Aadhaar/bank always masked |

EMS routes the app also calls, unchanged except now bearer-token aware:
`leads` (GET, scoped to `agent_user_ref`/assignment automatically by
existing EMS logic), `agent-invoices`, `agent-invoices/{id}/approve`,
`agent-payments`, `agent-revenue-rules`.

## 6. Security notes already built in

- PAN, Aadhaar and bank account numbers are encrypted at rest with
  AES-256-GCM (`crypto.php`) — the database only ever stores ciphertext plus
  a last-4 index for lookups/collision checks.
- Every API response (`profile/me`, admin lists) returns **masked** values
  only (`XXXXXXXX1234`-style). Nothing decrypts to plaintext except inside
  `mobile.php`/`kyc-doc.php` at the moment it's needed, and it's never
  written to any log.
- KYC document images are stored **outside the web root** and served only
  through `kyc-doc.php`, which checks the caller is the document's owner or
  an admin, and logs every view to `agent_kyc_access_log`.
- Bearer tokens are stored as SHA-256 hashes only (`mobile_auth_tokens`) —
  the raw token exists only in the response body once, and on the phone's
  encrypted local storage after that.
- New self-registrations start `approval_status = 'pending'` and cannot log
  in (`auth/login`/`verify-otp` both reject non-approved accounts) until an
  admin approves — per your point 7.
- Username collisions get a numeric suffix (`sandeepkumarswami`,
  `sandeepkumarswami2`, ...) exactly as you specified.
- Invoice approval only flips `agent_approved = 1` in EMS, same as today —
  your team still processes the actual payment manually; nothing here
  changes that.

## 7. What you still need to do on your side

- Confirm SMTP/mail settings used by `send_otp_email()` can handle the
  extra volume once agents self-register from the app.
- Decide who has the `admin` role today (that's who sees the pending-agents
  approval queue) — no new role was introduced.
- Test `mobile.php` and `kyc-doc.php` on a staging copy first, the way you
  would any other production PHP change.
