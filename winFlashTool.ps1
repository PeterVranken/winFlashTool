# Launch Java application winFlashTool
$usage = `
'usage: .\winFlashTool.ps1 ...
  Launch Java application winFlashTool
  ... stands for the application arguments. Try --help first'

function endWithError { write-host $usage; exit; }

# Support help
#if($args[0] -match '^$|^(-h|/h|/\?|--help)$') {endWithError}

# Limit the allowed number of parameters.
#if($args[0] -match '.') {endWithError}


# Check if an environment variable $varName is already defined. If not, assign it the given
# value $value.
function setEnvVar ([string]$varName, [string]$value) {

    $oldValue = (get-item "Env:$varName" -ErrorAction SilentlyContinue).Value
    if ($oldValue -eq $null) {
        set-item env:$varName "$value"
        #write-host "Environment variable $varName is set to $value"
    } else {
        #write-host "Environment variable $varName is already persistently set to $oldValue"
    }
}

# The build requires a JAVA runtime 8.
#   TODO Check or set this path. The proposed path is the FEV standard installation path
# and it might work out of the box.
setEnvVar JAVA_HOME "C:\ProgramFiles\Java\jdk-23.0.1"

# Prepare the Windows search path for the run of the application.
$env:PATH = `
    "$env:JAVA_HOME\bin" `
    + ";" + [System.IO.Path]::GetFullPath("$PSScriptRoot\lib\PEAK\x86_64") `
    + ";" + [System.IO.Path]::GetFullPath("$PSScriptRoot\lib\PEAK\x86_64") `
    + ";$env:PATH"

# Prepare the Java classpath for the run of the application.
$cp = [System.IO.Path]::GetFullPath("$PSScriptRoot\build\libs\winFlashTool-0.1.jar") `
      + ";" + [System.IO.Path]::GetFullPath("$PSScriptRoot\lib\jna\jna-5.14.0.jar") `
      + ";" + [System.IO.Path]::GetFullPath("$PSScriptRoot\lib\jna\jna-platform-5.14.0.jar") `
      + ";" + [System.IO.Path]::GetFullPath("$PSScriptRoot\lib\Apache\log4j\*") `
      + ";" + [System.IO.Path]::GetFullPath("$PSScriptRoot\lib\Apache\commons-lang3-3.20\commons-lang3-3.20.0.jar")

# Let Java run the application.
java.exe -ea -classpath $cp winFlashTool.WinFlashTool @args
