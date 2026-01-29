# PowerShell script to fix RxJava imports
Get-ChildItem -Path 'c:\BulkSMS2\app\src\main\java' -Filter '*.java' -Recurse | ForEach-Object {
    $content = Get-Content $_ -Raw
    $original = $content
    $content = $content -replace 'import io\.reactivex\.;', 'import io.reactivex.rxjava3.core.'
    $content = $content -replace 'import io\.reactivex\.([^;]+);', 'import io.reactivex.rxjava3.$1'
    if ($content -ne $original) {
        Set-Content -Path $_ -Value $content -NoNewline
        Write-Host "Fixed: $_"
    }
}
