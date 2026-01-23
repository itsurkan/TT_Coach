# View Logs from Android Device
# Швидкий перегляд логів без експорту

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$packageName = "com.ttcoachai"
$logPath = "/data/data/$packageName/files/logs"

Write-Host "=== AI Coach - View Logs ===" -ForegroundColor Cyan
Write-Host ""

function Show-LogDirectory {
    param($dir, $title)
    
    Write-Host "`n=== $title ===" -ForegroundColor Yellow
    
    $files = & $adb shell "run-as $packageName ls -lh $logPath/$dir/" 2>$null | Select-String -Pattern "\.jsonl"
    
    if ($files) {
        foreach ($file in $files) {
            Write-Host $file -ForegroundColor Gray
        }
        
        # Show latest file content
        $latestFile = & $adb shell "run-as $packageName ls -t $logPath/$dir/*.jsonl 2>/dev/null | head -1" 2>$null
        $latestFile = $latestFile.Trim()
        
        if ($latestFile) {
            Write-Host "`nLatest file: $latestFile" -ForegroundColor Cyan
            Write-Host "Content preview:" -ForegroundColor Cyan
            
            $content = & $adb exec-out "run-as $packageName cat $latestFile" 2>$null
            
            if ($content) {
                # Pretty print JSON (first 3 lines)
                $lines = $content -split "`n" | Select-Object -First 3
                foreach ($line in $lines) {
                    if ($line.Trim()) {
                        try {
                            $json = $line | ConvertFrom-Json
                            $json | ConvertTo-Json -Depth 10 | Write-Host -ForegroundColor Green
                            Write-Host ""
                        } catch {
                            Write-Host $line -ForegroundColor Gray
                        }
                    }
                }
                
                $totalLines = ($content -split "`n").Count
                if ($totalLines -gt 3) {
                    Write-Host "... and $($totalLines - 3) more lines" -ForegroundColor Gray
                }
            } else {
                Write-Host "  (empty)" -ForegroundColor Gray
            }
        }
    } else {
        Write-Host "  No .jsonl files found" -ForegroundColor Gray
    }
}

# Show all log directories
Show-LogDirectory "training_sessions" "Training Sessions"
Show-LogDirectory "performance_metrics" "Performance Metrics"  
Show-LogDirectory "errors" "Errors"
Show-LogDirectory "events" "Events"

# Storage stats
Write-Host "`n=== Storage Stats ===" -ForegroundColor Yellow
$storageInfo = & $adb shell "run-as $packageName du -sh $logPath" 2>$null
Write-Host $storageInfo -ForegroundColor Cyan

Write-Host "`n Done!" -ForegroundColor Green
Write-Host "To export all logs run: .\scripts\export_logs.ps1" -ForegroundColor Cyan
