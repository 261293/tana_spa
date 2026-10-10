# Phone-number verification (SMS one-time codes)

Proves that the person who signed up controls the phone number on the account.
Works for numbers from any country; the SMS vendor is pluggable.

> **Switched off by default.** The whole feature sits behind the `phone_otp_verification` feature
> flag, which ships **off** because the flow is unreliable. With it off, sign-up goes straight to
> onboarding (the account is created, just with `phoneVerified: false`) and both OTP routes answer
> `503 feature_disabled`. Turn it back on — globally, or for one tester — from the `/admin` portal:
> [`feature-flags.md`](feature-flags.md).

## How it works

```
signed-in user                    server                              SMS provider
      │  POST /auth/phone/otp/send   │                                      │
      │ ───────────────────────────► │  checks: account, country policy,    │
      │                              │  cooldown, hourly/daily/IP caps      │
      │                              │ ── start(+84…) ───────────────────►  │ texts the code
      │ ◄─ 202 {resendAfter, masked} │                                      │
      │  POST /auth/phone/otp/verify │                                      │
      │  {code}                      │  spends 1 attempt (atomic)           │
      │ ───────────────────────────► │ ── check(+84…, code) ─────────────►  │
      │ ◄─ 200 user(phoneVerified)   │  marks users.phone_verified_at       │
```

The code is **never** stored in our database. It lives with the provider (or, for the offline dev provider, in memory).

## Which provider covers "every country"?

No SMS vendor can promise literally every country without per-country caveats (carrier registration, blocked routes, price). What I checked:

| Option | Coverage / fit | Notes |
|---|---|---|
| **Twilio Verify** (implemented) | Vendor states "global coverage with support for verifications in **180+ countries and 42 languages**"; channels: SMS, WhatsApp, voice, email, push, TOTP, silent network auth | From $0.05 per *successful* verification plus channel fees. Fraud Guard (SMS-pumping detection) is on by default. Vietnamese (`vi`) is a supported SMS locale. |
| Firebase Phone Number Verification | **Not global**: private preview in a handful of countries (DE, ID, MY, plus FI, FR, PK, ES) | Different from Firebase Auth's SMS sign-in; I did not evaluate that product. |
| Vonage, Infobip, Sinch, AWS, local VN gateways (eSMS, SpeedSMS) | Not evaluated here | Fit behind the same `PhoneVerificationProvider` interface (below). |

The coverage figures are the vendors' own claims; I have **not** sent a live SMS to any country.

### Country-specific caveat that matters for this app: Vietnam

Vietnamese carriers require a **registered brand name (alphanumeric sender ID)** for business SMS, the brand name must appear in the message text, and approval takes from days to several weeks depending on the provider. Do this registration early, or Vietnamese users may receive nothing. Zalo is the dominant messaging app there and is a common alternative channel (not implemented).

## The provider seam

`PhoneVerificationProvider` (`server/.../auth/otp/`) has two methods, `start(phone, locale)` and `check(phone, code)`.

| Implementation | When |
|---|---|
| `TwilioVerifyProvider` | selected when `TWILIO_VERIFY_SERVICE_SID`, `TWILIO_API_KEY_SID`, `TWILIO_API_KEY_SECRET` are all set |
| `DevPhoneVerificationProvider` | otherwise; real random codes written to the **server log**, never sent. **Refused when `APP_ENV` is `production` or `uat`.** |
| `DevPhoneVerificationProvider(acceptAnyCode = true)` | **always** when `APP_ENV` is `dev`, `development`, `sit` or `local`, even if Twilio is configured: any 4-10 digit code is approved, no SMS is sent. |

Verification is enforced only in `uat` and `production`.

Adding another vendor = one new class. Routes, limits and the database do not change.

## Defending against SMS pumping (and cost abuse)

OTP endpoints are a classic fraud target: attackers trigger texts to premium-rate numbers they profit from. Layers, cheapest first:

