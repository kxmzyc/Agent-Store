param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$Username = "alice",
  [string]$Password = "123456",
  [int]$ProductId = 21,
  [int]$Requests = 100
)

$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" -ContentType "application/json" -Body (@{
  username = $Username
  password = $Password
} | ConvertTo-Json)

$headers = @{ Authorization = "Bearer $($login.accessToken)" }
$jobs = 1..$Requests | ForEach-Object {
  Start-Job -ScriptBlock {
    param($BaseUrl, $Headers, $ProductId)
    try {
      Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/orders/direct" -Headers $Headers -ContentType "application/json" -Body (@{
        productId = $ProductId
        quantity = 1
        shippingAddress = "并发测试地址"
      } | ConvertTo-Json)
      "success"
    } catch {
      "failed"
    }
  } -ArgumentList $BaseUrl, $headers, $ProductId
}

$results = $jobs | Receive-Job -Wait -AutoRemoveJob
$success = ($results | Where-Object { $_ -eq "success" }).Count
$failed = ($results | Where-Object { $_ -eq "failed" }).Count
"success=$success failed=$failed"
