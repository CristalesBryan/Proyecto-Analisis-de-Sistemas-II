package gt.municipalidad.qrds.service;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import gt.municipalidad.qrds.dto.BitacoraDtos.EventoBitacora;
import gt.municipalidad.qrds.dto.BitacoraDtos.FiltrosBitacora;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class BitacoraArchivoService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Guatemala"));
    private static final List<String> COLUMNAS = List.of(
            "Fecha", "Ámbito", "Tipo", "Usuario", "Caso", "Descripción", "Estado anterior", "Estado nuevo", "Dirección", "Resultado");

    public byte[] excel(String titulo, String filtros, List<EventoBitacora> eventos) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            XSSFSheet hoja = libro.createSheet("Auditoría");
            int fila = 0;
            hoja.createRow(fila++).createCell(0).setCellValue("Municipalidad — Reporte de auditoría");
            hoja.createRow(fila++).createCell(0).setCellValue(titulo);
            hoja.createRow(fila++).createCell(0).setCellValue(filtros);
            fila++;
            Row encabezado = hoja.createRow(fila++);
            for (int i = 0; i < COLUMNAS.size(); i++) {
                encabezado.createCell(i).setCellValue(COLUMNAS.get(i));
            }
            for (EventoBitacora evento : eventos) {
                Row row = hoja.createRow(fila++);
                List<String> datos = fila(evento);
                for (int i = 0; i < datos.size(); i++) {
                    row.createCell(i).setCellValue(datos.get(i));
                }
            }
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    public byte[] pdf(String titulo, String filtros, List<EventoBitacora> eventos) throws IOException {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Document documento = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
            PdfWriter.getInstance(documento, salida);
            documento.open();
            documento.add(new Paragraph("Municipalidad — Reporte de auditoría", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
            documento.add(new Paragraph(titulo, FontFactory.getFont(FontFactory.HELVETICA, 10)));
            documento.add(new Paragraph(filtros, FontFactory.getFont(FontFactory.HELVETICA, 9)));
            documento.add(new Paragraph(" "));
            PdfPTable tabla = new PdfPTable(COLUMNAS.size());
            tabla.setWidthPercentage(100);
            Font encabezadoFuente = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, Color.WHITE);
            Font celdaFuente = FontFactory.getFont(FontFactory.HELVETICA, 7);
            for (String columna : COLUMNAS) {
                PdfPCell celda = new PdfPCell(new Phrase(columna, encabezadoFuente));
                celda.setBackgroundColor(new Color(15, 76, 92));
                celda.setPadding(3);
                tabla.addCell(celda);
            }
            for (EventoBitacora evento : eventos) {
                for (String valor : fila(evento)) {
                    PdfPCell celda = new PdfPCell(new Phrase(valor == null ? "" : valor, celdaFuente));
                    celda.setPadding(3);
                    tabla.addCell(celda);
                }
            }
            documento.add(tabla);
            documento.close();
            return salida.toByteArray();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("No fue posible generar el archivo del reporte de auditoría.", ex);
        }
    }

    private List<String> fila(EventoBitacora evento) {
        return List.of(
                evento.fechaHora() == null ? "" : FECHA.format(evento.fechaHora()),
                texto(evento.dominio()),
                texto(evento.tipoEvento()),
                texto(evento.usuario()),
                texto(evento.caso()),
                texto(evento.descripcion()),
                texto(evento.estadoAnterior()),
                texto(evento.estadoNuevo()),
                texto(evento.ipCliente()),
                texto(evento.resultado()));
    }

    private String texto(String valor) {
        return valor == null ? "" : valor;
    }
}
