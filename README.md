# Crafty+ Control para Android

Reconstrucción del proyecto beta para CRAFTY+ USB-C. El archivo contiene código fuente. La compilación y las pruebas con un CRAFTY+ físico están pendientes.

## Generar el APK en GitHub

1. Extrae el ZIP y crea un repositorio privado.
2. Sube el contenido de CraftyPlus_Nativo_Android a la raíz del repositorio, incluida la carpeta oculta .github. El archivo .github/workflows/build-apk.yml debe quedar en la rama principal.
3. Abre Actions → Compilar Crafty+ APK → Run workflow.
4. Si finaliza correctamente, descarga el artefacto CraftyPlus-Control-APK-beta de esa ejecución y extrae app-debug.apk.

Para compilar localmente, abre la carpeta del proyecto con Android Studio o ejecuta gradle :app:assembleDebug con Gradle 8.9, JDK 17 y el SDK Android 35 instalado. El resultado esperado queda en app/build/outputs/apk/debug/.

## Uso de la beta

Enciende el CRAFTY+, cierra otras apps conectadas al equipo, activa Bluetooth y pulsa Buscar CRAFTY+. En Android 6–11, la búsqueda también puede requerir activar la ubicación. Elige el equipo de la lista. Actualizar lecturas consulta el estado y vuelve a leer los ajustes tras cada cambio.

Controles implementados en el código: temperatura, incremento común de Boost y Superboost, batería, brillo LED, vibración del CRAFTY+, temporizador de apagado, localización, horas de uso y firmware. La interfaz habilita los ajustes según las características Bluetooth que expone el dispositivo. No incluye preferencias de sonido ni vibración del teléfono.

Primero comprueba que las lecturas coincidan con el dispositivo antes de guardar ajustes. La conexión y las escrituras requieren validación con el equipo real.

Referencias del protocolo y permisos:

- https://firsttris.github.io/reactive-volcano-app/docs/protocol.html#crafty-crafty
- https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
