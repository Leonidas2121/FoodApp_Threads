@echo off
setlocal

set JAVA="C:\Program Files\Common Files\Oracle\Java\javapath\java.exe"

set CLASSPATH=C:\Users\thano\.gradle\caches\modules-2\files-2.1\com.fasterxml.jackson.core\jackson-annotations\2.15.0\89b0fd554928425a776a6e97ed010034312af21d\jackson-annotations-2.15.0.jar;^
C:\Users\thano\.gradle\caches\modules-2\files-2.1\com.fasterxml.jackson.core\jackson-core\2.15.0\12f334a1dc9c6d2854c43ae314024dde8b3ad572\jackson-core-2.15.0.jar;^
C:\Users\thano\.gradle\caches\modules-2\files-2.1\com.fasterxml.jackson.core\jackson-databind\2.15.0\d41caa3a4e9f85382702a059a65c512f85ac230\jackson-databind-2.15.0.jar;^
C:\Users\thano\.gradle\caches\modules-2\files-2.1\ch.randelshofer\fastdoubleparser\0.8.0\85c25540369921659556ead85e02c99ef0d24280\fastdoubleparser-0.8.0.jar;^
C:\Users\thano\Desktop\AndroidLabNetwork\MyApplicationNetwork\backend\build\classes\java\main

%JAVA% -cp "%CLASSPATH%" com.example.myapplicationnetwork.Master

endlocal
pause