1. **The destination is never a request parameter.** The code goes to the number already on the (authenticated) account, so an attacker can't aim the endpoint at arbitrary numbers.
2. Per-IP rate limit in front of the routes (10/min), keyed per client.
3. Per-account **60s cooldown**, **5 sends/hour per number**, **10 sends/day per account**, **20 sends/hour per IP** (database-backed, so they hold across multiple server instances).
4. Optional **country allow/block list** (`OTP_ALLOWED_COUNTRIES`, `OTP_BLOCKED_COUNTRIES`). Default: every country allowed. If you only serve Vietnam, set `OTP_ALLOWED_COUNTRIES=VN` and most pumping becomes impossible.
5. **5 guesses per code**, spent atomically *before* each check, so parallel guessing can't exceed the cap (a 6-digit code ⇒ ~1-in-200,000 per code).
6. A failed provider call still counts against the limits, so an outage can't be used to bypass them.
7. The provider's own fraud controls (Twilio Fraud Guard).

Watch `phone_verifications` per `country_code`: many sends with few approvals from one country is the pumping signature.

**Number ownership:** a phone is unique only among *verified* accounts. Registering with someone else's number can't lock them out; whoever proves control first owns it (enforced by a partial unique index, so it holds under concurrency).

## API

All require `Authorization: Bearer <access token>`.

| Request | Success | Errors |
|---|---|---|
| `POST /api/v1/auth/phone/otp/send` (optional `Accept-Language`) | `202` `{resendAfterSeconds, expiresInSeconds, maskedPhone}` | `409 phone_already_verified` · `409 phone_claimed` · `422 phone_country_unsupported` · `429 too_many_requests` (+`Retry-After`, `retryAfterSeconds`) · `503 otp_unavailable` |
| `POST /api/v1/auth/phone/otp/verify` `{ "code": "123456" }` | `200` user with `phoneVerified: true` | `400 otp_invalid` (+`attemptsRemaining`) · `400 otp_expired` · `404 otp_not_requested` · `409 phone_claimed` · `429 otp_too_many_attempts` · `503 otp_unavailable` |

`UserDto.phoneVerified` is also returned by `/register`, `/login` and `/me`.

## Configuration

| Variable | Purpose |
|---|---|
| `TWILIO_VERIFY_SERVICE_SID`, `TWILIO_API_KEY_SID`, `TWILIO_API_KEY_SECRET` | Twilio Verify. Use a scoped API key, not the account auth token. |
| `OTP_ALLOWED_COUNTRIES` / `OTP_BLOCKED_COUNTRIES` | comma-separated ISO codes, e.g. `VN,US` |
| `DEFAULT_PHONE_REGION` | region assumed for numbers typed without `+countrycode` (default `VN`) |
| `TRUST_PROXY=true` | **only** behind a reverse proxy that sets `X-Forwarded-For`; otherwise every client looks like the proxy and per-client limits become global |
| `APP_ENV=production` | refuses to start with the dev provider, in-memory users, or a random JWT secret |

## Production checklist

- [ ] Create a Twilio Verify service; enable Fraud Guard at the level you want; set a spend cap/alert on the account.
- [ ] Register the Vietnamese brand name (and any other markets with sender-ID rules) before launch.
- [ ] Decide `OTP_ALLOWED_COUNTRIES` (narrowest list that matches your customers).
- [ ] Set `TRUST_PROXY=true` if (and only if) behind a trusted proxy.
- [ ] Send one real SMS per target market and confirm delivery. **This has not been done.**
- [ ] Decide the policy for *unverified* phones (see below).

## Screens (client)

| Screen | Where | Notes |
|---|---|---|
| Sign-up | `feature/Auth` (`SignUpScreen`) | From the Figma SIGNUP frame. Name, email, phone, password; Google button is "coming soon". |
| Verify phone | `feature/Auth` (`VerifyPhoneScreen`) | **Not in Figma.** Laid out after a reference OTP screen (icon badge, sent-to pill, 3+3 code cells, expiry note, resend countdown) in this app's colours. A code is requested automatically on open; typing the 6th digit submits; "Do it later" skips. |

Flow: login → *Sign up now* → sign-up → verify phone → welcome → consent → assistant chat → home. Skipping verification still continues to the welcome screen. The two icons on the sign-up screen for *name* and *phone* are stand-ins drawn in the same stroke style, because the Figma export was rate-limited; swap in the real assets.

## Not built yet

- **Enforcement.** Login works with an unverified phone. Requiring verification before booking/checkout is a product decision; the check is `phoneVerified` on the user.
- **Changing a phone number** (must reset `phone_verified_at` and re-verify).
- Voice/WhatsApp fallback channels, silent network authentication, and a rate limit on `/register` itself.
