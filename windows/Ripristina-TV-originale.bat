@echo off
chcp 65001 >nul
title Irbas TV - ripristino
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0installa.ps1" -Ripristina
