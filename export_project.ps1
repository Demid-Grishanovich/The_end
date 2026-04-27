# Настройки
$root = "C:\Warn\Diplom\datacrowd-lab"
$outputDir = "C:\Warn\Diplom\_export"
$maxFileSizeKB = 500

$allowedExtensions = @(".java", ".kt",".html",".css",".conf", ".go", ".sql", ".yml", ".yaml", ".xml", ".md", ".json", ".properties", ".env", ".dockerfile")
$specialFiles = @("Dockerfile", "docker-compose.yml", "Makefile", ".env", "go.mod", "go.sum", "pom.xml")
$excludeFolders = @("target", ".git", "node_modules", ".idea", "__pycache__", ".gradle", "build", ".mvn")

New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

$allFiles = Get-ChildItem -Path $root -Recurse -File | Where-Object {
    $path = $_.FullName
    $excluded = $false
    foreach ($f in $excludeFolders) {
        if ($path -like "*\$f\*") { $excluded = $true; break }
    }
    ($allowedExtensions -contains $_.Extension -or $specialFiles -contains $_.Name) -and -not $excluded
}

$partIndex = 1
$currentSize = 0
$currentContent = [System.Text.StringBuilder]::new()

foreach ($file in $allFiles) {
    $relative = $file.FullName.Replace($root + "\", "")
    $header = "`n`n========================================`nFile: $relative`n========================================`n"
    $body = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue

    $chunk = $header + $body
    $chunkKB = [System.Text.Encoding]::UTF8.GetByteCount($chunk) / 1024

    if ($currentSize + $chunkKB -gt $maxFileSizeKB -and $currentSize -gt 0) {
        $outFile = Join-Path $outputDir "project_part$partIndex.txt"
        [System.IO.File]::WriteAllText($outFile, $currentContent.ToString(), [System.Text.Encoding]::UTF8)
        Write-Host "Saved: $outFile ($([math]::Round($currentSize))KB)"
        $partIndex++
        $currentContent.Clear() | Out-Null
        $currentSize = 0
    }

    $currentContent.Append($chunk) | Out-Null
    $currentSize += $chunkKB
}

if ($currentSize -gt 0) {
    $outFile = Join-Path $outputDir "project_part$partIndex.txt"
    [System.IO.File]::WriteAllText($outFile, $currentContent.ToString(), [System.Text.Encoding]::UTF8)
    Write-Host "Saved: $outFile ($([math]::Round($currentSize))KB)"
}

Write-Host "`nDone! $partIndex file(s) in $outputDir"