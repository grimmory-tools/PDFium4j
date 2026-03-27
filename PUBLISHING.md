# Publishing to Maven Central

First-time setup and release process for publishing PDFium4j to Maven Central
via the Sonatype Central Portal using the [nmcp](https://github.com/GradleUp/nmcp) plugin.

## One-time setup

### 1. Register on Sonatype Central Portal

Go to [central.sonatype.com](https://central.sonatype.com) and create an account.

### 2. Verify namespace ownership

In the Central Portal dashboard, go to **Namespaces** and claim `org.grimmory`.

For GitHub-based verification, add a temporary empty repo named after the
verification key Sonatype gives you under the `grimmory-tools` GitHub org.
Once verified, you can delete that repo.

### 3. Generate a GPG signing key

```bash
gpg --full-generate-key
# Choose: RSA and RSA, 4096 bits, no expiration (or your preference)
# Use your real name and email
```

Publish the public key to a keyserver so Sonatype can verify signatures:

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys <YOUR_KEY_ID>
```

Export the private key in ASCII-armor format for use in CI:

```bash
gpg --armor --export-secret-keys <YOUR_KEY_ID>
```

Copy the full output (including the `-----BEGIN PGP PRIVATE KEY BLOCK-----`
header and footer) for the `GPG_PRIVATE_KEY` secret.

### 4. Generate Sonatype API token

In the Central Portal, go to your account settings and generate a user token.
This gives you a username/password pair for API access.

### 5. Add GitHub repository secrets

Go to **Settings > Secrets and variables > Actions** in the GitHub repo and add:

| Secret | Value |
|---|---|
| `SONATYPE_USERNAME` | Your Sonatype token username |
| `SONATYPE_PASSWORD` | Your Sonatype token password |
| `GPG_PRIVATE_KEY` | Full ASCII-armored private key |
| `GPG_PASSPHRASE` | Passphrase for the GPG key |

## Publishing a release

The publish workflow triggers on version tags matching `v*`.

### 1. Make sure CI is green on main

### 2. Tag and push to upstream

Ensure you have the `upstream` remote configured correctly:

```bash
git remote add upstream https://github.com/grimmory-tools/PDFium4j.git
```

Create the version tag and push it to the main repository:

```bash
git tag v0.1.0
git push upstream v0.1.0
```

> [!IMPORTANT]
> Ensure you push the tag to the **upstream** repository (`grimmory-tools/PDFium4j`), not your fork (`balazs-szucs/PDFium4j`). The publishing secrets must be configured in the main repository.

The workflow will:
1. Extract the version from the tag (`v0.1.0` becomes `0.1.0`)
2. Patch `build.gradle.kts` with that version
3. Build and test
4. Publish both `pdfium4j` and its per-platform native classifier JARs to Maven Central
   via `publishAggregationToCentralPortal` (nmcp bundles and uploads to the Central Portal API)
5. Create a GitHub Release with auto-generated release notes

### 3. Verify on Maven Central

After the workflow completes, check
[central.sonatype.com](https://central.sonatype.com) for your artifacts.
New deployments to the Central Portal typically appear in Maven Central search
within a few minutes.

## Published artifacts

| Artifact | Description |
|---|---|
| `org.grimmory:pdfium4j` | Core API (Java 25 FFM bindings) |
| `org.grimmory:pdfium4j::natives-linux-x64` | Linux x86_64 native binary |
| `org.grimmory:pdfium4j::natives-linux-arm64` | Linux aarch64 native binary |
| `org.grimmory:pdfium4j::natives-darwin-x64` | macOS x86_64 native binary |
| `org.grimmory:pdfium4j::natives-darwin-arm64` | macOS aarch64 native binary |
| `org.grimmory:pdfium4j::natives-windows-x64` | Windows x86_64 native binary |

## Local publishing (testing)

To verify the publication locally before pushing a tag:

```bash
./gradlew publishAggregationToMavenLocal
ls ~/.m2/repository/org/grimmory/pdfium4j/
```

## Troubleshooting

**"403 Forbidden" from Sonatype**: Token is wrong or namespace is not verified.
Re-check the secrets and namespace claim in the Central Portal.

**"Signing key not found"**: The `GPG_PRIVATE_KEY` secret must include the full
armor block. Make sure there are no trailing whitespace or truncation issues.

**"Missing POM metadata"**: Maven Central requires `name`, `description`, `url`,
`license`, `developer`, and `scm` in the POM. These are already configured in
`build.gradle.kts`.
