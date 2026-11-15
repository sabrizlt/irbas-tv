@echo off
chcp 65001 >nul
title Irbas TV
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0installa.ps1"
