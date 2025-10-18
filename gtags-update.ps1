# See usage message for help. See
# http://technet.microsoft.com/de-de/library/ee221100(en-us).aspx for syntax of Powershell
# scripts.
$usage = `
'usage: .\gtags-update.ps1
  Create gtags database(s) for the project'

#
# Copyright (c) 2021, FEV Europe GmbH, Germany
# Copyright (c) 2022-2025, FEV.io GmbH, Germany
#
# Author: Peter Vranken (mailto:vranken@FEV.io)
#

function endWithError
{
    Write-Host $usage;
    Write-Host "Press any key to continue ..."
    $dummy = $host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

    exit;
}

# Support help
if($args[0] -match '^(-h|/h|/\?|--help)$') {endWithError}

# Limit the allowed number of parameters.
if($args[1] -match '.') {endWithError}

# Run this script in the background with lowered priority.
$null = wmic process where "Handle=$PID" call setpriority "below normal"

# TODO Enter all directories, where you want to see a gtags database as comma separated list.
$dbPathList = "src"

# Process all target paths in a loop.
#   The loop variable needs to have a single character name.
foreach ($a in $dbPathList) {
    # Support relative path designations by making them absolute.
    try {
        $a = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath("$a")
    }
    catch{
        # Invalid path. We can skip it.
        write-host "Skipping invalid directory designation $a..."
        continue
    }
    cd $a
    write-host "Processing source code directory $a...`nState before update:"
    dir G?*A??
    #pwd.exe
    gtags.exe  

    # Evaluate the return code of the previous command.
    if ($lastExitCode -ge 1) {
        write-host 'There was an error running gtags'
        exit
    } else {
        write-host "State after update:"
        dir G?*A??
    }

    popd
}

# Restore the original priority: The change affected the process and this script had not
# necessarily been started in a new process. We don't want to permanently change the
# priority of the calling (shell) process.
$null = wmic process where "Handle=$PID" call setpriority "normal"

# Give a chance to inspect the results.
."$env:windir\System32\timeout.exe" /T 3
