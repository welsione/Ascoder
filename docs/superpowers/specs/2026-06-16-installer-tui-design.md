# Ascoder 安装引导 TUI + 自动打包工具 设计文档

> 日期：2026-06-16
> 状态：已确认

## 目标

为 Ascoder 构建跨平台安装引导 TUI 和自动打包工具，实现"解压即安装"的产品化体验。

- **安装引导 TUI**：Bash（macOS/Linux）+ PowerShell（Windows），零外部依赖，线性交互流程
- **自动打包工具**：一个 `scripts/package.sh`，构建 JAR + 前端 + 收集 CodeGraph 二进制，输出通用 zip 包

## zip 包结构

```
ascoder/
├── install.sh              # macOS/Linux 安装引导 TUI
├── install.ps1             # Windows 安装引导 TUI
├── uninstall.sh            # macOS/Linux 卸载
├── uninstall.ps1           # Windows 卸载
├── ascoder.jar             # 后端预构建 JAR
├── frontend/               # 前端预构建静态资源
│   └── dist/
├── bin/
│   ├── start.sh            # 启停脚本 (Unix)
│   ├── stop.sh
│   ├── start.ps1           # 启停脚本 (Windows)
│   └── stop.ps1
├── lib/
│   └── codegraph/          # CodeGraph CLI 预置二进制
│       ├── codegraph-linux-amd64
│       ├── codegraph-darwin-amd64
│       ├── codegraph-darwin-arm64
│       └── codegraph-windows-amd64.exe
├── conf/
│   └── application.yml     # Spring Boot 配置模板
└── README.txt              # 快速上手说明
```

## TUI 安装流程

install.sh / install.ps1 线性六步流程：

### [1/6] 欢迎信息 & 确认

- 显示 Ascoder 简介、版本号
- 询问用户是否继续安装

### [2/6] 选择安装目录

- 默认路径：Linux/macOS `/opt/ascoder`，Windows `C:\Ascoder`
- 用户可输入自定义路径
- 检测路径是否存在，确认覆盖或新建
- 将 zip 包内容复制到安装目录

### [3/6] 环境检测 & 依赖安装

逐项检测，显示状态标记（✓ 已安装 / ⬇ 正在安装 / ✗ 安装失败）：

| 依赖 | 检测方式 | 自动安装策略 |
|------|----------|-------------|
| Java 17+ | `java -version` | Linux: `apt install` / `yum install`；macOS: `brew install`（无 brew 则提示手动安装）；Windows: `winget install` 或下载 MSI |
| Git | `git --version` | Linux: `apt install` / `yum install`；macOS: `brew install` 或 Xcode CLI tools；Windows: `winget install` 或下载安装包 |
| CodeGraph CLI | `codegraph --version` | 从 `lib/codegraph/` 复制对应平台二进制到安装目录 `bin/`，并添加到 PATH |

- 安装失败时提示手动安装步骤，用户完成后按回车继续检测
- CodeGraph 失败不阻塞安装（可后续补装）

### [4/6] 数据库配置

- 仅支持远程 MySQL 连接（不自动安装本地 MySQL）
- 收集信息：

| 配置项 | 默认值 |
|--------|--------|
| 主机 | 127.0.0.1 |
| 端口 | 3306 |
| 用户名 | （必填） |
| 密码 | （必填） |
| 数据库名 | ascoder |

- 测试连接：`mysql -h <host> -P <port> -u <user> -p<password> -e "SELECT 1"`
- 连接失败则提示错误信息，允许重试
- 必须连接成功才能继续

### [5/6] LLM 配置

- 选择 Provider：MiniMax / Anthropic / OpenAI
- 输入 API Key（必填）
- 输入 Model ID（根据 Provider 给默认值：MiniMax → MiniMax-M2.7）
- 输入 Base URL（根据 Provider 给默认值）

### [6/6] 生成配置 & 注册服务

1. 写入 `.env` 配置文件（所有收集的配置项）
2. 写入 `conf/application.yml` 覆盖模板
3. 注册系统服务（见下方服务管理章节）
4. 同时生成 `bin/` 下的启停脚本
5. 启动服务
6. 打印访问地址：`http://localhost:5173`

## 启停与服务管理

### 启停脚本

**Unix (bin/start.sh, bin/stop.sh)**：
- `start.sh`：后台启动 JAR（`java -jar ascoder.jar`），PID 写入 `run/ascoder.pid`
- `stop.sh`：读取 PID 发送 SIGTERM，等待优雅关闭

**Windows (bin/start.ps1, bin/stop.ps1)**：
- `start.ps1`：`Start-Process java -ArgumentList '-jar ascoder.jar'`
- `stop.ps1`：`Stop-Process -Name java`

### 系统服务注册

| 平台 | 方式 | 产物 |
|------|------|------|
| Linux | systemd unit | `/etc/systemd/system/ascoder.service` |
| macOS | launchd plist | `~/Library/LaunchAgents/com.ascoder.service.plist` |
| Windows | sc.exe | Windows 服务 `Ascoder` |

- 注册失败时降级，只保留启停脚本，打印提示
- 服务配置中设置环境变量指向 `.env` 文件

### 卸载

- `uninstall.sh` / `uninstall.ps1`
- 停止服务 → 注销服务 → 删除安装目录

## 自动打包工具

`scripts/package.sh`，在开发机上执行：

```
[1] 检测构建工具 (Maven, npm)
[2] 构建 JAR: cd backend && ./mvnw package -DskipTests
[3] 构建前端: cd frontend/web && npm install && npm run build
[4] 收集 CodeGraph CLI 二进制（需预下载到 lib/codegraph/）
[5] 组装临时目录（按 zip 包结构布局）
[6] 打包: zip -r ascoder-<version>.zip ascoder/
[7] 清理临时文件
```

输出：一个通用 zip 包（包含所有平台的 CodeGraph 二进制和双平台安装脚本），约 200-400MB。

版本号从 `backend/pom.xml` 的 `<version>` 读取，zip 文件名为 `ascoder-v1.0.0.zip`。

## 约束与边界

- **TUI 零外部依赖**：仅使用 Bash/PowerShell 内置命令和系统自带工具
- **MySQL 不自动安装**：只支持连接远程 MySQL，用户需自行准备
- **CodeGraph 安装失败不阻塞**：可后续手动安装
- **Java/Git 安装失败阻塞**：必须安装成功才能继续
- **服务注册失败降级**：降级为启停脚本模式
- **单包通用**：一个 zip 包适配所有平台
