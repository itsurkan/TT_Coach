# Quick Export Logs Script
# Simple one-liner to export all logs

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$pkg = "com.google.mediapipe.examples.poselandmarker"
$exportDir = "d:\Desktop\TT_Coach_AI\logs_export"

Write-Host "Exporting logs..." -ForegroundColor Cyan

New-Item -ItemType Directory -Path $exportDir -Force | Out-Null

# Export each file
& $adb exec-out "run-as $pkg cat /data/data/$pkg/files/logs/training_sessions/2026-01-03_sessions.jsonl" 2>$null > "$exportDir\sessions.jsonl"
& $adb exec-out "run-as $pkg cat /data/data/$pkg/files/logs/events/2026-01-03_events.jsonl" 2>$null > "$exportDir\events.jsonl"

# Try to get strokes if exists
& $adb exec-out "run-as $pkg cat /data/data/$pkg/files/logs/training_sessions/2026-01-03_strokes.jsonl" 2>$null > "$exportDir\strokes.jsonl"

Write-Host "Done! Logs exported to: $exportDir" -ForegroundColor Green
Write-Host ""
Get-ChildItem $exportDir | ForEach-Object {
    $size = "{0:N0}" -f $_.Length
    Write-Host "  $($_.Name) - $size bytes" -ForegroundColor Gray
}
