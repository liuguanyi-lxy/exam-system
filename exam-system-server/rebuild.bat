@echo off
echo ========================================
echo 清理并重新构建 exam-system-server 项目
echo ========================================
echo.

echo 步骤 1: 删除 target 目录...
if exist "target" (
    rmdir /s /q "target"
    echo target 目录已删除
) else (
    echo target 目录不存在
)
echo.

echo 步骤 2: 执行 Maven 清理和安装...
call mvn clean install -DskipTests
echo.

echo ========================================
echo 构建完成！
echo ========================================
pause
