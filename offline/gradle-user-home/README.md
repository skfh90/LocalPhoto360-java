# Trimmed Gradle user home

Split zip of Gradle **8.9** plus this app’s Maven cache. Each part is under GitHub’s 100 MB file limit.

Join and install on Windows:

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\tools\install-offline-gradle.ps1
```

That writes `wrapper\` and `caches\modules-2\` into `C:\Users\1-PYC\.gradle`.
