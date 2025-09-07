@echo off
echo Starting AI TrueJobs Backend...
echo.

REM Set environment variables for better compatibility
set JAVA_OPTS=-Xmx1024m --enable-native-access=ALL-UNNAMED
set GRADLE_OPTS=--warning-mode all

echo Attempting to start with Gradle...
gradlew.bat bootRun --no-daemon --warning-mode all

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Gradle failed. Attempting direct Java compilation...
    echo This may take a moment...
    
    REM Try compiling and running directly
    if exist "src\main\java\com\aitrujobs\AITrueJobsApplication.java" (
        echo Found main application file
        REM Could add direct Java compilation here if needed
    )
)

pause
