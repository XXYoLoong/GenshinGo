@echo off
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0"
if not exist "pom.xml" (
  echo [错误] 未找到 pom.xml，请勿移动此脚本。
  pause
  exit /b 1
)

set "MAVEN_ARGS="
REM 勿在 if (...) 块内 shift（CMD 对块内 shift 行为不可靠，会导致端口仍被拼进 MAVEN_ARGS）
if "%~1"=="" goto :_collect_args
echo(%~1| findstr /r "^[0-9][0-9]*$" >nul 2>&1
if errorlevel 1 goto :_collect_args
set "SERVER_PORT=%~1"
shift
:_collect_args
if "%~1"=="" goto :_args_done
set "MAVEN_ARGS=%MAVEN_ARGS% %1"
shift
goto :_collect_args
:_args_done

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

if defined SERVER_PORT (
  echo(%SERVER_PORT%| findstr /r "^[0-9][0-9]*$" >nul 2>&1
  if errorlevel 1 (
    echo [错误] SERVER_PORT=%SERVER_PORT% 不是有效端口号。
    pause
    exit /b 1
  )
  call :_check_port_busy "%SERVER_PORT%"
  if errorlevel 1 exit /b 1
) else (
  call :_choose_free_port
  if errorlevel 1 exit /b 1
)

echo [信息] 工作目录: %CD%
echo [信息] JAVA_HOME: %JAVA_HOME%
echo [信息] 服务地址: http://localhost:%SERVER_PORT%
echo [信息] 使用 Maven Wrapper 启动 Spring Boot...
echo [提示] 启动完成后会自动打开系统默认浏览器
echo [提示] 若没有新窗口，请用浏览器手动打开上面的服务地址
call "%~dp0mvnw.cmd" spring-boot:run "-Dspring-boot.run.arguments=--server.port=%SERVER_PORT% --deepseek.launch-browser=true" %MAVEN_ARGS%
exit /b %ERRORLEVEL%

:_choose_free_port
for /f "usebackq delims=" %%P in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\find-free-port.ps1" 8080 8099`) do (
  if not defined SERVER_PORT set "SERVER_PORT=%%P"
)
if not defined SERVER_PORT (
  echo [错误] 8080-8099 端口都被占用，无法自动启动。
  echo [提示] 请关闭占用端口的程序，或执行: run.bat 端口号
  pause
  exit /b 1
)
exit /b 0

:_check_port_busy
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\test-port-free.ps1" %~1 >nul 2>&1
if errorlevel 1 (
  echo [错误] 端口 %~1 已被占用。
  echo [提示] 请换一个端口，例如: run.bat 8082
  pause
  exit /b 1
)
exit /b 0
