<img src="assets/logo.png" alt="X-USB" width="96" height="96">

# X-USB

Herramienta para pendrives y discos USB, en **Android, Linux y Windows**, con la misma interfaz y la misma lógica en las tres: formatear, grabar imágenes, hacer copias, gestionar particiones y borrar.

Es de [Static Forge](https://staticforge.me). Libre (GPL-3.0), gratis, sin anuncios y sin telemetría.

## Qué hace (y dónde)

| Función | Linux | Windows | Android |
|---|---|---|---|
| Listar USB conectados | ✅ | ✅ | ✅ (root) |
| Formatear FAT16/32, exFAT, NTFS | ✅ | ✅ (FAT32 ≤ 32 GB) | ✅ (root + herramientas) |
| Formatear ext2/3/4, F2FS | ✅ | ❌ | ✅ (root + herramientas) |
| Grabar ISO/IMG en bruto | ✅ | ✅ | ✅ (root) |
| Copia de seguridad y restauración | ✅ | ✅ | ✅ (root) |
| Borrado | ✅ completo | ⚠️ rápido (tablas y firmas) | ✅ completo |
| Particiones: ver, crear, borrar | ✅ | ✅ | ✅ (root + herramientas) |
| Convertir MBR ⇄ GPT sin perder datos | ✅ | ❌ | ✅ (root + herramientas) |
| BIN → ISO, descargador de ISOs | ✅ | ✅ | ✅ |
| Ventoy | ❌ aún | ❌ aún | ❌ aún |

- ✅ = implementado. Lo que se ha ejecutado de verdad y lo que solo compila está detallado en [CHANGELOG.md](CHANGELOG.md).
- Lo que no funciona en una plataforma devuelve un error claro; nada finge haber terminado.

## Seguridad

Antes de escribir, cada plataforma comprueba que el destino es un **disco USB** y **no** el disco del sistema ni uno de arranque. Aun así, escribir en un disco es destructivo: comprueba siempre qué dispositivo has elegido.

## Requisitos

- **Linux**: `parted`, `gdisk` (sgdisk), `dosfstools`, `exfatprogs`, `ntfs-3g`, `f2fs-tools` según lo que uses; permisos de administrador (se piden con `pkexec`).
- **Windows 10/11**: ejecutar como **administrador** para modificar discos. Java 21 si usas el `.jar`.
- **Android 8.0+** con **USB OTG** y **root**. Android no trae `parted`, `sgdisk` ni `mkfs.*`: sin ellos las operaciones dan error.

## Instalar

Descarga desde [Releases](https://github.com/KCRooty/X-USB/releases): APK (Android), `.deb` (Linux) y `.jar` (Windows, `java -jar`).

## Compilar

JDK 21 y el SDK de Android (plataforma 34).

```sh
./gradlew :app:assembleDebug                  # APK
./gradlew :desktop:run                        # escritorio
./gradlew :desktop:packageUberJarForCurrentOS # JAR portable
```

## Estructura

- `common/` — interfaz (Compose Multiplatform) y lógica compartida (`ShellBackend`, `JvmOps`).
- `desktop/` — Linux y Windows (`LinuxBackend`, `WindowsBackend`).
- `app/` — Android (`AndroidBackend` con root vía libsu).

## Licencia

[GPL-3.0](LICENSE). Hecho por KCRooty en Static Forge.
