@echo off
chcp 65001 >nul
rem בונה את האפליקציה ומוציא לתיקייה הזו: KeepScreenOn.apk
rem Android לא בונה מנתיב עם עברית - מעתיקים לנתיב באנגלית, בונים, ומחזירים את ה-APK לכאן.
rem לפני כל גרסה חדשה: להגדיל את versionCode ו-versionName בקובץ android\app\build.gradle.kts
set "ROOT=%~dp0"
set "DST=C:\src\keepscreen-build"

robocopy "%ROOT%android\app" "%DST%\app" /MIR /XD build /NFL /NDL /NJH /NJS >nul
robocopy "%ROOT%android\signing" "%DST%\signing" /MIR /NFL /NDL /NJH /NJS >nul
for %%f in (build.gradle.kts settings.gradle.kts gradle.properties local.properties) do copy /Y "%ROOT%android\%%f" "%DST%\%%f" >nul
rem ה-Java של Android Studio, ותיקייה זמנית קצרה (אחרת Java בווינדוס נכשל ב-loopback)
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp"
call "%USERPROFILE%\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13\bin\gradle.bat" -p "%DST%" testReleaseUnitTest assembleRelease %*
if errorlevel 1 (echo הבנייה נכשלה & exit /b 1)
copy /Y "%DST%\app\build\outputs\apk\release\app-release.apk" "%ROOT%KeepScreenOn.apk" >nul

echo.
echo מוכן:
echo   %ROOT%KeepScreenOn.apk
