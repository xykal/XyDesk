#requires -Version 5.1
# Default Start is the logged-in owner's desktop, matching the normal remote-host
# model. Pass -VirtualDisplay720p only for the explicit strict VDD path.
[CmdletBinding(SupportsShouldProcess=$true)]
param(
 [ValidateSet('Start','Stop','Status','Credentials','RevokeAccess','Fit')][string]$Action='Status',
 [switch]$VirtualDisplay720p,
 [string]$ConsoleUser=$env:USERNAME,
 [string]$HostDirectory=$PSScriptRoot
)
$ErrorActionPreference='Stop'
$admin=([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (!$admin) {throw 'Administrator PowerShell required. No automatic elevation.'}
$name=if ($VirtualDisplay720p) {'XyDesk-Virtual720-Console'} else {'XyDesk-UserHost'}
$engine=[IO.Path]::GetFullPath((Join-Path $HostDirectory 'xydesk-host.exe'))
# Upgrade cleanup: old uxhd8 used this task name for the lab-account workaround.
# Remove it only when its action points to this exact install and worker; never
# touch an unrelated task or process owned by another installation.
if (-not $VirtualDisplay720p) {
 $legacy=Get-ScheduledTask -TaskName 'XyDesk-Virtual720-Console' -ErrorAction SilentlyContinue
 if ($legacy) {
  $legacyArgs=(($legacy.Actions | Select-Object -First 1).Arguments)
  $sameWorker=$legacyArgs -and $legacyArgs -match [regex]::Escape($HostDirectory) -and $legacyArgs -match 'Console-Worker\.ps1'
  if ($sameWorker) {
   Disable-ScheduledTask -TaskName 'XyDesk-Virtual720-Console' -ErrorAction SilentlyContinue | Out-Null
   Stop-ScheduledTask -TaskName 'XyDesk-Virtual720-Console' -ErrorAction SilentlyContinue
   Unregister-ScheduledTask -TaskName 'XyDesk-Virtual720-Console' -Confirm:$false -ErrorAction SilentlyContinue
   Write-Host 'Legacy console task from this install removed; host will run as the selected Windows user.'
  }
 }
}
$account=New-Object Security.Principal.NTAccount("$env:COMPUTERNAME\$ConsoleUser")
$sid=$account.Translate([Security.Principal.SecurityIdentifier]).Value
$profile=Get-CimInstance Win32_UserProfile | Where-Object {$_.SID -eq $sid}
if (!$profile) {throw 'Console user profile not found. No account created or session moved.'}
$state=Join-Path $profile.LocalPath 'AppData\Local\XyDesk-RemoteCore-Test'
if (-not ('XyDeskConsoleSession' -as [type])) {
 Add-Type 'using System.Runtime.InteropServices; public static class XyDeskConsoleSession { [DllImport("kernel32.dll")] public static extern uint WTSGetActiveConsoleSessionId(); }'
}
$consoleSession=[XyDeskConsoleSession]::WTSGetActiveConsoleSessionId()
$requireConsoleSession=$VirtualDisplay720p
function Get-OwnedHost {
 @(Get-CimInstance Win32_Process -Filter "Name='xydesk-host.exe'" | Where-Object {
   if ($_.ExecutablePath -ine $engine) {return $false}
   if ($requireConsoleSession -and $_.SessionId -ne $consoleSession) {return $false}
   $owner=Invoke-CimMethod -InputObject $_ -MethodName GetOwnerSid
   $owner.ReturnValue -eq 0 -and $owner.Sid -eq $sid
 })
}
function Get-OwnedWorker {
 $powershell=[IO.Path]::GetFullPath("$env:WINDIR\System32\WindowsPowerShell\v1.0\powershell.exe")
 $hostPattern=[regex]::Escape($HostDirectory)
 @(Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" | Where-Object {
   if ($_.ExecutablePath -ine $powershell) {return $false}
   if (!$_.CommandLine -or $_.CommandLine -notmatch 'Console-Worker\.ps1' -or $_.CommandLine -notmatch $hostPattern) {return $false}
   $owner=Invoke-CimMethod -InputObject $_ -MethodName GetOwnerSid
   $owner.ReturnValue -eq 0 -and $owner.Sid -eq $sid
 })
}
$task=Get-ScheduledTask -TaskName $name -ErrorAction SilentlyContinue
if ($task) {
 $registeredSid=if ($task.Principal.UserId -match '^S-1-') {$task.Principal.UserId} else {(New-Object Security.Principal.NTAccount($task.Principal.UserId)).Translate([Security.Principal.SecurityIdentifier]).Value}
 if ($registeredSid -ne $sid) {throw 'Task belongs to another account. Refusing takeover.'}
 $arguments=($task.Actions | Select-Object -First 1).Arguments
 if ($arguments -match '-EncodedCommand\s+(\S+)') {
  try {$arguments=[Text.Encoding]::Unicode.GetString([Convert]::FromBase64String($Matches[1]))}catch{throw 'Unknown task command.'}
 }
 if ($arguments -notmatch 'Start-Virtual720\.ps1|Console-Worker\.ps1') {throw 'Unknown task action. Refusing takeover.'}
}
if ($Action -eq 'Stop') {
 if (!$PSCmdlet.ShouldProcess('Only the XyDesk console task and its owned engine','Stop (RDP remains connected)')) {return}
 $owned=Get-OwnedHost
 $workers=Get-OwnedWorker
 if ($task) {
  # Disable BEFORE stopping the action. Otherwise Task Scheduler's restart
  # policy races the manual Stop and launches the supervisor again.
  Disable-ScheduledTask -TaskName $name -ErrorAction SilentlyContinue | Out-Null
  Stop-ScheduledTask -TaskName $name -ErrorAction SilentlyContinue
 }
 Start-Sleep -Seconds 1
 # Stop the worker as well as xydesk-host.exe. The worker owns the child via a
 # kill-on-close Job Object; killing only the engine makes -Supervise respawn it.
 foreach ($p in $workers + $owned) {
  $now=Get-CimInstance Win32_Process -Filter "ProcessId=$($p.ProcessId)" -ErrorAction SilentlyContinue
  if ($now -and $now.CreationDate -eq $p.CreationDate) {Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue}
 }
 Start-Sleep -Seconds 1
 # A restart already queued before Disable can survive the first snapshot; do
 # one exact-path sweep, never a global taskkill.
 foreach ($p in (Get-OwnedHost)) {
  Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
 }
 Write-Host 'Console host stopped. Task disabled and supervisor/engine terminated. Identity, driver, Windows and RDP preserved.'
 return
}
if ($Action -eq 'Fit') {
 $fitScript=Join-Path $HostDirectory 'Console-Fit.ps1'
 if (!(Test-Path -LiteralPath $fitScript)) {throw 'Console-Fit.ps1 missing from install directory.'}
 $fitLog=Join-Path $state 'console-fit.log'
 $fitTask='XyDesk-Console-Fit'
 Unregister-ScheduledTask -TaskName $fitTask -Confirm:$false -ErrorAction SilentlyContinue
 # *>&1: Write-Host/Write-Warning dari Console-Fit.ps1 ada di stream lain,
 # tanpa redirect ini log fit selalu kosong dan pengguna tidak melihat hasil.
 $fitArgs='-NoProfile -ExecutionPolicy Bypass -Command "& ''FITSCRIPT'' *>&1 | Out-File -LiteralPath ''FITLOG'' -Encoding utf8"'
 $fitArgs=$fitArgs.Replace('FITSCRIPT',$fitScript).Replace('FITLOG',$fitLog)
 $fitAction=New-ScheduledTaskAction -Execute "$env:WINDIR\System32\WindowsPowerShell\v1.0\powershell.exe" -Argument $fitArgs
 $fitPrincipal=New-ScheduledTaskPrincipal -UserId "$env:COMPUTERNAME\$ConsoleUser" -LogonType Interactive
 Register-ScheduledTask -TaskName $fitTask -Action $fitAction -Principal $fitPrincipal -Description 'One-shot window fit into the streamed desktop' -Force | Out-Null
 Start-ScheduledTask -TaskName $fitTask
 Start-Sleep -Seconds 3
 if (Test-Path -LiteralPath $fitLog) {Get-Content -LiteralPath $fitLog -Tail 5} else {Write-Warning 'Log fit belum terbaca; tugas mungkin masih berjalan di sesi console.'}
 return
}
if ($Action -eq 'Credentials') {
 Write-Warning 'Private connection details. Do not send this output or a screenshot to chat.'
 Write-Host 'Device ID:'; Get-Content (Join-Path $state 'device_id')
 Write-Host 'Password:'; Get-Content (Join-Path $state 'password')
 return
}
if ($Action -eq 'RevokeAccess') {
 if (!$PSCmdlet.ShouldProcess('All remembered browsers for this console identity','Revoke access')) {return}
 $old=$env:XYDESK_HOME
 try {$env:XYDESK_HOME=$state; & $engine --revoke-remembered; if ($LASTEXITCODE -ne 0){throw 'Revoke failed.'}}
 finally {$env:XYDESK_HOME=$old}
 return
}
if ($Action -eq 'Start') {
 if (!$PSCmdlet.ShouldProcess("Existing logged-in $ConsoleUser console",'Start Virtual720 without RDP disconnect')) {return}
 & (Join-Path $HostDirectory 'Start-TestHost.ps1') -CheckOnly
 $owned=Get-OwnedHost
 if ($owned.Count -gt 0 -or ($task -and $task.State -eq 'Running')) {
  # Bukan kegagalan: host memang sudah berjalan. Tampilkan status, jangan
  # melempar error merah yang membingungkan pengguna panel.
  Write-Host 'Host sudah berjalan; tidak ada duplikat yang dibuat. Gunakan Stop dahulu bila ingin memulai ulang.'
 } else {
 $worker=Join-Path $HostDirectory 'Console-Worker.ps1'
 if (!(Test-Path -LiteralPath $worker)) {throw 'Console worker missing.'}
 $workerArgs='-NoProfile -NonInteractive -WindowStyle Hidden -File "'+$worker+'"'
 if ($VirtualDisplay720p) {$workerArgs+=' -VirtualDisplay720p'}
 $actionSpec=New-ScheduledTaskAction -Execute "$env:WINDIR\System32\WindowsPowerShell\v1.0\powershell.exe" -Argument $workerArgs -WorkingDirectory $HostDirectory
 $principal=New-ScheduledTaskPrincipal -UserId "$env:COMPUTERNAME\$ConsoleUser" -LogonType Interactive -RunLevel Highest
 $settings=New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1)
 Register-ScheduledTask -TaskName $name -Action $actionSpec -Principal $principal -Settings $settings -Description (if ($VirtualDisplay720p) {'XyDesk strict Virtual720 console host'} else {'XyDesk host for the logged-in Windows user'}) -Force | Out-Null
 Start-ScheduledTask -TaskName $name
 Start-Sleep -Seconds 8
 }
}
Get-ScheduledTask -TaskName $name -ErrorAction SilentlyContinue | Format-List TaskName,State
Get-ScheduledTaskInfo -TaskName $name -ErrorAction SilentlyContinue | Format-List LastRunTime,LastTaskResult
Get-OwnedHost | Format-Table Name,ProcessId,SessionId -AutoSize
$log=Join-Path $state 'console-status.log'
if (Test-Path $log) {Get-Content -LiteralPath $log -Tail 15}
Write-Host 'Running is process status, not proof of signaling or streaming. Status log omits credentials.'
