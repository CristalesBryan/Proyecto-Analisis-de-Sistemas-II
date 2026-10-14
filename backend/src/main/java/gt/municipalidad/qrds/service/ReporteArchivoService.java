package gt.municipalidad.qrds.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import gt.municipalidad.qrds.service.ReporteConsultaService.TablaReporte;
import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class ReporteArchivoService {

    public void escribirExcel(Path destino, String titulo, TablaReporte tabla) throws IOException {
        Files.createDirectories(destino.getParent());
        try (XSSFWorkbook libro = new XSSFWorkbook(); OutputStream salida = Files.newOutputStream(destino)) {
            XSSFSheet hoja = libro.createSheet("Reporte");
            int fila = 0;
            Row tituloFila = hoja.createRow(fila++);
            tituloFila.createCell(0).setCellValue(titulo);
            fila++;
            Row encabezado = hoja.createRow(fila++);
            for (int i = 0; i < tabla.columnas().size(); i++) {
                encabezado.createCell(i).setCellValue(tabla.columnas().get(i));
            }
            for (List<String> datos : tabla.filas()) {
                Row row = hoja.createRow(fila++);
                for (int i = 0; i < datos.size(); i++) {
                    row.createCell(i).setCellValue(datos.get(i));
                }
            }
            for (int i = 0; i < tabla.columnas().size(); i++) {
                hoja.autoSizeColumn(i);
            }
            libro.write(salida);
        }
    }

    public void escribirPdf(Path destino, String titulo, TablaReporte tabla) throws IOException {
        Files.createDirectories(destino.getParent());
        try (OutputStream salida = Files.newOutputStream(destino)) {
            escribirDocumento(titulo, tabla, salida);
        }
    }

    private void escribirDocumento(String titulo, TablaReporte tabla, OutputStream salida) throws IOException {
        try {
            Document documento = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
            PdfWriter.getInstance(documento, salida);
            documento.open();
            Font tituloFuente = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Font encabezadoFuente = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
            Font celdaFuente = FontFactory.getFont(FontFactory.HELVETICA, 8);
            documento.add(new Paragraph(titulo, tituloFuente));
            documento.add(new Paragraph(" "));
            PdfPTable pdf = new PdfPTable(Math.max(tabla.columnas().size(), 1));
            pdf.setWidthPercentage(100);
            for (String columna : tabla.columnas()) {
                PdfPCell celda = new PdfPCell(new Phrase(columna, encabezadoFuente));
                celda.setBackgroundColor(new Color(15, 76, 92));
                celda.setHorizontalAlignment(Element.ALIGN_LEFT);
                celda.setPadding(4);
                pdf.addCell(celda);
            }
            for (List<String> fila : tabla.filas()) {
                for (String valor : fila) {
                    PdfPCell celda = new PdfPCell(new Phrase(valor == null ? "" : valor, celdaFuente));
                    celda.setPadding(3);
                    pdf.addCell(celda);
                }
            }
            if (tabla.filas().isEmpty()) {
                PdfPCell vacia = new PdfPCell(new Phrase("No hay casos con los criterios seleccionados.", celdaFuente));
                vacia.setColspan(Math.max(tabla.columnas().size(), 1));
                vacia.setPadding(6);
                pdf.addCell(vacia);
            }
            documento.add(pdf);
            documento.close();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("No fue posible escribir el PDF.", ex);
        }
    }
}
