#Requires -Version 7.0
# Open Parties and Claims - adds chunk claims and player parties to Minecraft
# Copyright (C) 2022-2026, Xaero <xaero1996@gmail.com> and contributors
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of version 3 of the GNU Lesser General Public License
# (LGPL-3.0-only) as published by the Free Software Foundation.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Lesser General Public License for more details.
#
# You should have received copies of the GNU Lesser General Public License
# and the GNU General Public License along with this program.
# If not, see <https://www.gnu.org/licenses/>.

<#
.SYNOPSIS
    [Team Claims] The automated two-player test of the 1.4 hub: one dedicated Fabric server and two Fabric dev clients
    (Player1 = op and party owner, Player2 = plain member), driven by scripts, with a report.

.DESCRIPTION
    1. Checks the prerequisites (run-server/eula.txt, online-mode=false, Player1 in ops.json, port free, the game lock
       file, no test JVM of this checkout still running).
    2. Prepares a fresh server world run-server/PerfTest-OPACHub (deletes only that folder) with the test's server
       tomls in its serverconfig folder (teamClaimsMinMembers = 2, maxSingleClaimActionSize = 4), and fresh
       options.txt files for the two client run folders (default key bindings, no pause on focus loss).
    3. Builds the dev-only source set (one Gradle run).
    4. Starts :Fabric:runTwoPlayerTestServer, waits for the server to be ready, then
       :Fabric:runTwoPlayerTestClient1 and :Fabric:runTwoPlayerTestClient2 (each with -PtwoPlayerTest=<out>). The
       clients connect by themselves and run their scenario (Fabric/src/twoplayertest/.../client/Scenario.java).
    5. Waits for both drivers to finish (every wait has a time-out), asks the server to stop, stops ONLY the processes
       this script started (their process trees, by pid), collects the logs, checks the server log and crash reports.
    6. Writes results.json (one entry per assertion) and summary.md into the output folder and exits with 0 only when
       every assertion passed: 1 when one failed (a time-out is a failure), 2 when none failed but one was skipped,
       3 when the run could not start.

    An assertion id of tools/twoplayertest/expected-assertions.txt that no driver reported counts as FAILED, so a
    driver that stops early can never look green.

    -DryRun does everything except starting the server and the clients: prerequisites (except the game lock), world and
    options preparation, the build, a Gradle check that the three run tasks exist, and a self-test of the result merge
    (a missing id must fail, a complete passing set must pass). It does not need the game lock.

.EXAMPLE
    pwsh -File tools/twoplayertest/Run-TwoPlayerTest.ps1 -DryRun
.EXAMPLE
    pwsh -File tools/twoplayertest/Run-TwoPlayerTest.ps1
