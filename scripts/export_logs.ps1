# Export Logs from Android Device
# Скрипт для експорту логів з Android пристрою на комп'ютер

$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$exportDir = "d:\Desktop\TT_Coach_AI\logs_export_$timestamp"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$packageName = "com.ttcoachai"
$logPath = "/data/data/$packageName/files/logs"

Write-Host "=== AI Coach - Export Logs ===" -ForegroundColor Cyan
Write-Host ""

# Create export directory
New-Item -ItemType Directory -Path $exportDir -Force | Out-Null
Write-Host "✓ Created export directory: $exportDir" -ForegroundColor Green

# Export all log directories
$logDirs = @(
    "training_sessions",
    "performance_metrics", 
    "errors",
    "events",
    "raw_poses"
)

foreach ($dir in $logDirs) {
    Write-Host "`nExporting $dir..." -ForegroundColor Yellow
    
    $localDir = Join-Path $exportDir $dir
    New-Item -ItemType Directory -Path $localDir -Force | Out-Null
    
    # Get list of files
    $files = & $adb shell "run-as $packageName ls $logPath/$dir/" 2>$null
    
    if ($LASTEXITCODE -eq 0) {
        foreach ($file in $files) {
            $file = $file.Trim()
            if ($file -and $file -ne "." -and $file -ne "..") {
                Write-Host "  - $file"
                
                $remotePath = "$logPath/$dir/$file"
                $localPath = Join-Path $localDir $file
                
                # Pull file using exec-out for binary safety
                & $adb exec-out "run-as $packageName cat $remotePath" > $localPath 2>$null
                
                if ($LASTEXITCODE -eq 0) {
                    $size = (Get-Item $localPath).Length
                    Write-Host "    ✓ $size bytes" -ForegroundColor Green
                } else {
                    Write-Host "    ✗ Failed" -ForegroundColor Red
                }
            }
        }
    } else {
        Write-Host "  No files found or directory doesn't exist" -ForegroundColor Gray
    }
}

Write-Host "`n=== Summary ===" -ForegroundColor Cyan
Write-Host "Logs exported to: $exportDir" -ForegroundColor Green
Write-Host ""

# Count files and show structure
$fileCount = (Get-ChildItem -Path $exportDir -Recurse -File).Count
Write-Host "Total files: $fileCount" -ForegroundColor Cyan

# Show directory tree
Write-Host "`nDirectory structure:" -ForegroundColor Yellow
Get-ChildItem -Path $exportDir -Recurse | ForEach-Object {
    $indent = "  " * ($_.FullName.Split('\').Count - $exportDir.Split('\').Count - 1)
    if ($_.PSIsContainer) {
        Write-Host "$indent$($_.Name)/" -ForegroundColor Blue
    } else {
        $size = "{0:N0}" -f $_.Length
        Write-Host "$indent$($_.Name) ($size bytes)" -ForegroundColor Gray
    }
}

Write-Host "`n✓ Done!" -ForegroundColor Green
Write-Host "Open exported logs with: code $exportDir" -ForegroundColor Cyan
