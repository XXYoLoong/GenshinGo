@echo off
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0"
if not exist "pom.xml" (
  echo [错误] 未找到 pom.xml，请勿移动此脚本。
  pause
  exit /b 1
)

REM 仅当 JAVA_HOME 为「JDK 根目录」且含 bin\java.exe 时才保留（避免指到 java.exe 或 javapath 导致 mvnw 报错）
if defined JAVA_HOME if not exist "%JAVA_HOME%\bin\java.exe" set "JAVA_HOME="
if not defined JAVA_HOME (
  for /f "tokens=2*" %%A in ('reg query "HKLM\SOFTWARE\JavaSoft\JDK" /s /v JavaHome 2^>nul ^| findstr /i "JavaHome"') do (
    if exist "%%B\bin\java.exe" (
      set "JAVA_HOME=%%B"
      goto :_gotjdk
    )
  )
  for /f "tokens=2*" %%A in ('reg query "HKLM\SOFTWARE\JavaSoft\Java Development Kit" /s /v JavaHome 2^>nul ^| findstr /i "JavaHome"') do (
    if exist "%%B\bin\java.exe" (
      set "JAVA_HOME=%%B"
      goto :_gotjdk
    )
  )
)
:_gotjdk

if not defined JAVA_HOME (
  for /f "usebackq delims=" %%H in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\print-java-home.ps1" 2^>nul`) do set "JAVA_HOME=%%H"
)

if not defined JAVA_HOME (
  echo [错误] 未设置有效的 JAVA_HOME，且注册表中未找到 JDK。
  echo 请将 JAVA_HOME 设为 JDK 根目录，例如: C:\Program Files\Java\jdk-17
  pause
  exit /b 1
)
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [错误] JAVA_HOME=%JAVA_HOME% 下没有 bin\java.exe
  pause
  exit /b 1
)

echo [信息] 工作目录: %CD%
echo [信息] JAVA_HOME: %JAVA_HOME%
if defined SERVER_PORT (
  echo [信息] 服务端口: %SERVER_PORT% ^(环境变量 SERVER_PORT，会覆盖 application.yml 中的 server.port^)
) else (
  echo [提示] 默认端口 8080。若启动失败提示端口被占用，可先执行 set SERVER_PORT=8081，或在上一级目录运行: 一键启动.bat 8081
)
echo [信息] 使用 Maven Wrapper 启动 Spring Boot...
call "%~dp0mvnw.cmd" spring-boot:run %*
