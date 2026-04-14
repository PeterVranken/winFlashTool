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

$appLaunchScript = [System.IO.Path]::GetFullPath("$PSScriptRoot\build\install\winFlashTool\bin\winFlashTool.bat")
.$appLaunchScript @args
