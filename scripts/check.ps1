[CmdletBinding()]
param([switch]$E2E)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot

function Require([string]$command) {
  if(-not (Get-Command $command -ErrorAction SilentlyContinue)){ throw "Required command not found: $command" }
}

Require node
Require npm
if(Get-Command mvn -ErrorAction SilentlyContinue){
  Push-Location "$root\backend"; try { mvn -B -ntp verify } finally { Pop-Location }
} else { Write-Warning 'Maven not found; backend checks skipped (JDK 21 + Maven required).' }

foreach($app in @('user-web','admin-web')){
  Push-Location "$root\apps\$app"
  try {
    npm ci
    npm run lint
    npm test
    npm run build
    if($E2E){ npm run test:e2e }
  } finally { Pop-Location }
}

if(Get-Command py -ErrorAction SilentlyContinue){
  Push-Location "$root\migration"
  try { py -3 -m unittest discover -s tests -v } finally { Pop-Location }
} else { Write-Warning 'Python launcher not found; migration unit tests skipped (Python 3 required).' }
