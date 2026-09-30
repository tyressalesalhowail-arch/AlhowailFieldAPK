const fs = require("fs");
const path = require("path");

const root = __dirname;
const android = path.join(root, "android", "app");

const must = (cond, msg) => {
  if (!cond) {
    console.error("PATCH FAILED: " + msg);
    process.exit(1);
  }
};

// 1) Native Java code
const javaDir = path.join(
  android,
  "src",
  "main",
  "java",
  "com",
  "alhowail",
  "field"
);

fs.mkdirSync(javaDir, { recursive: true });

for (const file of ["AlhowailNativePlugin.java", "MainActivity.java"]) {
  const source = path.join(root, file);
  must(
    fs.existsSync(source),
    `Missing Java source file: ${source}`
  );

  fs.copyFileSync(
    source,
    path.join(javaDir, file)
  );
}

// 2) Optional app resources/icons
const resourcesDir = path.join(root, "resources", "res");
const androidResDir = path.join(android, "src", "main", "res");

if (fs.existsSync(resourcesDir)) {
  const copyDir = (src, dst) => {
    fs.mkdirSync(dst, { recursive: true });

    for (const entry of fs.readdirSync(src, {
      withFileTypes: true
    })) {
      const source = path.join(src, entry.name);
      const destination = path.join(dst, entry.name);

      if (entry.isDirectory()) {
        copyDir(source, destination);
      } else {
        fs.copyFileSync(source, destination);
      }
    }
  };

  copyDir(resourcesDir, androidResDir);
}

// 3) Android permissions
const manifestPath = path.join(
  android,
  "src",
  "main",
  "AndroidManifest.xml"
);

must(
  fs.existsSync(manifestPath),
  "AndroidManifest.xml not found"
);

let manifest = fs.readFileSync(manifestPath, "utf8");

const permissions = [
  "android.permission.ACCESS_FINE_LOCATION",
  "android.permission.ACCESS_COARSE_LOCATION",
  "android.permission.CAMERA"
];

for (const permission of permissions) {
  if (!manifest.includes(permission)) {
    manifest = manifest.replace(
      "<application",
      `<uses-permission android:name="${permission}" />\n\n    <application`
    );
  }
}

const features = [
  "android.hardware.camera",
  "android.hardware.location.gps"
];

for (const feature of features) {
  if (!manifest.includes(feature)) {
    manifest = manifest.replace(
      "<application",
      `<uses-feature android:name="${feature}" android:required="false" />\n\n    <application`
    );
  }
}

fs.writeFileSync(manifestPath, manifest);

// 4) Version + signing
const gradlePath = path.join(android, "build.gradle");

must(
  fs.existsSync(gradlePath),
  "android/app/build.gradle not found"
);

let gradle = fs.readFileSync(gradlePath, "utf8");

must(
  /versionCode\s+\d+/.test(gradle),
  "versionCode not found in app/build.gradle"
);

must(
  /versionName\s+"[^"]*"/.test(gradle),
  "versionName not found in app/build.gradle"
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
  "Android project patched successfully: native code, permissions, version and signing."
);
