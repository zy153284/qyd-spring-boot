[CmdletBinding()]
param([switch]$Build=$true)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$envFile="$root\deploy\.env"
if(-not (Get-Command docker -ErrorAction SilentlyContinue)){ throw 'Docker Desktop / docker CLI is required.' }
if(-not (Test-Path $envFile)){ throw 'Copy deploy/.env.example to deploy/.env and replace every CHANGE_ME value.' }
if(Select-String -Path $envFile -SimpleMatch 'CHANGE_ME' -Quiet){ throw 'deploy/.env still contains CHANGE_ME placeholders.' }
$args=@('compose','--env-file',$envFile,'-f',"$root\deploy\docker-compose.yml",'up','-d')
if($Build){$args+='--build'}
& docker @args
if($LASTEXITCODE -ne 0){throw "docker compose failed with exit code $LASTEXITCODE"}
& docker compose --env-file $envFile -f "$root\deploy\docker-compose.yml" ps
