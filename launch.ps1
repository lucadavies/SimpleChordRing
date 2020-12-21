#Starts the rmiregistry, RestKit server and multiple nodes. Each process is started in its own window which is first resized to easily fit on screen.

Start-Process powershell {[console]::WindowWidth=50; [console]::WindowHeight=20; [console]::BufferWidth=[console]::WindowWidth; java RestKit; Read-Host}
Start-Process powershell {[console]::WindowWidth=30; [console]::WindowHeight=2; [console]::BufferWidth=[console]::WindowWidth; Write-Host "RMI Registry running..."; rmiregistry; Read-Host}
Start-Sleep -s 1
Start-Process powershell {[console]::WindowWidth=30; [console]::WindowHeight=20; [console]::BufferWidth=[console]::WindowWidth; java ChordNode lancashire; Read-Host}
Start-Sleep -s 2
Start-Process powershell {[console]::WindowWidth=30; [console]::WindowHeight=20; [console]::BufferWidth=[console]::WindowWidth; java ChordNode yorkshire lancashire; Read-Host}
Start-Process powershell {[console]::WindowWidth=30; [console]::WindowHeight=20; [console]::BufferWidth=[console]::WindowWidth; java ChordNode cumbria lancashire; Read-Host}
Start-Process powershell {[console]::WindowWidth=30; [console]::WindowHeight=20; [console]::BufferWidth=[console]::WindowWidth; java ChordNode chesire lancashire; Read-Host}
Start-Process powershell {[console]::WindowWidth=30; [console]::WindowHeight=20; [console]::BufferWidth=[console]::WindowWidth; java ChordNode shropshire lancashire; Read-Host}
Start-Process powershell {[console]::WindowWidth=30; [console]::WindowHeight=20; [console]::BufferWidth=[console]::WindowWidth; java ChordNode powys lancashire; Read-Host}
Start-Process powershell {[console]::WindowWidth=30; [console]::WindowHeight=20; [console]::BufferWidth=[console]::WindowWidth; java ChordNode gwenedd lancashire; Read-Host}
Start-Process powershell {[console]::WindowWidth=30; [console]::WindowHeight=20; [console]::BufferWidth=[console]::WindowWidth; java ChordNode cornwall lancashire; Read-Host}
Start-Process powershell {[console]::WindowWidth=30; [console]::WindowHeight=20; [console]::BufferWidth=[console]::WindowWidth; java ChordNode devon lancashire; Read-Host}
Start-Process powershell {[console]::WindowWidth=30; [console]::WindowHeight=20; [console]::BufferWidth=[console]::WindowWidth; java ChordNode somerset lancashire; Read-Host}