# Phone-number verification (SMS one-time codes)

Proves that the person who signed up controls the phone number on the account. Supabase Auth
sends and checks the code; the SMS goes through the provider configured in the Supabase project
(Twilio Verify is the one planned).

> **Switched off by default.** The whole feature sits behind the `phone_otp_verification` feature
> flag, which ships **off** because the flow is unreliable. With it off, sign-up goes straight to
> onboarding (the account is created, just with `phoneVerified: false`). Turn it back on —
> globally, or for one tester — from the `/admin` portal: [`feature-flags.md`](feature-flags.md).
>
> Supabase Auth cannot read our flag table, so the flag alone does not stop a code being sent: the
> phone provider has to stay **disabled in the project's Auth settings while the flag is off**, and
> turning the feature on means doing both. The app reads Auth's `phone_provider_disabled` as
> `feature_disabled`.

## How it works

```
signed-in user                                   Supabase Auth                         SMS provider
      │  PUT /auth/v1/user {phone}                     │                                     │
      │ ─────────────────────────────────────────────► │  rate limits, phone change started  │
      │                                                │ ── send code ─────────────────────► │ texts the code
      │ ◄─ 200                                         │                                     │
      │  POST /auth/v1/verify                          │                                     │
      │  {type: phone_change, phone, token}            │                                     │
      │ ─────────────────────────────────────────────► │  confirms auth.users.phone          │
      │ ◄─ 200                                         │                                     │
      │                                   trigger on auth.users copies the confirmed phone
      │                                   into users.phone / users.phone_verified_at
```

Before the first call the app re-reads the account (`me()`): an already verified phone is
`phone_already_verified` without contacting Auth, and the number sent is the one already on the
account, normalised to E.164. The code is **never** stored in our database; Auth keeps its own
record.

| Piece | Where |
|---|---|
| The calls and the error mapping | `feature/Auth/.../AuthRemoteApiImpl.kt` (`sendPhoneOtp`, `verifyPhoneOtp`, `appCode`) |
| Normalising a typed number to E.164 | `AuthValidation.normalizePhone` in the app, `private.normalize_phone` in SQL |
| Copying the confirmed phone | `private.on_auth_phone_confirmed` (`20261105004153_identity_on_supabase_auth.sql`) |
| Auth's SMS and rate-limit settings | the project's Auth settings; locally `[auth.sms]` and `[auth.rate_limit]` in `supabase/config.toml` |

The resend countdown (60 s) and the expiry the screen shows (3600 s) are constants in
`AuthRemoteApiImpl`, not values read from Auth; keep them in step with the project's settings.

## Errors the app shows

Auth's own error codes are mapped onto the codes in `AuthErrorCodes`:

| Auth says | The app reads |
|---|---|
| `phone_exists` | `phone_claimed` (the number is verified on another account) |
| `over_sms_send_rate_limit`, `over_request_rate_limit` | `too_many_requests` |
| `sms_send_failed` | `otp_unavailable` |
| `phone_provider_disabled` | `feature_disabled` |

Anything else, including a wrong or expired code, keeps Auth's own code.

## Which provider covers "every country"?

No SMS vendor can promise literally every country without per-country caveats (carrier registration, blocked routes, price). What I checked:

| Option | Coverage / fit | Notes |
|---|---|---|
| **Twilio Verify** (planned) | Vendor states "global coverage with support for verifications in **180+ countries and 42 languages**"; channels: SMS, WhatsApp, voice, email, push, TOTP, silent network auth | From $0.05 per *successful* verification plus channel fees. Fraud Guard (SMS-pumping detection) is on by default. Vietnamese (`vi`) is a supported SMS locale. |
| Firebase Phone Number Verification | **Not global**: private preview in a handful of countries (DE, ID, MY, plus FI, FR, PK, ES) | Different from Firebase Auth's SMS sign-in; I did not evaluate that product. |
| Vonage, Infobip, Sinch, AWS, local VN gateways (eSMS, SpeedSMS) | Not evaluated here | Supabase Auth supports a fixed list of SMS providers (`supabase/config.toml` names Twilio, Twilio Verify, MessageBird, Textlocal and Vonage); a vendor outside it needs an Auth hook. |

The coverage figures are the vendors' own claims; I have **not** sent a live SMS to any country.

### Country-specific caveat that matters for this app: Vietnam

Vietnamese carriers require a **registered brand name (alphanumeric sender ID)** for business SMS, the brand name must appear in the message text, and approval takes from days to several weeks depending on the provider. Do this registration early, or Vietnamese users may receive nothing. Zalo is the dominant messaging app there and is a common alternative channel (not implemented).

## Defending against SMS pumping (and cost abuse)

OTP sending is a classic fraud target: attackers trigger texts to premium-rate numbers they profit from. What is left now that Auth sends the code:

1. **Only a signed-in account can start a phone change**, and the app sends the number already on the account.
2. **Auth's rate limits**: SMS sent per hour and verifications per five minutes per IP (`sms_sent`, `token_verifications`; locally 30 each in `supabase/config.toml`, remotely in the project's Auth settings), and a minimum interval between two codes (`max_frequency`).
3. The provider's own fraud controls (Twilio Fraud Guard).

**Lost in the move from the Ktor server:** the per-country allow/block lists, the per-account and per-number send caps kept in our database, and the `phone_verifications` table that showed sends per country. Without the allow list, limiting SMS to Vietnam has to be done at the provider (Twilio's geo permissions). The phone format check is also weaker than the old libphonenumber validation.

**Number ownership:** a phone is unique only among *verified* accounts. Registering with someone else's number can't lock them out; whoever proves control first owns it (enforced by a partial unique index, so it holds under concurrency).

## Production checklist

- [ ] Create a Twilio Verify service; enable Fraud Guard at the level you want; set a spend cap/alert on the account.
- [ ] Restrict the countries SMS may go to at the provider (the app no longer can).
- [ ] Register the Vietnamese brand name (and any other markets with sender-ID rules) before launch.
- [ ] Enter the provider in the project's Auth settings and enable the phone provider — only together with turning the flag on.
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
- **Changing a phone number** from the app (must reset `phone_verified_at` and re-verify).
- Voice/WhatsApp fallback channels and silent network authentication.
