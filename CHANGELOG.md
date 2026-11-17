# Changelog

## 1.1.0 — 2026-11-17

Versión multiplataforma: Android, Linux y Windows comparten interfaz y lógica.

### Nuevo
- Escritorio (Linux y Windows) con operaciones reales sobre USB.
- Backend común `ShellBackend`: descubrimiento por sysfs, guardas de seguridad, `dd` con progreso, formato, particiones, MBR⇄GPT.
- Windows: listado, formato, borrado rápido, escritura/copia en bruto y particiones vía PowerShell.
- Comprobación de seguridad: solo se actúa sobre discos USB que no sean del sistema ni de arranque.

### Cambiado
- Android usa la misma interfaz que el escritorio (se retiró la interfaz duplicada).
- El escritorio de la 1.0.0 era una maqueta: ya no hay operaciones simuladas.

### Verificado
- **Linux**: 19 pruebas de integración contra imágenes loop (formato FAT32/ext4/exFAT/NTFS, particiones, MBR→GPT, escritura y copia con sha256, borrado, BIN→ISO, guardas). Todas pasan. Nunca contra discos reales.
- **Windows**: compila y el JAR arranca. No se han ejecutado escrituras (requieren administrador y un disco de pruebas).
- **Android**: el APK compila. No se ha ejecutado en un dispositivo.

### Limitaciones conocidas
- Ventoy no está integrado en ninguna plataforma.
- Windows: sin ext2/3/4 ni F2FS; FAT32 solo hasta 32 GB; MBR⇄GPT exige borrar el disco; el borrado es rápido, no completo.
- Android: necesita root y las herramientas `parted`, `sgdisk` y `mkfs.*`, que no vienen de serie.
- El redimensionado de particiones no está implementado.

## 1.0.0
Primera versión: solo Android. La versión de escritorio incluida era una maqueta sin operaciones reales.
