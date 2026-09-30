// Runs on GitHub after `npx cap add android`: adds our native plugin, icons, permissions, version and signing.
const fs = require('fs'), path = require('path');
const root = path.join(__dirname, '..'), android = path.join(root, 'android', 'app');
const must = (cond, msg) => { if (!cond) { console.error('PATCH FAILED: ' + msg); process.exit(1); } };

// 1) native code (plugin + MainActivity that registers it)
const javaDir = path.join(android, 'src/main/java/com/alhowail/field');
fs.mkdirSync(javaDir, { recursive: true });
for (const f of ['AlhowailNativePlugin.java', 'MainActivity.java']) fs.copyFileSync(path.join(root, 'native', f), path.join(javaDir, f));
const printDir = path.join(android, 'src/main/java/android/print');       // PDF writer helper (package android.print)
fs.mkdirSync(printDir, { recursive: true });
fs.copyFileSync(path.join(root, 'native/android_print/PdfPrinter.java'), path.join(printDir, 'PdfPrinter.java'));
fs.mkdirSync(path.join(android, 'src/main/assets/public'), { recursive: true });

// 2) app icons (Al-Howail logo)
const copyDir = (src, dst) => { fs.mkdirSync(dst, { recursive: true }); for (const e of fs.readdirSync(src, { withFileTypes: true })) {
  const s = path.join(src, e.name), d = path.join(dst, e.name); e.isDirectory() ? copyDir(s, d) : fs.copyFileSync(s, d); } };
copyDir(path.join(root, 'resources/res'), path.join(android, 'src/main/res'));

// 3) permissions: precise location + camera
const manifestPath = path.join(android, 'src/main/AndroidManifest.xml');
let manifest = fs.readFileSync(manifestPath, 'utf8');
const perms = ['android.permission.ACCESS_FINE_LOCATION', 'android.permission.ACCESS_COARSE_LOCATION', 'android.permission.CAMERA']
  .filter(p => !manifest.includes(p)).map(p => `    <uses-permission android:name="${p}" />`).join('\n');
const feats = ['android.hardware.camera', 'android.hardware.location.gps'].filter(f => !manifest.includes(f))
  .map(f => `    <uses-feature android:name="${f}" android:required="false" />`).join('\n');
must(manifest.includes('<application'), 'no <application> in manifest');
manifest = manifest.replace('<application', `${perms}\n${feats}\n\n    <application`);
fs.writeFileSync(manifestPath, manifest);

// 4) version (from the GitHub build) + release signing (keystore comes from GitHub secrets)
const gradlePath = path.join(android, 'build.gradle');
let gradle = fs.readFileSync(gradlePath, 'utf8');
must(/versionCode\s+\d+/.test(gradle) && /versionName\s+"[^"]*"/.test(gradle), 'versionCode/versionName not found in app/build.gradle');
gradle = gradle.replace(/versionCode\s+\d+/, 'versionCode Integer.parseInt(System.getenv("VERSION_CODE") ?: "1")')
               .replace(/versionName\s+"[^"]*"/, 'versionName (System.getenv("VERSION_NAME") ?: "1.0")');
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
            if (System.getenv("KEYSTORE_PATH")) signingConfig signingConfigs.release
            minifyEnabled false
        }
    }
}
`;
fs.writeFileSync(gradlePath, gradle);
console.log('Android project patched: plugin, icons, permissions, version, signing.');
