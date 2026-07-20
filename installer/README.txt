==========================================
  Ascoder - 团队代码理解平台
==========================================

快速安装（需要管理员/管理员权限）:

  macOS / Linux:
    chmod +x install.sh
    sudo ./install.sh

  Windows (以管理员身份运行 PowerShell):
    Set-ExecutionPolicy Bypass -Scope Process
    .\install.ps1

安装完成后访问: http://localhost:5173

启停命令:
  Unix:   bin/start.sh / bin/stop.sh
  Windows: bin\start.ps1 / bin\stop.ps1

卸载:
  Unix:    sudo ./uninstall.sh
  Windows: .\uninstall.ps1

配置文件: .env
日志目录: run/

更多信息: https://github.com/welsione/ascoder
