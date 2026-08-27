# Gradle wrapper (project)

`gradle-wrapper.jar` and `gradle-wrapper.properties` stay in git.

`gradle-8.9-bin.zip` is not in git (GitHub 100 MB file limit). First online build downloads it into:

`C:\Users\1-PYC\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\`

For a fully offline PC, use `tools/install-offline-gradle.ps1` and the `gradle-user-home.zip.*` parts instead of copying a whole `%USERPROFILE%\.gradle` tree.
