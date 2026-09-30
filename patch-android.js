// Runs on GitHub after `npx cap add android`:
// adds our native plugin, icons, permissions, version and signing.

const fs = require('fs');
const path = require('path');

// patch-android.js is already located in the repository root.
const root = __dirname;
const android = path.join(root, 'android', 'app');

const must = (cond, msg) => {
  if (!cond) {
    console.error('PATCH FAILED: ' + msg);
    process.exit(1);
  }
};

// ============================================================
// 1) NATIVE JAVA CODE
// ============================================================

const javaDir = path.join(
  android,
  'src/main/java/com/alhowail/field'
);

fs.mkdirSync(javaDir, { recursive: true });

// These files are stored directly in the repository root.
for (const f of [
  'AlhowailNativePlugin.java',
  'MainActivity.java'
]) {
  const src = path.join(root, f);
  const dst = path.join(javaDir, f);

  must(
    fs.existsSync(src),
    `Missing native Java file: ${src}`
  );

  fs.copyFileSync(src, dst);
  console.log(`Copied ${f}`);
}

// PDF writer helper
const printDir = path.join(
  android,
  'src/main/java/android/print'
);

fs.mkdirSync(printDir, { recursive: true });

const pdfSrc = path.join(root, 'PdfPrinter.java');
const pdfDst = path.join(printDir, 'PdfPrinter.java');

must(
  fs.existsSync(pdfSrc),
  `Missing PdfPrinter.java: ${pdfSrc}`
);

fs.copyFileSync(pdfSrc, pdfDst);
console.log('Copied PdfPrinter.java');

fs.mkdirSync(
  path.join(android, 'src/main/assets/public'),
  { recursive: true }
);

// ============================================================
// 2) APP ICONS / ANDROID RESOURCES
// ============================================================

const copyDir = (src, dst) => {
  fs.mkdirSync(dst, { recursive: true });

  for (const e of fs.readdirSync(src, { withFileTypes: true })) {
    const s = path.join(src, e.name);
    const d = path.join(dst, e.name);

    if (e.isDirectory()) {
      copyDir(s, d);
    } else {
      fs.copyFileSync(s, d);
    }
  }
};

const resourceSrc = path.join(root, 'resources', 'res');
const resourceDst = path.join(android, 'src/main/res');

// Do not fail the whole APK build if custom resources are absent.
// Capacitor's default generated resources can still be used.
if (fs.existsSync(resourceSrc)) {
  copyDir(resourceSrc, resourceDst);
  console.log('Custom Android resources copied.');
} else {
  console.warn(
    'WARNING: resources/res not found. Keeping generated Capacitor Android resources.'
  );
}

// ============================================================
// 3) ANDROID PERMISSIONS
// ============================================================

const manifestPath = path.join(
  android,
  'src/main/AndroidManifest.xml'
);

must(
  fs.existsSync(manifestPath),
  `AndroidManifest.xml not found: ${manifestPath}`
);

let manifest = fs.readFileSync(manifestPath, 'utf8');

const perms = [
  'android.permission.ACCESS_FINE_LOCATION',
  'android.permission.ACCESS_COARSE_LOCATION',
  'android.permission.CAMERA'
]
  .filter(p => !manifest.includes(p))
  .map(
    p => `    <uses-permission android:name="${p}" />`
  )
  .join('\n');

const feats = [
  'android.hardware.camera',
  'android.hardware.location.gps'
]
  .filter(f => !manifest.includes(f))
  .map(
    f =>
      `    <uses-feature android:name="${f}" android:required="false" />`
  )
  .join('\n');

must(
  manifest.includes('<application'),
  'no <application> in AndroidManifest.xml'
);

manifest = manifest.replace(
  '<application',
  `${perms}\n${feats}\n\n    <application`
);

fs.writeFileSync(manifestPath, manifest);

// ============================================================
// 4) VERSION + RELEASE SIGNING
// ============================================================

const gradlePath = path.join(android, 'build.gradle');

must(
  fs.existsSync(gradlePath),
  `app/build.gradle not found: ${gradlePath}`
);

let gradle = fs.readFileSync(gradlePath, 'utf8');

must(
  /versionCode\s+\d+/.test(gradle) &&
    /versionName\s+"[^"]*"/.test(gradle),
  'versionCode/versionName not found in app/build.gradle'
);

gradle = gradle
  .replace(
    /versionCode\s+\d+/,
    'versionCode Integer.parseInt(System.getenv("VERSION_CODE") ?: "1")'
  )
  .replace(
    /versionName\s+"[^"]*"/,
    'versionName (System.getenv("VERSION_NAME") ?: "1.0")'
  );

gradle += `

android {
    signingConfigs {
        release {
            if (System.getenv("KEYSTORE_PATH")) {
                storeFile file(System.getenv("KEYSTORE_PATH"))
                storePassword System.getenv("KEYSTORE_PASSWORD")
                keyAlias System.getenv("KEY_ALIAS")
                keyPassword System.getenv("KEY_PASSWORD")
                storeType "pkcs12"
            }
        }
    }

    buildTypes {
        release {
            if (System.getenv("KEYSTORE_PATH")) {
                signingConfig signingConfigs.release
            }
            minifyEnabled false
        }
    }
}
`;

fs.writeFileSync(gradlePath, gradle);

console.log(
  'Android project patched successfully: native plugin, PDF helper, permissions, resources, version and signing.'
);
