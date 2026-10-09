@echo off
setlocal EnableDelayedExpansion
if defined MAVEN_HOME if exist "%MAVEN_HOME%\bin\mvn.cmd" (
  call "%MAVEN_HOME%\bin\mvn.cmd" %*
  exit /b !ERRORLEVEL!
)
where mvn.cmd >nul 2>&1
if not errorlevel 1 (
  call mvn.cmd %*
  exit /b !ERRORLEVEL!
)
for /d %%M in ("%USERPROFILE%\.m2\wrapper\dists\apache-maven-*") do (
  for /d %%D in ("%%~fM\*") do (
    if exist "%%~fD\bin\mvn.cmd" (
      call "%%~fD\bin\mvn.cmd" %*
      exit /b !ERRORLEVEL!
    )
  )
)
echo Maven not found. Set MAVEN_HOME or use an installed mvn.cmd. 1>&2
exit /b 1
