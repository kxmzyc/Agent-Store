param(
  [string]$BaseUrl = "http://localhost:8081",
  [string]$AdminUsername = "admin",
  [string]$AdminPassword = "123456",
  [string]$Password = "123456",
  [int]$Requests = 100,
  [int]$Stock = 80,
  [int]$Rounds = 3,
  [int]$TimeoutSeconds = 30
)

$scriptPath = Join-Path $PSScriptRoot "concurrent_order_test.py"
python $scriptPath `
  --base-url $BaseUrl `
  --admin-username $AdminUsername `
  --admin-password $AdminPassword `
  --password $Password `
  --requests $Requests `
  --stock $Stock `
  --rounds $Rounds `
  --timeout $TimeoutSeconds
exit $LASTEXITCODE
