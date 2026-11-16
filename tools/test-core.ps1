param([Parameter(Mandatory=$true)][string]$JsonJar)
$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$jar=(Resolve-Path -LiteralPath $JsonJar).Path
$expected='3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'
if((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected){throw 'Expected unmodified JSON-java 20240303 from Maven Central'}
$compiler=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/javac.exe'}else{'javac'}
$runtime=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/java.exe'}else{'java'}
$output='state/core-tests/classes'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$tests=@('FileCipherTest','BridgeEndpointTest','PrivateRecordsTest','TaskDescriptionTest','AssistantIntentTest','AssistantRouterTest','AssistantFastRouterTest','AssistantEarlyRouteTest','ProviderCatalogTest','DirectApiTest','LocalFilesTest','DirectInputsTest','DirectWebTest','MathSyntaxTest','SpeechTextTest','SpeechRetryTest','VolcSpeechTest')
$files=$tests | ForEach-Object { 'test/java/com/codexpocket/app/'+$_+'.java' }
$files+='test/support/com/codexpocket/app/SecretStore.java'
$files+='test/support/com/codexpocket/app/CoreTestRunner.java'
& $compiler -encoding UTF-8 -cp $jar -sourcepath app/src/main/java -d $output @files
if($LASTEXITCODE -ne 0){throw 'Core regression compilation failed'}
foreach($name in $tests){
    & $runtime -cp ($output+[IO.Path]::PathSeparator+$jar) 'com.codexpocket.app.CoreTestRunner' $name
    if($LASTEXITCODE -ne 0){throw ('Failed '+$name)}
}
Write-Host ('Passed '+$tests.Count+' JVM regression programs. No paid API calls or real personal data used.')
