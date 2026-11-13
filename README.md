# Crafty+ Control — aplicación Android nativa (beta, no oficial)

Proyecto Android **nativo** para un **STORZ & BICKEL CRAFTY+ con puerto USB-C**.
Se comunica por **Bluetooth Low Energy (BLE)** con el dispositivo; el puerto USB-C
se utiliza para carga, no para esta conexión. No abre la web, no necesita Google
Chrome ni conexión a internet, y no comparte información con un servidor.

> **Estado: prototipo beta sin pruebas en hardware físico.** Es código fuente preparado
> para generar un APK con Android Studio o GitHub Actions; **no incluye un APK precompilado**.
> Las características y el protocolo BLE se basan en implementaciones públicas de terceros,
> no en una API oficial garantizada. Los nombres y funciones pueden variar con el firmware.

## Funciones implementadas

- Escanear dispositivos BLE compatibles y seleccionar manualmente el CRAFTY+.
- Conectar y desconectar; consultar temperatura actual, objetivo, batería y estado de calentamiento.
- Ajustar objetivo entre **40 y 210 °C**.
- Ajustar incremento de Boost (0 a 30 °C); mostrar estimación de Boost y Superboost,
  con un límite de 210 °C. El modelo utiliza un incremento común para estos modos.
- Encender y detener el calefactor, siempre mediante una acción explícita (encendido con confirmación).
- Cambiar brillo LED del dispositivo y vibración del dispositivo.
- Habilitar o deshabilitar LED de carga y opción de Bluetooth permanente.
- Apagado automático entre 1 y 5 minutos, con escritura de código de autorización BLE.
- Localizar el equipo (si el firmware CRAFTY+ lo admite).
- Leer número de serie, firmware, versión BLE y horas de funcionamiento cuando están disponibles.
- Cambiar visualización °C / °F y habilitar sonido o vibración del **teléfono** al alcanzar
  temperatura objetivo. Estas preferencias locales permanecen guardadas.
- Restablecimiento de configuración de fábrica con confirmación previa.
- Botón de actualización manual de lecturas, sin telemetría ni anuncios.

## Limitaciones que debes conocer

- No se ha conectado a un CRAFTY+ USB-C físico. Una escritura podría no estar disponible
  en algunas versiones de firmware; verificar cada control antes de uso normal.
- La detección puede exigir activar Ubicación en teléfonos con Android 11 o anterior.
- No se implementan las actualizaciones de firmware; el fabricante lista esa funcionalidad
  para VENTY y VOLCANO HYBRID, no CRAFTY+.
- No son aplicables controles exclusivos del VENTY/VEAZY/VOLCANO, como bomba de aire,
  modos de carga VENTY o workflows de escritorio.
- En el primer enlace hay numerosas lecturas BLE secuenciales. Puede tomar varios segundos.
- Si el dispositivo se conecta a otra aplicación simultáneamente puede rechazar el enlace.
- En algunos equipos el «Bluetooth permanente» puede consumir batería en reposo.
- **Precaución:** no dejes un calefactor activo sin supervisión. El CRAFTY+ no funciona
  mientras se carga por USB-C; esto también aplica a control remoto.

## Compilar con Android Studio

1. Instala **Android Studio** con JDK 17, Android SDK **35** y Gradle **8.11.1**.
2. Abre la carpeta `CraftyPlus_Nativo_Android` como proyecto.
3. El proyecto contiene el archivo de propiedades del wrapper, pero no el JAR ni el
   ejecutable de Gradle Wrapper. Si tu Android Studio no los genera, usa Gradle
   instalado o ejecuta `gradle wrapper --gradle-version 8.11.1`.
4. Selecciona **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Instala `app/build/outputs/apk/debug/app-debug.apk` en tu Android.

## Compilar un APK en GitHub, sin Android Studio

1. Crea un repositorio privado en GitHub y sube el contenido de **esta carpeta**
   (no una carpeta contenedora adicional).
2. En GitHub elige **Actions > Compilar Crafty+ APK > Run workflow**.
3. Descarga el artefacto `CraftyPlus-Control-APK-beta` y extrae `app-debug.apk`.

El APK resultante es un **APK de pruebas firmado para depuración**.

## Cómo conectar

1. Desconecta tu CRAFTY+ de Chrome u otras aplicaciones y desconecta el USB-C.
2. Enciende Bluetooth en Android. En Android 11 o anterior activa también Ubicación.
3. Enciende el CRAFTY+, mantenlo cerca y toca **Buscar y conectar CRAFTY+**.
4. Concede los permisos Bluetooth, espera el escaneo y escoge la entrada
   `Storz&Bickel` / `STORZ&BICKEL` correspondiente al equipo.
5. Confirma que la batería, firmware y temperaturas son reales antes de
   modificar cualquier ajuste; prueba primero un cambio sencillo de brillo.

## Implementación

- Java y Android framework estándar. Sin bibliotecas de terceros y sin permiso INTERNET.
- Servicios BLE: `00000001/2/3-4c45-4b43-4942-265a524f5453`.
- Lecturas, escrituras y suscripciones GATT en cola secuencial con timeout.
- En Android 12+ pide BLUETOOTH_SCAN y BLUETOOTH_CONNECT.
- La app no ejecuta comandos automáticamente al conectar; comienza con lecturas.

### Referencias de interoperabilidad

- https://firsttris.github.io/reactive-volcano-app/docs/protocol.html
- https://github.com/firsttris/reactive-volcano-app
- https://support.storz-bickel.com/hc/es/articles/36098380975377-Ajustes-de-temperatura
- https://support.storz-bickel.com/hc/en-us/articles/35848047118097-S-B-Web-App
- https://www.storz-bickel.com/en/app/web

Este proyecto utiliza una implementación propia basada en información pública, **sin copiar
código fuente ni archivos del fabricante o de otras aplicaciones**.

**Sin afiliación ni aprobación de STORZ & BICKEL.** No ofrece garantía sobre
compatibilidad, exactitud de lecturas o comportamiento BLE hasta probarlo físicamente.

## Nota sobre la descarga y compilación
Este paquete incluye el proyecto Android completo, no el APK. Para compilarlo, sube los archivos de la raíz al repositorio y ejecuta el flujo de GitHub Actions. No se ha verificado una compilación real del proyecto ni el comportamiento Bluetooth en un dispositivo físico.
