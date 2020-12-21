Start-Process powershell {[console]::WindowWidth=30; [console]::WindowHeight=20; [console]::BufferWidth=[console]::WindowWidth; rmiregistry; Read-Host}
Start-Sleep -s 1
Start-Process powershell {[console]::WindowWidth=30; [console]::WindowHeight=20; [console]::BufferWidth=[console]::WindowWidth; java ChordNode lancashire; Read-Host}
Start-Sleep -s 2
Start-Process powershell {[console]::WindowWidth=30; [console]::WindowHeight=20; [console]::BufferWidth=[console]::WindowWidth; java ChordNode yorkshire lancashire; Read-Host}
Start-Process powershell {[console]::WindowWidth=30; [console]::WindowHeight=20; [console]::BufferWidth=[console]::WindowWidth; java ChordNode cumbria lancashire; Read-Host}
Start-Process powershell {[console]::WindowWidth=30; [console]::WindowHeight=20; [console]::BufferWidth=[console]::WindowWidth; java ChordNode chesire lancashire; Read-Host}
Start-Process powershell {[console]::WindowWidth=30; [console]::WindowHeight=20; [console]::BufferWidth=[console]::WindowWidth; java ChordNode shropshire lancashire; Read-Host}
Start-Sleep -s 10
java Client put alex lancashire
java Client put jamie lancashire
java Client put sam lancashire
java Client put john lancashire
java Client put dave lancashire
java Client put luca lancashire
java Client put stephen lancashire
java Client put robinson lancashire
java Client put celeste lancashire