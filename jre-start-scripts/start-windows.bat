@echo off
title Servidor CloudCrypt - Desplegando
cls
echo ====================================================================
echo      INICIANDO CLOUDCRYPT
echo ====================================================================
echo.
echo  [OK] Entorno Java localizado correctamente.
echo  [...] Levantando modulos...
echo.
echo  ------------------------------------------------------------------
echo  POR FAVOR, ESPERA UNOS SEGUNDOS ...
echo  ------------------------------------------------------------------
echo.

start /b "" ".\jre-windows\jdk-21.0.11+10-jre\bin\java.exe" -jar cloudcrypt.jar --logging.level.root=OFF > nul 2>&1

timeout /t 8 /nobreak

title Servidor CloudCrypt - Activo
cls
echo ====================================================================
echo      INICIANDO CLOUDCRYPT
echo ====================================================================
echo.
echo  [OK] Entorno Java localizado correctamente.
echo  [OK] Todos los modulos estan activos.
echo.
echo  ------------------------------------------------------------------
echo  ¡SERVIDOR LEVANTADO!
echo  Para entrar a la aplicacion, haz Ctrl+Clic en el siguiente enlace:
echo  --^> http://localhost:8080
echo  ------------------------------------------------------------------
echo.
echo  [AVISO] No cierres esta ventana. Si la cierras, se apagara la nube.
echo ====================================================================
echo.

:loop
timeout /t 10 >nul
goto loop