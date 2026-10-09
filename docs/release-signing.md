# Release signing (Android and iOS)

Signing is how a store and a device know a build really came from you. It is separate from
[code hardening](code-hardening.md): obfuscation decides how hard the artifact is to read,
signing decides who is allowed to publish an update for it.

**Nothing in this page's material belongs in git.** `.gitignore` covers `*.jks`, `*.keystore`,
`keystore.properties`, `*.p12` and `*.mobileprovision`, and CI's gitleaks job scans every commit.
If a signing key ever does leak, it cannot be rotated the way a password can: for Android it
means a new app listing unless Play App Signing is in use, and for Apple it means revoking the
certificate and re-issuing profiles.

## Android

### One-time: create the upload key

```bash
keytool -genkeypair -v \
  -keystore ~/keys/tana-upload-key.jks \
  -alias upload -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Tana, O=Tana, C=VN"
```

Keep the file **outside the repository**, back it up somewhere you will still have in five years
(a lost upload key means contacting Play support; a lost app signing key without Play App Signing
means a new listing), and note the password in a password manager.

Use **Play App Signing**: Play then holds the app signing key and this key is only the *upload*
key, which Google can reset if you lose it. That one setting removes most of the risk above.

### Wire it into the build

The release build reads four values, from the environment first and from a gitignored
`androidApp/keystore.properties` second (`androidApp/keystore.properties.example` is the
template):

| Environment variable | `keystore.properties` | Meaning |
|---|---|---|
| `ANDROID_KEYSTORE_FILE` | `storeFile` | Path to the `.jks`, relative to the repository root or absolute |
| `ANDROID_KEYSTORE_PASSWORD` | `storePassword` | Keystore password |
| `ANDROID_KEY_ALIAS` | `keyAlias` | Key alias (`upload` above) |
| `ANDROID_KEY_PASSWORD` | `keyPassword` | Key password; defaults to the keystore password |

With all four present:

```bash
./gradlew :androidApp:assembleRelease    # androidApp-release.apk, signed
./gradlew :androidApp:bundleRelease      # androidApp-release.aab, what Play wants
```

With any of them missing the build still succeeds and produces
`androidApp-release-unsigned.apk` — CI has no keys and only needs the APK to check that R8 ran.
`./gradlew :androidApp:verifyReleaseObfuscation` prints which of the two you got.

### Verify

```bash
"$ANDROID_HOME"/build-tools/37.0.0/apksigner verify --verbose --print-certs \
  androidApp/build/outputs/apk/release/androidApp-release.apk
```

Expect `Verifies` and `Verified using v3 scheme: true`, and check the certificate digest matches
the key you meant to use. v1 (JAR) signing is deliberately off and v2 is only added when the
minimum SDK drops below 28 — with `minSdk = 31`, v3 is what every supported device verifies.

### In CI

Store the keystore as a base64 secret (`base64 -w0 tana-upload-key.jks`) and decode it into the
runner, then pass the four variables through `env:`:

```yaml
- name: Decode the upload keystore
  env:
    KEYSTORE_BASE64: ${{ secrets.ANDROID_KEYSTORE_BASE64 }}
  run: echo "$KEYSTORE_BASE64" | base64 -d > "$RUNNER_TEMP/upload.jks"
- name: Build the signed bundle
  env:
    ANDROID_KEYSTORE_FILE: ${{ runner.temp }}/upload.jks
    ANDROID_KEYSTORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD }}
    ANDROID_KEY_ALIAS: ${{ secrets.ANDROID_KEY_ALIAS }}
    ANDROID_KEY_PASSWORD: ${{ secrets.ANDROID_KEY_PASSWORD }}
  run: ./gradlew :androidApp:bundleRelease --console=plain
```

Such a job belongs in a separate, tag-triggered workflow — never in `ci.yml`, which runs on pull
requests from forks and is deliberately secret-free (see [ci.md](ci.md)). Archive
`androidApp/build/outputs/mapping/release/mapping.txt` from that job as a **private** artifact, or
upload it to Play: without it the crash reports for that build cannot be read.

## iOS

Apple signing is Xcode's, not Gradle's. The project uses **automatic signing**, so Xcode requests
the certificates and provisioning profiles it needs from your Apple Developer account.

### One-time

1. Put your team identifier in `iosApp/Configuration/Config.xcconfig` (`TEAM_ID=ABCDE12345`).
   It is also part of `PRODUCT_BUNDLE_IDENTIFIER`, so nothing signs until it is set. The team ID
   is not a secret; the certificates it lets you use are.
2. Sign in to the account in Xcode → Settings → Accounts, once per machine.
3. Set `teamID` in `iosApp/Configuration/ExportOptions.plist` to the same value.

The `Release` configuration signs with an **Apple Distribution** identity (`Debug` keeps
*Apple Development*), which is what App Store Connect and TestFlight require. If you want to run a
*Release* build on a device you own, either archive and use `release-testing` or temporarily
switch that one setting back to *Apple Development* — a development-signed archive cannot be
uploaded.

### Archive and export an IPA

```bash
# The Xcode build calls :composeApp:embedAndSignAppleFrameworkForXcode itself; Release maps to
# the release Kotlin framework, so the IPA gets the optimized, stripped binary.
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Release -destination 'generic/platform=iOS' \
  -archivePath build/iosApp.xcarchive archive

xcodebuild -exportArchive -archivePath build/iosApp.xcarchive \
  -exportOptionsPlist iosApp/Configuration/ExportOptions.plist \
  -exportPath build/ipa
```

Verify what came out:

```bash
unzip -p build/ipa/*.ipa 'Payload/*.app/embedded.mobileprovision' | security cms -D | head -40
codesign -dv --verbose=4 build/ipa/Payload/*.app
```

Keep `build/iosApp.xcarchive`'s dSYMs: the archive is stripped, so without them a crash report
from the App Store is unreadable. `ExportOptions.plist` has `uploadSymbols` on, which hands them
to App Store Connect at upload time.

### In CI

On a macOS runner, do not try to reuse a developer's certificates by hand. Use an **App Store
Connect API key** and let Xcode manage signing:

```yaml
- run: |
    xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
      -configuration Release -destination 'generic/platform=iOS' \
      -archivePath "$RUNNER_TEMP/iosApp.xcarchive" archive \
      -allowProvisioningUpdates \
      -authenticationKeyPath "$RUNNER_TEMP/AuthKey.p8" \
      -authenticationKeyID "$ASC_KEY_ID" \
      -authenticationKeyIssuerID "$ASC_ISSUER_ID"
```

The `.p8` comes from a secret, is written to the runner's temp directory and never to the
workspace. The alternative — importing a `.p12` into a temporary keychain — works too, but it
means storing a distribution certificate in CI secrets, and a leaked certificate is worse than a
leaked API key because an API key can be revoked without invalidating existing builds.

## Checklist before a first public release

- [ ] Play App Signing enabled; upload keystore and its password backed up separately.
- [ ] `TEAM_ID` and `ExportOptions.plist` filled in; archive signed with Apple Distribution.
- [ ] `mapping.txt` archived per release, privately ([code-hardening.md](code-hardening.md)).
- [ ] `Constants.apiBaseUrl` points at the deployed HTTPS backend — release builds block
      cleartext HTTP.
- [ ] The signed build smoke-tested on a real device, not just the debug build.
