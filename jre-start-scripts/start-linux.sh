#!/bin/bash

echo -ne "\033]0;Servidor CloudCrypt - Desplegando\007"
clear

echo "===================================================================="
echo "      INICIANDO CLOUDCRYPT"
echo "===================================================================="
echo ""
echo "  [OK] Entorno Java localizado correctamente."
echo "  [...] Levantando modulos..."
echo ""
echo "  ------------------------------------------------------------------"
echo "  POR FAVOR, ESPERA UNOS SEGUNDOS ..."
echo "  ------------------------------------------------------------------"
echo ""

sleep 8

echo -ne "\033]0;Servidor CloudCrypt - Activo\007"
clear

echo "===================================================================="
echo "      INICIANDO CLOUDCRYPT"
echo "===================================================================="
echo ""
echo "  [OK] Entorno Java localizado correctamente."
echo "  [OK] Todos los modulos estan activos."
echo ""
echo "  ------------------------------------------------------------------"
echo "  ¡SERVIDOR LEVANTADO!"
echo "  Para entrar a la aplicacion, haz Ctrl+Clic en el siguiente enlace:"
echo "  --> http://localhost:8080"
echo "  ------------------------------------------------------------------"
echo ""
echo "  [AVISO] No cierres esta ventana. Si la cierras, se apagara la nube."
echo "===================================================================="
echo ""

"./jre-linux/jdk-21.0.11+10-jre/bin/java" -jar cloudcrypt.jar --logging.level.root=OFF > /dev/null 2>&1

echo ""
echo "===================================================================="
echo "   [INFO] El servidor se ha apagado."
echo "===================================================================="

echo "Presione cualquier tecla para continuar..."
read -n 1 -s
