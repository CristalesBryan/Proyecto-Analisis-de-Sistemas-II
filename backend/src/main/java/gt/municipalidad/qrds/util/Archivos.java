package gt.municipalidad.qrds.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class Archivos {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> EXTENSIONES = Set.of("pdf", "jpg", "jpeg", "png", "docx");

    private Archivos() {
    }

    public static String motivoRechazo(String nombre, byte[] contenido) {
        return motivoRechazo(nombre, contenido, MAX_BYTES, EXTENSIONES);
    }

    public static String motivoRechazo(String nombre, byte[] contenido, long maxBytes, java.util.Set<String> extensiones) {
        String ext = extension(nombre);
        if (!extensiones.contains(ext)) {
            return "Formato no permitido. Use PDF, JPG, PNG o DOCX.";
        }
        if (contenido.length > maxBytes) {
            long megas = Math.max(1, maxBytes / (1024 * 1024));
            return "El archivo supera el máximo de " + megas + " MB.";
        }
        if (!firmaValida(contenido, ext)) {
            return "El contenido del archivo no coincide con el formato declarado.";
        }
        return null;
    }

    public static String mime(String nombre) {
        return switch (extension(nombre)) {
            case "pdf" -> "application/pdf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }

    public static String nombreSeguro(String original) {
        String nombre = original == null ? "archivo" : Path.of(original).getFileName().toString();
        nombre = nombre.replaceAll("[^a-zA-Z0-9._-]", "_");
        return nombre.isBlank() ? "archivo" : nombre;
    }

    public static Path persistir(Path directorio, String codigo, String nombre, byte[] contenido) throws IOException {
        String ext = extension(nombre);
        String almacenado = ext.isBlank() ? UUID.randomUUID().toString() : UUID.randomUUID() + "." + ext;
        Path carpeta = directorio.toAbsolutePath().normalize().resolve(codigo);
        Files.createDirectories(carpeta);
        Path destino = carpeta.resolve(almacenado).normalize();
        if (!destino.startsWith(carpeta)) {
            throw new IOException("Ruta de archivo inválida.");
        }
        Files.write(destino, contenido);
        return destino;
    }

    public static boolean pareceEjecutable(byte[] contenido) {
        if (contenido == null || contenido.length < 2) {
            return false;
        }
        boolean mz = contenido[0] == 'M' && contenido[1] == 'Z';
        boolean elf = contenido.length >= 4
                && contenido[0] == 0x7F
                && contenido[1] == 'E'
                && contenido[2] == 'L'
                && contenido[3] == 'F';
        boolean script = contenido[0] == '#' && contenido[1] == '!';
        return mz || elf || script;
    }

    public static void guardarCuarentena(Path directorio, byte[] contenido) throws IOException {
        Path carpeta = directorio.toAbsolutePath().normalize().resolve("cuarentena");
        Files.createDirectories(carpeta);
        Path destino = carpeta.resolve(UUID.randomUUID().toString()).normalize();
        if (!destino.startsWith(carpeta)) {
            throw new IOException("Ruta de archivo inválida.");
        }
        Files.write(destino, contenido);
    }

    public static String extension(String nombre) {
        int punto = nombre.lastIndexOf('.');
        return punto < 0 ? "" : nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean firmaValida(byte[] contenido, String ext) {
        if (contenido.length < 4) {
            return false;
        }
        return switch (ext) {
            case "pdf" -> contenido[0] == '%' && contenido[1] == 'P' && contenido[2] == 'D' && contenido[3] == 'F';
            case "jpg", "jpeg" -> (contenido[0] & 0xFF) == 0xFF && (contenido[1] & 0xFF) == 0xD8;
            case "png" -> (contenido[0] & 0xFF) == 0x89 && contenido[1] == 'P' && contenido[2] == 'N' && contenido[3] == 'G';
            case "docx" -> contenido[0] == 'P' && contenido[1] == 'K';
            default -> false;
        };
    }
}
