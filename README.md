<img src="assets/logo.png" alt="X-USB" width="96" height="96">

# X-USB

Herramienta para pendrives y discos USB en Android: grabar ISOs, montar un USB con Ventoy, formatear, gestionar particiones y borrar de forma segura. Pensada para hacer desde el móvil lo que normalmente harías con Rufus o Ventoy en el PC.

Es de [Static Forge](https://staticforge.me). Libre, gratis, sin anuncios y sin telemetría.

## Qué hace

- **Grabar ISO/IMG** en un USB, bloque a bloque.
- **Ventoy**: instala Ventoy en el USB (MBR o GPT, con o sin Secure Boot) para arrancar varias ISOs sin volver a formatear.
- **Formatear** en FAT16, FAT32, exFAT, NTFS, ext2/3/4 y F2FS, eligiendo tamaño de clúster y etiqueta. Antes de tocar nada enseña un resumen y pide confirmación.
- **Particiones**: ver, crear, borrar y redimensionar.
- **BIN a ISO**: convierte imágenes BIN/CUE.
- **Borrado seguro** del dispositivo.
- **Descargador de ISOs** con un catálogo de distribuciones.
- Temas: morado oscuro, claro y azul con amarillo. Español e inglés.

## Requisitos

- Android 8.0 o superior con **USB OTG**.
- **Root** para todo lo que escribe en el dispositivo (formatear, grabar, particiones). Sin root solo funcionan las partes que no tocan el disco, como el descargador.

## Instalar

Descarga la APK desde [Releases](https://github.com/KCRooty/X-USB/releases).

## Compilar

Necesitas JDK 17 y el SDK de Android (plataforma 34).

```sh
./gradlew assembleDebug
```

La APK sale en `app/build/outputs/apk/debug/`.

## Estado

Proyecto en desarrollo. Cada operación que escribe en un disco pide confirmación, pero úsalo con cuidado y comprueba siempre qué dispositivo has elegido.

## Licencia

[GPL-3.0](LICENSE). Hecho por KCRooty en Static Forge.
