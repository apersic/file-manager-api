# Walks health, create, list, search, and delete against a running API.
# Usage: .\scripts\demo.ps1
# Override the base with API_BASE_URL.

$ErrorActionPreference = "Stop"
$base = if ($env:API_BASE_URL) { $env:API_BASE_URL.TrimEnd("/") } else { "http://localhost:8080" }

function Invoke-Api {
  param(
    [Parameter(Mandatory = $true)][string]$Method,
    [Parameter(Mandatory = $true)][string]$Path,
    [string]$Body,
    [Parameter(Mandatory = $true)][int]$Expect
  )
  $params = @{
    Method = $Method
    Uri = "$base$Path"
    Headers = @{ Accept = "application/json" }
    UseBasicParsing = $true
  }
  if ($Body) {
    $params.ContentType = "application/json; charset=utf-8"
    $params.Body = [System.Text.Encoding]::UTF8.GetBytes($Body)
  }
  $status = 0
  $payload = ""
  try {
    $response = Invoke-WebRequest @params
    $status = [int]$response.StatusCode
    $payload = [string]$response.Content
  } catch {
    $detail = $_.ErrorDetails.Message
    $resp = $_.Exception.Response
    if ($resp) {
      $status = [int]$resp.StatusCode
    }
    if ($detail) {
      $payload = $detail
    }
    if ($status -eq 0) {
      throw
    }
  }
  if ($status -ne $Expect) {
    throw "$Method $Path returned $status, expected $Expect. $payload"
  }
  if ([string]::IsNullOrWhiteSpace($payload)) {
    return $null
  }
  return $payload | ConvertFrom-Json
}

$health = Invoke-Api -Method GET -Path "/actuator/health" -Expect 200
if ($health.status -ne "UP") {
  throw "Health status is $($health.status)"
}

$root = Invoke-Api -Method GET -Path "/api/v1/nodes/root" -Expect 200
if ($root.name -ne "Home" -or $root.kind -ne "FOLDER") {
  throw "Root is $($root.kind) $($root.name)"
}

$token = "demo-" + [guid]::NewGuid().ToString("N")
$folder = Invoke-Api -Method POST -Path "/api/v1/nodes" -Expect 201 -Body (
  @{ parentId = $root.id; kind = "FOLDER"; name = $token } | ConvertTo-Json -Compress
)
$file = Invoke-Api -Method POST -Path "/api/v1/nodes" -Expect 201 -Body (
  @{ parentId = $folder.id; kind = "FILE"; name = "$token-notes" } | ConvertTo-Json -Compress
)

$list = Invoke-Api -Method GET -Path "/api/v1/nodes?parentId=$($folder.id)&limit=50" -Expect 200
$listed = @($list.items | Where-Object { $_.id -eq $file.id })
if ($listed.Count -ne 1) {
  throw "List did not return the new file"
}

$prefix = Invoke-Api -Method GET -Path "/api/v1/nodes/search?q=$([uri]::EscapeDataString($token))&match=PREFIX&parentId=$($root.id)&limit=10" -Expect 200
$prefixed = @($prefix.items | Where-Object { $_.id -eq $folder.id })
if ($prefixed.Count -ne 1) {
  throw "Prefix search did not return the new folder"
}

$exact = Invoke-Api -Method GET -Path "/api/v1/nodes/search?q=$([uri]::EscapeDataString("$token-notes"))&match=EXACT&parentId=$($folder.id)&limit=10" -Expect 200
$matched = @($exact.items | Where-Object { $_.id -eq $file.id -and $_.kind -eq "FILE" })
if ($matched.Count -ne 1) {
  throw "Exact search did not return the new file"
}

Invoke-Api -Method DELETE -Path "/api/v1/nodes/$($folder.id)" -Expect 204 | Out-Null
Invoke-Api -Method GET -Path "/api/v1/nodes/$($file.id)" -Expect 404 | Out-Null
Invoke-Api -Method DELETE -Path "/api/v1/nodes/$($file.id)" -Expect 404 | Out-Null
Invoke-Api -Method DELETE -Path "/api/v1/nodes/$($root.id)" -Expect 409 | Out-Null

Write-Output "demo passed against $base"
