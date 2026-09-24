#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

/**
 * Calculates the next version according to strict Block 5 rules:
 * - Each register X, Y, Z is in range 0-9.
 * - Z increments (0-9). At 9, resets to 0 and increments Y.
 * - When Y reaches 9 and Z is 9, next step increments X (0.9.9 -> 1.0.0).
 * - Limit is 9.9.9. Beyond 9.9.9, adds letter suffixes: 9.9.9a, 9.9.9b ... 9.9.9z.
 */
function calculateNextVersion(currentVersion) {
  const clean = currentVersion.replace(/^v/, '').trim();
  const match = clean.match(/^(\d)\.(\d)\.(\d)([a-z])?$/);

  if (!match) {
    return '0.1.1';
  }

  let x = parseInt(match[1], 10);
  let y = parseInt(match[2], 10);
  let z = parseInt(match[3], 10);
  let suffix = match[4];

  if (x === 9 && y === 9 && z === 9) {
    if (!suffix) {
      return '9.9.9a';
    }
    const nextCharCode = suffix.charCodeAt(0) + 1;
    if (nextCharCode > 'z'.charCodeAt(0)) {
      return '9.9.9z';
    }
    return `9.9.9${String.fromCharCode(nextCharCode)}`;
  }

  if (z < 9) {
    z++;
  } else {
    z = 0;
    if (y < 9) {
      y++;
    } else {
      y = 0;
      if (x < 9) {
        x++;
      }
    }
  }

  return `${x}.${y}.${z}`;
}

function main() {
  const rootDir = path.resolve(__dirname, '..');
  const pkgPath = path.join(rootDir, 'package.json');
  const gradlePath = path.join(rootDir, 'app', 'build.gradle.kts');

  let currentVersion = '0.1.0';

  if (fs.existsSync(pkgPath)) {
    const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf8'));
    if (pkg.version) {
      currentVersion = pkg.version;
    }
  }

  const nextVersion = calculateNextVersion(currentVersion);
  console.log(`Current version: ${currentVersion} -> Next version: ${nextVersion}`);

  // Update package.json
  if (fs.existsSync(pkgPath)) {
    const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf8'));
    pkg.version = nextVersion;
    fs.writeFileSync(pkgPath, JSON.stringify(pkg, null, 2) + '\n');
  }

  // Update app/build.gradle.kts
  if (fs.existsSync(gradlePath)) {
    let gradleContent = fs.readFileSync(gradlePath, 'utf8');
    gradleContent = gradleContent.replace(
      /versionName\s*=\s*"[^"]+"/,
      `versionName = "${nextVersion}"`
    );

    // Also increment versionCode
    gradleContent = gradleContent.replace(
      /versionCode\s*=\s*(\d+)/,
      (match, code) => `versionCode = ${parseInt(code, 10) + 1}`
    );

    fs.writeFileSync(gradlePath, gradleContent);
  }

  // Set GitHub Actions output if running in GHA
  if (process.env.GITHUB_OUTPUT) {
    fs.appendFileSync(process.env.GITHUB_OUTPUT, `new_version=${nextVersion}\n`);
    fs.appendFileSync(process.env.GITHUB_OUTPUT, `tag_name=v${nextVersion}\n`);
  }

  return nextVersion;
}

if (require.main === module) {
  main();
}

module.exports = { calculateNextVersion };
