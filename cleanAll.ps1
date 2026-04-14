# Clean all gradle build artifacts including everything downloaded. Use this script to test
# if the public repository is really self-contained.
#   Please note, many gradle artifacts are shared between different gradle projects.
# Running this script may affect all of your gradle projects.
gradlew --stop
Remove-Item -Recurse -Force .gradle
Remove-Item -Recurse -Force build
Remove-Item -Recurse -Force "$env:USERPROFILE\.gradle"
