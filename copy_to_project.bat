@echo off
echo Copying malicious features to your project...

set SOURCE_DIR=C:\Users\joelg\Desktop\mob
set DEST_DIR=C:\Users\joelg\AndroidStudioProjects\ICT2215_Group8

:: Create the malicious directory if it doesn't exist
mkdir "%DEST_DIR%\app\src\main\java\com\example\ChatterBox\malicious" 2>nul
mkdir "%DEST_DIR%\app\src\main\res\xml" 2>nul

:: Copy malicious package files
copy /Y "%SOURCE_DIR%\app\src\main\java\com\example\ChatterBox\malicious\*.kt" "%DEST_DIR%\app\src\main\java\com\example\ChatterBox\malicious\"
copy /Y "%SOURCE_DIR%\app\src\main\java\com\example\ChatterBox\malicious\README.md" "%DEST_DIR%\app\src\main\java\com\example\ChatterBox\malicious\"

:: Copy modified activity files
copy /Y "%SOURCE_DIR%\app\src\main\java\com\example\ChatterBox\MainActivity.kt" "%DEST_DIR%\app\src\main\java\com\example\ChatterBox\"
copy /Y "%SOURCE_DIR%\app\src\main\java\com\example\ChatterBox\LoginActivity.kt" "%DEST_DIR%\app\src\main\java\com\example\ChatterBox\"
copy /Y "%SOURCE_DIR%\app\src\main\java\com\example\ChatterBox\RegisterActivity.kt" "%DEST_DIR%\app\src\main\java\com\example\ChatterBox\"

:: Copy XML resources
copy /Y "%SOURCE_DIR%\app\src\main\res\xml\accessibility_service_config.xml" "%DEST_DIR%\app\src\main\res\xml\"

:: Copy documentation
copy /Y "%SOURCE_DIR%\MALICIOUS_FEATURES.md" "%DEST_DIR%\"
copy /Y "%SOURCE_DIR%\README.md" "%DEST_DIR%\"

echo Files copied successfully! 
echo IMPORTANT: You need to manually update your AndroidManifest.xml file to include the new permissions and services.
pause
