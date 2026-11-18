# Changelog

## 1.1.0 — 2026-11-18

Versión multiplataforma: Android, Linux y Windows comparten interfaz y lógica.

### Android sin root
- Listar, grabar ISO/IMG, borrar, copiar y restaurar ya no necesitan root: van por USB Host (protocolo de almacenamiento masivo con libaums). Solo hay que aceptar el aviso de permiso de Android al conectar el USB.
- Al grabar o restaurar, el USB se vuelve a leer y se compara con la imagen.
- Selector de archivos de Android para elegir imágenes y dónde guardar las copias.
- Formatear en FAT32 sin root: tabla MBR con una partición desde 1 MiB, formateada según la especificación de Microsoft. Probado contra imágenes de 512 MB a 40 GB, con sectores de 512 y 4096 bytes, con `fsck.fat` y mtools.
- Ver la tabla de particiones (MBR y GPT) sin root, con el sistema de archivos de cada partición.
- Los demás formatos, crear o borrar particiones y Ventoy siguen necesitando root; sin root avisan en vez de fallar a medias.

### Aspecto (Android y escritorio)
- Icono de la app a partir del logo de X-USB (antes usaba el genérico de Android).
- Marca en la cabecera e iconos en cada herramienta, en la barra lateral y en las tarjetas del móvil.

### Nuevo
- Escritorio (Linux y Windows) con operaciones reales sobre USB.
- Backend común `ShellBackend`: descubrimiento por sysfs, guardas de seguridad, `dd` con progreso, formato, particiones, MBR⇄GPT.
- Windows: listado, formato, borrado rápido, escritura/copia en bruto y particiones vía PowerShell.
- Comprobación de seguridad: solo se actúa sobre discos USB que no sean del sistema ni de arranque.

### Interfaz nueva (identidad «Forja»)
- Rediseño completo de la interfaz común: grafito cálido con un único acento brasa, tema claro «Papel», datos técnicos en monoespaciada.
- Escritorio: barra lateral con selector de unidad y herramientas agrupadas; móvil: inicio con lista. Se adapta al ancho.
- Selector de unidad real (varias unidades, actualizar, causa y siguiente paso si no hay ninguna o faltan permisos).
- Las operaciones destructivas piden escribir el identificador de la unidad (por ejemplo `sdb`) tras un resumen de lo que se pierde.
- Progreso real de cada operación; los errores se muestran tal cual los da el sistema.
- Selector de archivos en escritorio para imágenes y copias; la ISO descargada queda propuesta al grabar.
- Las herramientas sin implementar (Ventoy, multi-arranque, PS2, archivos) aparecen como «planificado» y no hacen nada.
- El catálogo de ISO solo conserva enlaces directos comprobados el 2026-11-17 (se retiraron los que daban 404 o no eran una ISO).
- Se corrigió: textos ilegibles sobre fondo oscuro, botones sin acción, etiqueta de formato fija y uso incorrecto de la ruta de partición.

### Cambiado
- Android usa la misma interfaz que el escritorio (se retiró la interfaz duplicada).
- El escritorio de la 1.0.0 era una maqueta: ya no hay operaciones simuladas.

### Verificado
- **Linux**: 19 pruebas de integración contra imágenes loop (formato FAT32/ext4/exFAT/NTFS, particiones, MBR→GPT, escritura y copia con sha256, borrado, BIN→ISO, guardas). Todas pasan. Nunca contra discos reales.
- **Windows**: compila y el JAR arranca. No se han ejecutado escrituras (requieren administrador y un disco de pruebas).
- **Android**: el APK compila en CI. El formateador FAT32 propio se probó contra imágenes de 512 MB a 40 GB con `fsck.fat` y mtools. **El acceso por USB Host (grabar, borrar, copiar, restaurar) no se ha probado todavía en un dispositivo real.**

### Limitaciones conocidas
- Ventoy no está integrado en ninguna plataforma.
- Windows: sin ext2/3/4 ni F2FS; FAT32 solo hasta 32 GB; MBR⇄GPT exige borrar el disco; el borrado es rápido, no completo.
- Android: sin root solo FAT32, grabar, borrar, copiar, restaurar y ver particiones; el resto (otros formatos, crear o borrar particiones, MBR⇄GPT) necesita root y las herramientas `parted`, `sgdisk` y `mkfs.*`, que no vienen de serie.
- El redimensionado de particiones no está implementado.

## 1.0.0
Primera versión: solo Android. La versión de escritorio incluida era una maqueta sin operaciones reales.
