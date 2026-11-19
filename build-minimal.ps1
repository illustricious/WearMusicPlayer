$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sdk = Join-Path $root 'android-sdk'
if (-not (Test-Path $sdk)) { $sdk = $env:ANDROID_HOME }
$bundledJdk = Join-Path $root 'jdk'
if (Test-Path $bundledJdk) { $env:JAVA_HOME = $bundledJdk }
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a JDK (17 or newer).' }
$bt = Join-Path $sdk 'build-tools\36.0.0'
$platform = Join-Path $sdk 'platforms\android-35'
$out = Join-Path $PSScriptRoot 'app\build\minimal'
$apkOut = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug'
$java = @(Get-ChildItem (Join-Path $PSScriptRoot 'app\src\main\java') -Recurse -Filter '*.java' | ForEach-Object FullName)
$manifest = Join-Path $PSScriptRoot 'app\src\main\AndroidManifest.xml'
$resources = Join-Path $PSScriptRoot 'app\src\main\res'
New-Item -ItemType Directory -Force $out, $apkOut | Out-Null
$classes = Join-Path $out 'classes'
$dex = Join-Path $out 'dex'
$buildRoot = (Resolve-Path -LiteralPath $out).Path
foreach ($directory in @($classes, $dex)) {
  if (Test-Path -LiteralPath $directory) {
    $existing = Get-Item -LiteralPath $directory
    if ($existing.Parent.FullName -ne $buildRoot -or ($existing.Attributes -band [System.IO.FileAttributes]::ReparsePoint)) {
      throw "Refusing to clean unexpected build directory: $directory"
    }
    Remove-Item -LiteralPath $existing.FullName -Recurse -Force
  }
}
New-Item -ItemType Directory -Force $classes, $dex | Out-Null
& (Join-Path $env:JAVA_HOME 'bin\javac.exe') -encoding UTF-8 --release 8 -classpath (Join-Path $platform 'android.jar') -d $classes $java
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
$unsigned = Join-Path $out 'app-unsigned.apk'
& (Join-Path $bt 'aapt.exe') package -f -M $manifest -S $resources -I (Join-Path $platform 'android.jar') --min-sdk-version 30 --target-sdk-version 35 -F $unsigned
if ($LASTEXITCODE -ne 0) { throw 'aapt failed' }
$classesJar = Join-Path $out 'classes.jar'
Remove-Item $classesJar -ErrorAction SilentlyContinue
& (Join-Path $env:JAVA_HOME 'bin\jar.exe') cf $classesJar -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }
& (Join-Path $bt 'd8.bat') --min-api 30 --lib (Join-Path $platform 'android.jar') --output $dex $classesJar
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }
& (Join-Path $env:JAVA_HOME 'bin\jar.exe') uf $unsigned -C $dex classes.dex
if ($LASTEXITCODE -ne 0) { throw 'adding DEX failed' }
$aligned = Join-Path $out 'app-aligned.apk'
& (Join-Path $bt 'zipalign.exe') -f 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }
$keystore = Join-Path $out 'r50i-local-debug.keystore'
if (-not (Test-Path $keystore)) {
  & (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -genkeypair -keystore $keystore -storepass android -alias r50i -keypass android -dname 'CN=R50i Local Debug,O=Local,C=ID' -keyalg RSA -keysize 2048 -validity 10000
  if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
}
$apk = Join-Path $apkOut 'app-debug.apk'
& (Join-Path $bt 'apksigner.bat') sign --ks $keystore --ks-pass pass:android --key-pass pass:android --out $apk $aligned
if ($LASTEXITCODE -ne 0) { throw 'APK signing failed' }
& (Join-Path $bt 'apksigner.bat') verify --verbose $apk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
Write-Host "Built $apk"
