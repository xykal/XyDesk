#requires -Version 5.1
# Panel kontrol GUI sementara: bungkus klik-klik untuk Console-Virtual720.ps1.
# Tidak menggantikan manajer; semua aksi tetap lewat jalur scheduled task yang
# sama (Start/Stop/Status/Fit/Credentials). Panel ini hanya antarmuka.
# Menjalankan dirinya sebagai Administrator lewat satu prompt UAC karena
# manajer menolak dijalankan tanpa admin (kebijakan tanpa elevasi otomatis
# di dalam manajer itu sendiri).
param(
 [string]$ConsoleUser='runneradmin'
)
$ErrorActionPreference='Stop'
$admin=([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (!$admin) {
 $self=$PSCommandPath
 if (!$self) {$self=$MyInvocation.MyCommand.Path}
 Start-Process "$env:WINDIR\System32\WindowsPowerShell\v1.0\powershell.exe" -Verb RunAs -ArgumentList @('-sta','-NoLogo','-NoProfile','-WindowStyle','Hidden','-ExecutionPolicy','RemoteSigned','-File',('"'+$self+'"'),'-ConsoleUser',$ConsoleUser)
 return
}
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

$manager=Join-Path $PSScriptRoot 'Console-Virtual720.ps1'
if (!(Test-Path -LiteralPath $manager)) {
 [System.Windows.Forms.MessageBox]::Show('Console-Virtual720.ps1 tidak ditemukan di folder instalasi. Jalankan panel ini dari folder instalasi XyDesk Host Test.','XyDesk Control') | Out-Null
 return
}

$form=New-Object System.Windows.Forms.Form
$form.Text='XyDesk Control (paket uji)'
$form.ClientSize=New-Object System.Drawing.Size(760,600)
$form.MinimumSize=$form.ClientSize
$form.StartPosition='CenterScreen'

$dot=New-Object System.Windows.Forms.Panel
$dot.Size=New-Object System.Drawing.Size(18,18)
$dot.Location=New-Object System.Drawing.Point(14,16)
$dot.BackColor=[System.Drawing.Color]::Gray
$form.Controls.Add($dot)

$statusLabel=New-Object System.Windows.Forms.Label
$statusLabel.Location=New-Object System.Drawing.Point(40,15)
$statusLabel.AutoSize=$true
$statusLabel.Text='Host: belum diperiksa'
$form.Controls.Add($statusLabel)

$userLabel=New-Object System.Windows.Forms.Label
$userLabel.Location=New-Object System.Drawing.Point(420,16)
$userLabel.AutoSize=$true
$userLabel.Text='Akun console:'
$form.Controls.Add($userLabel)

$userBox=New-Object System.Windows.Forms.TextBox
$userBox.Location=New-Object System.Drawing.Point(510,13)
$userBox.Width=160
$userBox.Text=$ConsoleUser
$form.Controls.Add($userBox)

$buttons=New-Object System.Windows.Forms.FlowLayoutPanel
$buttons.Location=New-Object System.Drawing.Point(12,44)
$buttons.Size=New-Object System.Drawing.Size(736,42)
$buttons.FlowDirection='LeftToRight'
$buttons.WrapContents=$false
$form.Controls.Add($buttons)

$autoChk=New-Object System.Windows.Forms.CheckBox
$autoChk.Location=New-Object System.Drawing.Point(16,92)
$autoChk.AutoSize=$true
$autoChk.Text='Auto-refresh status tiap 15 detik'
$form.Controls.Add($autoChk)

$logBox=New-Object System.Windows.Forms.TextBox
$logBox.Multiline=$true
$logBox.ReadOnly=$true
$logBox.ScrollBars='Vertical'
$logBox.Font=New-Object System.Drawing.Font('Consolas',9)
$logBox.Location=New-Object System.Drawing.Point(12,118)
$logBox.Size=New-Object System.Drawing.Size(736,440)
$logBox.Anchor='Top,Bottom,Left,Right'
$form.Controls.Add($logBox)

function Write-Log([string]$text) {
 $stamp=Get-Date -Format 'HH:mm:ss'
 $logBox.AppendText("[$stamp] $text"+[Environment]::NewLine)
}
function Write-LogBlock([string]$title,[string]$text) {
 $logBox.AppendText('')
 $logBox.AppendText("=== $title ==="+[Environment]::NewLine)
 if ($text) {$logBox.AppendText(($text.TrimEnd()+[Environment]::NewLine))}
}
function Set-Indicator([string]$output) {
 if ($output -match 'State\s*:\s*Running') {
  $dot.BackColor=[System.Drawing.Color]::LimeGreen
  $statusLabel.Text='Host: Running (proses; bukan bukti streaming)'
 } elseif ($output -match 'State\s*:\s*(\S+)') {
  $dot.BackColor=[System.Drawing.Color]::Orange
  $statusLabel.Text='Host: '+$Matches[1]
 } elseif ($output -match 'already running') {
  $dot.BackColor=[System.Drawing.Color]::LimeGreen
  $statusLabel.Text='Host: Running (proses; bukan bukti streaming)'
 } else {
  $dot.BackColor=[System.Drawing.Color]::Gray
  $statusLabel.Text='Host: tidak terdaftar / tidak diketahui'
 }
}
function Get-StateDir([string]$user) {
 $account=New-Object Security.Principal.NTAccount("$env:COMPUTERNAME\$user")
 $sid=$account.Translate([Security.Principal.SecurityIdentifier]).Value
 $profile=Get-CimInstance Win32_UserProfile | Where-Object {$_.SID -eq $sid} | Select-Object -First 1
 if (!$profile) {return $null}
 return (Join-Path $profile.LocalPath 'AppData\Local\XyDesk-RemoteCore-Test')
}
$script:busy=$false
function Invoke-Action([string]$action,[bool]$quiet=$false) {
 if ($script:busy) {return}
 $script:busy=$true
 $user=$userBox.Text.Trim()
 if (!$user) {$user='runneradmin'}
 try {
  $form.Cursor=[System.Windows.Forms.Cursors]::WaitCursor
  if (!$quiet) {Write-Log "Menjalankan $action ..."}
  $statusLabel.Text='Bekerja ...'
  $form.Refresh()
  $tmp=Join-Path $env:TEMP ('xydesk-panel-'+[guid]::NewGuid().ToString('N'))
  $outFile="$tmp-out.txt";$errFile="$tmp-err.txt"
  $mgrEsc=$manager.Replace("'","''")
  $userEsc=$user.Replace("'","''")
  $inner="& '$mgrEsc' -Action $action -ConsoleUser '$userEsc' *>&1 | Out-String -Width 220"
  $p=Start-Process "$env:WINDIR\System32\WindowsPowerShell\v1.0\powershell.exe" -ArgumentList @('-NoLogo','-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-Command',$inner) -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -Wait -PassThru -RedirectStandardOutput $outFile -RedirectStandardError $errFile
  $out='';if (Test-Path $outFile) {$out=Get-Content $outFile -Raw}
  $err='';if (Test-Path $errFile) {$err=Get-Content $errFile -Raw}
  Remove-Item "$tmp-*" -ErrorAction SilentlyContinue
  if ($err) {$out=($out+[Environment]::NewLine+$err)}
  if (!$out.Trim()) {$out='(aksi selesai tanpa output; exit code '+$p.ExitCode+')'}
  Write-LogBlock $action $out
  Set-Indicator $out
 } catch {
  Write-LogBlock $action ("ERROR: "+$_.Exception.Message)
 } finally {
  $form.Cursor=[System.Windows.Forms.Cursors]::Default
  $script:busy=$false
 }
}
function Show-StatusLog {
 $user=$userBox.Text.Trim();if (!$user) {$user='runneradmin'}
 try {
  $state=Get-StateDir $user
  if (!$state) {Write-LogBlock 'Log' "Profil akun console '$user' tidak ditemukan."}
  $found=$false
  foreach ($name in @('console-status.log','console-fit.log')) {
   $path=Join-Path $state $name
   if (Test-Path -LiteralPath $path) {
    $found=$true
    $tail=Get-Content -LiteralPath $path -Tail 40
    Write-LogBlock $name ($tail -join [Environment]::NewLine)
   }
  }
  if (!$found) {Write-LogBlock 'Log' "Belum ada log di $state. Jalankan Start terlebih dahulu."}
 } catch {
  Write-LogBlock 'Log' ("ERROR: "+$_.Exception.Message)
 }
}

function New-Button([string]$text,[scriptblock]$handler) {
 $b=New-Object System.Windows.Forms.Button
 $b.Text=$text
 $b.AutoSize=$true
 $b.AutoSizeMode='GrowAndShrink'
 $b.Padding=New-Object System.Windows.Forms.Padding(6,2,6,2)
 $click={ $form.Activate(); & $handler }.GetNewClosure()
 $b.Add_Click($click)
 $buttons.Controls.Add($b)
 return $b
}
$btnStart=New-Button 'Start Host' { Invoke-Action 'Start' }
$btnStop=New-Button 'Stop Host' { Invoke-Action 'Stop' }
$btnStatus=New-Button 'Refresh Status' { Invoke-Action 'Status' }
$btnFit=New-Button 'Fit Jendela' { Invoke-Action 'Fit' }
$btnCred=New-Button 'Lihat Kredensial' {
 $yes=[System.Windows.Forms.MessageBox]::Show('Kredensial pairing akan tampil di panel ini. Jangan kirim tangkapan layar panel ke chat/apapun. Lanjutkan?','XyDesk Control',[System.Windows.Forms.MessageBoxButtons]::OKCancel,[System.Windows.Forms.MessageBoxIcon]::Warning)
 if ($yes -eq [System.Windows.Forms.DialogResult]::OK) { Invoke-Action 'Credentials' }
}
$btnLog=New-Button 'Lihat Log' { Show-StatusLog }
$btnExit=New-Button 'Tutup' { $form.Close() }

$timer=New-Object System.Windows.Forms.Timer
$timer.Interval=15000
$timer.Add_Tick({ Invoke-Action 'Status' $true })
$autoChk.Add_CheckedChanged({ if ($autoChk.Checked) { Invoke-Action 'Status' $true; $timer.Start() } else { $timer.Stop() } })

$form.Add_Shown({ Write-Log 'Panel siap. Semua aksi berjalan lewat Console-Virtual720.ps1 (jalur scheduled task yang sama seperti manual).'; Invoke-Action 'Status' $true })
[System.Windows.Forms.Application]::Run($form)