#>
[CmdletBinding()]
param(
    [switch]$DryRun,
    [switch]$SkipBuild,
    [string]$OutDir,
    [int]$ScenarioTimeoutMinutes = 25,
    [int]$StartTimeoutMinutes = 12,
    [int]$TotalTimeoutMinutes = 50,
    [int]$Port = 25565,
    [string]$GameLock = 'F:\Coding\.knowledge\game.lock',
    [switch]$NoGameLockCheck
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 3.0

$Repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$WorldName = 'PerfTest-OPACHub'
$ServerDir = Join-Path $Repo 'run-server'
$ClientDirs = [ordered]@{ Player1 = (Join-Path $Repo 'run-tpt-client1'); Player2 = (Join-Path $Repo 'run-tpt-client2') }
$Tasks = [ordered]@{ server = ':Fabric:runTwoPlayerTestServer'; Player1 = ':Fabric:runTwoPlayerTestClient1'; Player2 = ':Fabric:runTwoPlayerTestClient2' }
$Manifest = Join-Path $PSScriptRoot 'expected-assertions.txt'
$StartedAt = Get-Date
if (-not $OutDir) {
    $OutDir = Join-Path $Repo ('build\twoPlayerTest\' + $StartedAt.ToString('yyyyMMdd-HHmmss') + ($DryRun ? '-dryrun' : ''))
}
$OutDir = [System.IO.Path]::GetFullPath($OutDir)
if ($OutDir.Contains(' ')) { throw "The output folder must not contain spaces (it is passed to Gradle as a property): $OutDir" }

$script:Entries = [System.Collections.Generic.List[object]]::new()
$script:Started = [ordered]@{}   # role -> System.Diagnostics.Process (the gradlew.bat process this script started)
$RunIds = @('run.preconditions', 'run.build', 'run.server-started', 'run.client-Player1-started', 'run.client-Player2-started',
    'run.drivers-finished', 'run.processes-stopped', 'run.server-log-rejection-warning', 'run.no-crash-reports')

# ---------------------------------------------------------------------------------------------------------------- log

function Write-Log([string]$Text) {
    $line = '[' + (Get-Date).ToString('HH:mm:ss') + '] ' + $Text
    Write-Host $line
    Add-Content -LiteralPath (Join-Path $OutDir 'run.log') -Value $line -Encoding utf8
}

function Add-Entry([string]$Id, [string]$Description, [string]$Status, [string]$Expected, [string]$Actual, [string[]]$Screenshots = @()) {
    if ($Status -notin @('PASS', 'FAIL', 'SKIPPED')) { throw "bad status $Status" }
    $script:Entries.Add([pscustomobject]@{
            id = $Id; player = 'script'; description = $Description; status = $Status; expected = $Expected; actual = $Actual
            screenshots = @($Screenshots); time = (Get-Date).ToString('o')
        })
    Write-Log ("{0,-7} {1}: {2}" -f $Status, $Id, $Actual)
}

function Add-Check([string]$Id, [string]$Description, [bool]$Pass, [string]$Expected, [string]$Actual) {
    Add-Entry $Id $Description ($Pass ? 'PASS' : 'FAIL') $Expected $Actual
}

# ------------------------------------------------------------------------------------------------------------ helpers

function Read-ManifestIds([string]$Path) {
    return @(Get-Content -LiteralPath $Path -Encoding utf8 | ForEach-Object { $_.Trim() } | Where-Object { $_ -and -not $_.StartsWith('#') })
}

function Get-Descendants([int]$ProcessId) {
    $all = @(Get-CimInstance Win32_Process -Property ProcessId, ParentProcessId, CommandLine, CreationDate)
    $found = [System.Collections.Generic.List[object]]::new()
    $queue = [System.Collections.Generic.Queue[int]]::new()
    $queue.Enqueue($ProcessId)
    while ($queue.Count -gt 0) {
        $parent = $queue.Dequeue()
        foreach ($p in $all) {
            if ($p.ParentProcessId -eq $parent -and $p.ProcessId -ne $parent -and -not ($found | Where-Object ProcessId -EQ $p.ProcessId)) {
                $found.Add($p)
                $queue.Enqueue([int]$p.ProcessId)
            }
        }
    }
    return $found
}

function Get-StatusPid([string]$Name) {
    $file = Join-Path $OutDir "status\$Name"
    if (-not (Test-Path -LiteralPath $file)) { return $null }
    $line = Get-Content -LiteralPath $file -Encoding utf8 | Where-Object { $_ -like 'pid=*' } | Select-Object -First 1
    if (-not $line) { return $null }
    return [int]($line.Substring(4))
}

# Stops a process this script started and everything below it; also the game JVM whose pid the mod wrote (see
# Get-GameProcess: a recycled pid of somebody else's process is never touched). Never anything else.
function Stop-Started([string]$Role) {
    $stopped = @()
    $proc = $script:Started[$Role]
    if ($proc) {
        $tree = @(Get-Descendants $proc.Id)
        [array]::Reverse($tree)
        foreach ($p in $tree) {
            try { Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop; $stopped += "$($p.ProcessId)" } catch { }
        }
        if (-not $proc.HasExited) { try { Stop-Process -Id $proc.Id -Force -ErrorAction Stop; $stopped += "$($proc.Id)" } catch { } }
    }
    $game = Get-GameProcess $Role
    if ($game) {
        try { Stop-Process -Id $game.ProcessId -Force -ErrorAction Stop; $stopped += "$($game.ProcessId)" } catch { }
    }
    return $stopped
}

# The game JVM of a role (the pid its mod wrote into status/), or $null: only a java process that was already running
# when the mod wrote the file counts, so a pid that another process got later is never taken for ours.
function Get-GameProcess([string]$Role) {
    $statusName = if ($Role -eq 'server') { 'server.init' } else { "$Role.started" }
    $statusFile = Join-Path $OutDir "status\$statusName"
    $gamePid = Get-StatusPid $statusName
    if (-not $gamePid) { return $null }
    $game = Get-CimInstance Win32_Process -Filter "ProcessId = $gamePid" -ErrorAction SilentlyContinue
    if ($game -and $game.Name -match '^javaw?\.exe$' -and $game.CreationDate -le (Get-Item -LiteralPath $statusFile).LastWriteTime) { return $game }
    return $null
}

function Test-GameAlive([string]$Role) {
    return [bool](Get-GameProcess $Role)
}

function Start-GradleRun([string]$Role, [string[]]$GradleArgs) {
    $logDir = Join-Path $OutDir 'logs'
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $stdout = Join-Path $logDir "gradle-$Role.out.log"
    $stderr = Join-Path $logDir "gradle-$Role.err.log"
    Write-Log "starting $Role : gradlew $($GradleArgs -join ' ')"
    $proc = Start-Process -FilePath (Join-Path $Repo 'gradlew.bat') -ArgumentList $GradleArgs -WorkingDirectory $Repo `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -NoNewWindow -PassThru
    $null = $proc.Handle   # keeps the exit code readable after the process ends
    $script:Started[$Role] = $proc
    return $proc
}

function Wait-For([string]$What, [int]$TimeoutSeconds, [scriptblock]$Condition, [scriptblock]$GiveUp = { $false }) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (& $Condition) { return 'ok' }
        $reason = & $GiveUp
        if ($reason) { return "gave up: $reason" }
        Start-Sleep -Milliseconds 1000
    }
    return "timed out after $TimeoutSeconds s waiting for $What"
}

function Invoke-Gradle([string]$Name, [string[]]$GradleArgs) {
    $log = Join-Path $OutDir "logs\gradle-$Name.log"
    New-Item -ItemType Directory -Force -Path (Split-Path $log) | Out-Null
    Write-Log "gradlew $($GradleArgs -join ' ')"
    & (Join-Path $Repo 'gradlew.bat') @GradleArgs *> $log
    return $LASTEXITCODE
}

# ------------------------------------------------------------------------------------------------------------ merging

# Combines the script's own entries with the drivers' results files: every id of the manifest must be reported, an
# unreported one is FAILED; entries outside the manifest (the drivers' "unplanned." ids) are kept as they are.
function Merge-Results([string]$Folder, [object[]]$ScriptEntries, [string[]]$ManifestIds, [bool]$IsDryRun, [datetime]$Start) {
    $driverEntries = @{}
    $files = [ordered]@{}
    foreach ($player in 'Player1', 'Player2') {
        $file = Join-Path $Folder "$player\results.json"
        $files[$player] = 'missing'
        if (Test-Path -LiteralPath $file) {
            try {
                $json = Get-Content -LiteralPath $file -Raw -Encoding utf8 | ConvertFrom-Json
                $files[$player] = $json.finished ? 'finished' : 'NOT finished (the driver stopped before it wrote its end)'
                foreach ($entry in @($json.results)) {
                    if (-not $driverEntries.ContainsKey($entry.id)) { $driverEntries[$entry.id] = [System.Collections.Generic.List[object]]::new() }
                    $driverEntries[$entry.id].Add($entry)
                }
            } catch {
                $files[$player] = "unreadable: $($_.Exception.Message)"
            }
        }
    }
    $all = [System.Collections.Generic.List[object]]::new()
    foreach ($e in $ScriptEntries) { $all.Add($e) }
    foreach ($id in $ManifestIds) {
        if ($driverEntries.ContainsKey($id)) {
            $list = $driverEntries[$id]
            if ($list.Count -eq 1) { $all.Add($list[0]) }
            else {
                $all.Add([pscustomobject]@{ id = $id; player = $list[0].player; description = $list[0].description; status = 'FAIL'
                        expected = 'reported once'; actual = "reported $($list.Count) times: " + (($list | ForEach-Object { "$($_.status) $($_.actual)" }) -join ' / ')
                        screenshots = @($list | ForEach-Object { $_.screenshots }); time = (Get-Date).ToString('o') })
            }
            $driverEntries.Remove($id)
        } else {
            $player = $id.StartsWith('p1.') ? 'Player1' : ($id.StartsWith('p2.') ? 'Player2' : '?')
            $status = $IsDryRun ? 'SKIPPED' : 'FAIL'
            $why = $IsDryRun ? 'dry run: nothing was started' : "not reported by $player (its results.json: $($files[$player]))"
            $all.Add([pscustomobject]@{ id = $id; player = $player; description = '(not reported)'; status = $status; expected = 'reported by the driver'
                    actual = $why; screenshots = @(); time = (Get-Date).ToString('o') })
        }
    }
    foreach ($rest in $driverEntries.Keys) { foreach ($e in $driverEntries[$rest]) { $all.Add($e) } }

    $counts = [ordered]@{ PASS = 0; FAIL = 0; SKIPPED = 0 }
    foreach ($e in $all) { $counts[$e.status] = $counts[$e.status] + 1 }
    $overall = if ($counts.FAIL -gt 0) { 'FAIL' } elseif ($counts.SKIPPED -gt 0) { 'NOT GREEN (skipped)' } else { 'PASS' }
    if ($IsDryRun) { $overall = ($counts.FAIL -gt 0) ? 'DRY RUN FAILED' : 'DRY RUN OK' }

    $report = [ordered]@{
        overall = $overall; dryRun = $IsDryRun; startedAt = $Start.ToString('o'); finishedAt = (Get-Date).ToString('o')
        counts = $counts; driverFiles = $files; results = $all
    }
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $Folder 'results.json') -Encoding utf8

    $md = [System.Text.StringBuilder]::new()
    [void]$md.AppendLine('# OPAC Team Claims 1.4.0: two-player test' + ($IsDryRun ? ' (DRY RUN, nothing started)' : ''))
    [void]$md.AppendLine('')
    [void]$md.AppendLine("**Overall: $overall**  ")
    [void]$md.AppendLine("PASS $($counts.PASS), FAIL $($counts.FAIL), SKIPPED $($counts.SKIPPED). Started $($Start.ToString('yyyy-MM-dd HH:mm:ss')), finished $((Get-Date).ToString('HH:mm:ss')).  ")
    [void]$md.AppendLine("Driver results: Player1 $($files.Player1); Player2 $($files.Player2).")
    [void]$md.AppendLine('')
    [void]$md.AppendLine('| Status | Id | Player | What | Expected | Actual | Screenshots |')
    [void]$md.AppendLine('|---|---|---|---|---|---|---|')
    foreach ($e in $all) {
        $cells = @($e.status, $e.id, $e.player, $e.description, $e.expected, $e.actual, ((@($e.screenshots) | ForEach-Object { "[$(Split-Path $_ -Leaf)]($_)" }) -join ' '))
        $cells = $cells | ForEach-Object { ([string]$_).Replace('|', '\|').Replace("`r", ' ').Replace("`n", ' ') }
        [void]$md.AppendLine('| ' + ($cells -join ' | ') + ' |')
    }
    Set-Content -LiteralPath (Join-Path $Folder 'summary.md') -Value $md.ToString() -Encoding utf8
    return $report
}

# A self-test of Merge-Results on made-up driver files: a missing id must fail, a complete passing set must pass.
function Test-Merge([string[]]$ManifestIds) {
    $problems = @()
    $base = Join-Path $OutDir 'merge-selftest'
    foreach ($case in 'incomplete', 'complete') {
        $folder = Join-Path $base $case
        foreach ($player in 'Player1', 'Player2') {
            New-Item -ItemType Directory -Force -Path (Join-Path $folder $player) | Out-Null
            $prefix = ($player -eq 'Player1') ? 'p1.' : 'p2.'
            $ids = @($ManifestIds | Where-Object { $_.StartsWith($prefix) })
            if ($case -eq 'incomplete' -and $player -eq 'Player2') { $ids = @($ids | Select-Object -First ([Math]::Max(0, $ids.Count - 1))) }
            $results = @($ids | ForEach-Object { [ordered]@{ id = $_; player = $player; description = 'made up'; status = 'PASS'; expected = 'x'; actual = 'x'; screenshots = @(); time = '' } })
            [ordered]@{ player = $player; finished = $true; results = $results } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $folder "$player\results.json") -Encoding utf8
        }
        $scriptEntries = @([pscustomobject]@{ id = 'run.selftest'; player = 'script'; description = 'made up'; status = 'PASS'; expected = 'x'; actual = 'x'; screenshots = @(); time = '' })
        $report = Merge-Results $folder $scriptEntries $ManifestIds $false (Get-Date)
        $expected = ($case -eq 'complete') ? 'PASS' : 'FAIL'
        if ($report.overall -ne $expected) { $problems += "merge self-test '$case': overall $($report.overall), expected $expected" }
        if ($case -eq 'incomplete' -and $report.counts.FAIL -ne 1) { $problems += "merge self-test 'incomplete': $($report.counts.FAIL) failures, expected exactly 1 (the unreported id)" }
        if ($case -eq 'complete' -and $report.counts.PASS -ne ($ManifestIds.Count + 1)) { $problems += "merge self-test 'complete': $($report.counts.PASS) passes, expected $($ManifestIds.Count + 1)" }
        if (-not (Test-Path -LiteralPath (Join-Path $folder 'summary.md'))) { $problems += "merge self-test '$case': no summary.md" }
    }
    return $problems
}

# --------------------------------------------------------------------------------------------------- the preparation

function Test-Prerequisites([bool]$CheckLock) {
    $problems = @()
    if (-not (Test-Path -LiteralPath (Join-Path $Repo 'gradlew.bat'))) { $problems += "no gradlew.bat in $Repo" }
    if (-not (Test-Path -LiteralPath $Manifest)) { $problems += "no manifest $Manifest" }
    $eula = Join-Path $ServerDir 'eula.txt'
    if (-not (Test-Path -LiteralPath $eula) -or -not (Select-String -LiteralPath $eula -Pattern '^\s*eula\s*=\s*true\s*$' -Quiet)) {
        $problems += "$eula does not hold eula=true (the owner accepts the EULA, not this script)"
    }
    $props = Join-Path $ServerDir 'server.properties'
    if (-not (Test-Path -LiteralPath $props)) { $problems += "no $props" }
    else {
        if (-not (Select-String -LiteralPath $props -Pattern '^\s*online-mode\s*=\s*false\s*$' -Quiet)) { $problems += "$props does not hold online-mode=false (offline dev accounts)" }
        $portLine = Select-String -LiteralPath $props -Pattern '^\s*server-port\s*=\s*(\d+)' | Select-Object -First 1
        if ($portLine -and [int]$portLine.Matches[0].Groups[1].Value -ne $Port) { $problems += "$props has server-port=$($portLine.Matches[0].Groups[1].Value), the clients connect to $Port" }
    }
    $ops = Join-Path $ServerDir 'ops.json'
    if (-not (Test-Path -LiteralPath $ops)) { $problems += "no $ops (Player1 must be op)" }
    else {
        $opList = @(Get-Content -LiteralPath $ops -Raw -Encoding utf8 | ConvertFrom-Json)
        if (-not ($opList | Where-Object { $_.name -eq 'Player1' -and $_.level -ge 2 })) { $problems += "Player1 is not op (level 2+) in $ops" }
        if ($opList | Where-Object { $_.name -eq 'Player2' }) { $problems += "Player2 must not be op in $ops" }
    }
    if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { $problems += "port $Port is in use (another server?)" }
    $leftovers = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" | Where-Object {
            $_.CommandLine -and $_.CommandLine.Contains($Repo) -and ($_.CommandLine -match 'opac\.twoplayertest\.role=(server|Player1|Player2)')
        })
    if ($leftovers) { $problems += "test JVMs of this checkout are still running (pids $(($leftovers | ForEach-Object ProcessId) -join ', ')); stop them first" }
    if ($CheckLock -and -not $NoGameLockCheck) {
        if (-not (Test-Path -LiteralPath $GameLock)) { $problems += "the game lock $GameLock does not exist: take it first (this run opens two game windows)" }
        else { Write-Log "game lock: $((Get-Content -LiteralPath $GameLock -Raw -Encoding utf8).Trim())" }
    }
    return $problems
}

function Initialize-World {
    $world = Join-Path $ServerDir $WorldName
    if (Test-Path -LiteralPath $world) {
        Write-Log "deleting the old test world $world"
        Remove-Item -LiteralPath $world -Recurse -Force
    }
    $serverconfig = Join-Path $world 'serverconfig'
    New-Item -ItemType Directory -Force -Path $serverconfig | Out-Null
    # Overrides of the config/ tomls for this world only (Forge Config API Port reads <world>/serverconfig first);
    # the loader fills in every option not written here with its default. Admin-tab changes land here as well, so they
    # go away with the world.
    Set-Content -LiteralPath (Join-Path $serverconfig 'openpartiesandclaims-teamclaims-server.toml') -Encoding utf8 -Value @'
[teamClaims]
	teamClaimsMinMembers = 2
'@
    Set-Content -LiteralPath (Join-Path $serverconfig 'openpartiesandclaims-server.toml') -Encoding utf8 -Value @'
[serverConfig]
	[serverConfig.claims]
		maxSingleClaimActionSize = 4
'@
    $latest = Join-Path $ServerDir 'logs\latest.log'
    if (Test-Path -LiteralPath $latest) { Remove-Item -LiteralPath $latest -Force }
}

function Initialize-Clients {
    foreach ($player in $ClientDirs.Keys) {
        $dir = $ClientDirs[$player]
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
        # A fresh options.txt without a single key_ line: every key binding is the default (P opens the hub, and
        # Social Interactions is on P as well). No pause when the window loses focus, no onboarding screen, no sound.
        Set-Content -LiteralPath (Join-Path $dir 'options.txt') -Encoding utf8 -Value @'
version:3955
onboardAccessibility:false
pauseOnLostFocus:false
guiScale:2
renderDistance:4
simulationDistance:5
maxFps:60
soundCategory_master:0.0
narrator:0
tutorialStep:none
joinedFirstServer:true
skipMultiplayerWarning:true
fullscreen:false
'@
        $latest = Join-Path $dir 'logs\latest.log'
        if (Test-Path -LiteralPath $latest) { Remove-Item -LiteralPath $latest -Force }
    }
}

function Get-NewCrashReports {
    $found = @()
    foreach ($dir in @($ServerDir) + @($ClientDirs.Values)) {
        $crashDir = Join-Path $dir 'crash-reports'
        if (Test-Path -LiteralPath $crashDir) {
            $found += @(Get-ChildItem -LiteralPath $crashDir -File | Where-Object { $_.LastWriteTime -ge $StartedAt } | ForEach-Object FullName)
        }
    }
    return $found
}

# ------------------------------------------------------------------------------------------------------------- main

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
Write-Log "two-player test$($DryRun ? ' (DRY RUN)' : ''): output $OutDir"
$manifestIds = Read-ManifestIds $Manifest
$exitCode = 3
try {
    $problems = @(Test-Prerequisites (-not $DryRun))
    Add-Check 'run.preconditions' 'Prerequisites of the run' ($problems.Count -eq 0) 'all prerequisites met' (($problems.Count -eq 0) ? 'all met' : ($problems -join '; '))
    if ($problems.Count -gt 0) { throw [System.OperationCanceledException]::new('prerequisites') }

    Initialize-World
    Initialize-Clients
    Write-Log "prepared $ServerDir\$WorldName and the options of $($ClientDirs.Values -join ', ')"

    $property = "-PtwoPlayerTest=$OutDir"
    if ($SkipBuild) {
        Add-Entry 'run.build' 'Build of the dev-only source set' 'SKIPPED' 'built' 'SKIPPED: -SkipBuild'
    } else {
        $code = Invoke-Gradle 'build' @(':Fabric:classes', ':Fabric:twoplayertestClasses', $property, '--console=plain')
        Add-Check 'run.build' 'Build of the dev-only source set' ($code -eq 0) 'gradle exit code 0' "gradle exit code $code (log logs/gradle-build.log)"
        if ($code -ne 0) { throw [System.OperationCanceledException]::new('build') }
    }

    if ($DryRun) {
        $code = Invoke-Gradle 'tasks-check' @('-m', $Tasks.server, $Tasks.Player1, $Tasks.Player2, $property, '--console=plain')
        $log = Get-Content -LiteralPath (Join-Path $OutDir 'logs\gradle-tasks-check.log') -Raw -Encoding utf8
        $missing = @($Tasks.Values | Where-Object { -not $log.Contains($_ + ' SKIPPED') })
        Add-Check 'dryrun.run-tasks-exist' 'The three run tasks exist with -PtwoPlayerTest' ($code -eq 0 -and $missing.Count -eq 0) ($Tasks.Values -join ', ') `
            ("gradle -m exit code $code" + (($missing.Count -gt 0) ? "; missing: $($missing -join ', ')" : '; all three planned'))
        $mergeProblems = @(Test-Merge $manifestIds)
        Add-Check 'dryrun.merge-selftest' 'The merge fails an unreported id and passes a complete set' ($mergeProblems.Count -eq 0) 'both cases as expected' `
            (($mergeProblems.Count -eq 0) ? 'both cases as expected' : ($mergeProblems -join '; '))
        foreach ($role in $Tasks.Keys) { Write-Log "would start $role : gradlew $($Tasks[$role]) $property -PtwoPlayerTestTimeoutMinutes=$ScenarioTimeoutMinutes --console=plain" }
        foreach ($id in $RunIds | Where-Object { $_ -notin @('run.preconditions', 'run.build') }) { Add-Entry $id '(run step)' 'SKIPPED' '-' 'SKIPPED: dry run, nothing was started' }
        $report = Merge-Results $OutDir $script:Entries $manifestIds $true $StartedAt
        Write-Log "dry run: $($report.overall) (results.json and summary.md in $OutDir)"
        $exitCode = ($report.overall -eq 'DRY RUN OK') ? 0 : 1
    } else {

        $timeoutProp = "-PtwoPlayerTestTimeoutMinutes=$ScenarioTimeoutMinutes"
        $deadline = $StartedAt.AddMinutes($TotalTimeoutMinutes)

        # ---- server
        $server = Start-GradleRun 'server' @($Tasks.server, $property, $timeoutProp, '--console=plain')
        $outcome = Wait-For 'status/server.ready' ($StartTimeoutMinutes * 60) { Test-Path -LiteralPath (Join-Path $OutDir 'status\server.ready') } `
        { if ($server.HasExited) { "the server's gradle process ended (exit code $($server.ExitCode))" } }
        Add-Check 'run.server-started' 'The dedicated server started with the test mod' ($outcome -eq 'ok') 'status/server.ready within the time-out' $outcome
        if ($outcome -ne 'ok') { throw [System.OperationCanceledException]::new('server') }

        # ---- clients, one after the other (each Gradle run configures the project before the next starts)
        foreach ($player in 'Player1', 'Player2') {
            $proc = Start-GradleRun $player @($Tasks[$player], $property, $timeoutProp, '--console=plain')
            $outcome = Wait-For "status/$player.started" ($StartTimeoutMinutes * 60) { Test-Path -LiteralPath (Join-Path $OutDir "status\$player.started") } `
            { if ($proc.HasExited) { "the gradle process of $player ended (exit code $($proc.ExitCode))" } elseif ($server.HasExited) { 'the server ended' } }
            Add-Check "run.client-$player-started" "The client of $player started with the test mod" ($outcome -eq 'ok') "status/$player.started within the time-out" $outcome
            if ($outcome -ne 'ok') { throw [System.OperationCanceledException]::new($player) }
        }

        # ---- the scenarios
        $remaining = [int][Math]::Max(60, ($deadline - (Get-Date)).TotalSeconds)
        $outcome = Wait-For 'status/Player1.finished and status/Player2.finished' $remaining {
            (Test-Path -LiteralPath (Join-Path $OutDir 'status\Player1.finished')) -and (Test-Path -LiteralPath (Join-Path $OutDir 'status\Player2.finished'))
        } {
            foreach ($role in 'Player1', 'Player2') {
                if (-not (Test-Path -LiteralPath (Join-Path $OutDir "status\$role.finished")) -and $script:Started[$role].HasExited) { return "the client of $role ended before its driver finished" }
            }
            if ($server.HasExited) { return 'the server ended during the scenarios' }
        }
        $why = @('Player1', 'Player2' | ForEach-Object {
                $f = Join-Path $OutDir "status\$_.finished"
                "$_ : " + ((Test-Path -LiteralPath $f) ? ((Get-Content -LiteralPath $f -Encoding utf8 | Where-Object { $_ -notlike 'pid=*' -and $_ -notlike 'time=*' }) -join ' ') : 'not finished')
            }) -join '; '
        Add-Check 'run.drivers-finished' 'Both drivers finished within the time-out' ($outcome -eq 'ok') 'both status/<player>.finished' "$outcome; $why"
    }
} catch [System.OperationCanceledException] {
    Write-Log "stopped early: $($_.Exception.Message)"
} catch {
    Add-Entry 'run.script-error' 'The script itself' 'FAIL' 'no error' ("script error: " + $_.Exception.Message + ' at ' + $_.InvocationInfo.PositionMessage)
} finally {
    if (-not $DryRun -and $script:Started.Count -gt 0) {
        # ---- stop: the server by its own stop request, the clients quit by themselves; whatever is left is killed
        New-Item -ItemType Directory -Force -Path (Join-Path $OutDir 'control') | Out-Null
        Set-Content -LiteralPath (Join-Path $OutDir 'control\stop-server') -Value 'stop' -Encoding utf8
        $killed = @()
        foreach ($role in @($script:Started.Keys)) {
            $proc = $script:Started[$role]
            $limit = ($role -eq 'server') ? 120 : 60
            $result = Wait-For "$role to exit" $limit { $proc.HasExited -and -not (Test-GameAlive $role) }
            if ($result -ne 'ok') {
                $stopped = @(Stop-Started $role)
                $killed += "$role (pids $($stopped -join ', '))"
            }
        }
        Add-Check 'run.processes-stopped' 'Every process the script started ended by itself' ($killed.Count -eq 0) 'no process had to be killed' `
            (($killed.Count -eq 0) ? 'all ended by themselves' : ('had to kill: ' + ($killed -join '; ')))

        # ---- logs, the rejection warning, crash reports
        $logs = Join-Path $OutDir 'logs'
        New-Item -ItemType Directory -Force -Path $logs | Out-Null
        $serverLog = Join-Path $ServerDir 'logs\latest.log'
        if (Test-Path -LiteralPath $serverLog) { Copy-Item -LiteralPath $serverLog -Destination (Join-Path $logs 'server-latest.log') -Force }
        foreach ($player in $ClientDirs.Keys) {
            $clientLog = Join-Path $ClientDirs[$player] 'logs\latest.log'
            if (Test-Path -LiteralPath $clientLog) { Copy-Item -LiteralPath $clientLog -Destination (Join-Path $logs "$player-latest.log") -Force }
        }
        $copied = Join-Path $logs 'server-latest.log'
        $warning = (Test-Path -LiteralPath $copied) ? @(Select-String -LiteralPath $copied -SimpleMatch 'Rejected an admin menu request' | Where-Object { $_.Line.Contains('Player2') }) : @()
        Add-Check 'run.server-log-rejection-warning' "The server log has the warning about Player2's rejected admin request" ($warning.Count -gt 0) `
            'a WARN line "Rejected an admin menu request ... of Player2" in logs/server-latest.log' `
            (($warning.Count -gt 0) ? $warning[0].Line.Trim() : ((Test-Path -LiteralPath $copied) ? 'no such line' : 'no server log'))
        $crashes = @(Get-NewCrashReports)
        Add-Check 'run.no-crash-reports' 'No crash report was written during the run' ($crashes.Count -eq 0) 'no new crash report' `
            (($crashes.Count -eq 0) ? 'none' : ($crashes -join '; '))
    }
    if (-not $DryRun) {
        # every run step that did not get to report fails, so a run that stopped early is never green
        foreach ($id in $RunIds) {
            if (-not ($script:Entries | Where-Object id -EQ $id)) { Add-Entry $id '(run step)' 'FAIL' 'the run gets to this step' 'not reached (the run stopped earlier)' }
        }
        $report = Merge-Results $OutDir $script:Entries $manifestIds $false $StartedAt
        Write-Log "overall: $($report.overall) (PASS $($report.counts.PASS), FAIL $($report.counts.FAIL), SKIPPED $($report.counts.SKIPPED)); results.json and summary.md in $OutDir"
        $exitCode = ($report.counts.FAIL -gt 0) ? 1 : (($report.counts.SKIPPED -gt 0) ? 2 : 0)
    } elseif ($exitCode -eq 3) {
        $report = Merge-Results $OutDir $script:Entries $manifestIds $true $StartedAt
        Write-Log "dry run: $($report.overall)"
        $exitCode = 1
    }
}
exit $exitCode
