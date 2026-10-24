const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawnSync } = require('child_process');

const gradle = fs.readFileSync(path.join(__dirname, 'app', 'build.gradle.kts'), 'utf8');
const VERSION = (gradle.match(/versionName = "([^"]+)"/) || [])[1] || '0.0.0';
const ENTREGA = path.join(os.homedir(), 'Trabajos', 'APKs', 'UniShip-APK');
const PROPS = path.join(ENTREGA, 'llaves', 'keystore.properties');
const tarea = process.argv.includes('--aab') ? 'bundleRelease' : 'assembleRelease';

const env = Object.assign({}, process.env);
const jdks = path.join(os.homedir(), '.jdks');
if (fs.existsSync(jdks)) {
    const jdk = fs.readdirSync(jdks).filter(d => /^jdk-(17|21)\./.test(d)).sort().pop();
    if (jdk) env.JAVA_HOME = path.join(jdks, jdk);
}
env.ANDROID_HOME = env.ANDROID_HOME || path.join(process.env.LOCALAPPDATA || '', 'Android', 'Sdk');
env.ANDROID_SDK_ROOT = env.ANDROID_HOME;
if (!fs.existsSync(PROPS)) {
    console.error('Falta la llave de firma: ' + PROPS);
    process.exit(1);
}
env.UNISHIP_KEYSTORE_PROPS = PROPS;

const gw = path.join(__dirname, process.platform === 'win32' ? 'gradlew.bat' : 'gradlew');
const r = spawnSync(gw, [tarea, '--console=plain', '-q'], { cwd: __dirname, env, stdio: 'inherit', shell: process.platform === 'win32' });
if (r.status !== 0) process.exit(r.status || 1);

fs.mkdirSync(ENTREGA, { recursive: true });
const origen = tarea === 'bundleRelease'
    ? path.join(__dirname, 'app', 'build', 'outputs', 'bundle', 'release', 'app-release.aab')
    : path.join(__dirname, 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk');
const destino = path.join(ENTREGA, 'UniShip-' + VERSION + path.extname(origen));
fs.copyFileSync(origen, destino);
console.log('Listo: ' + destino + ' (' + (fs.statSync(destino).size / 1048576).toFixed(1) + ' MB)');
