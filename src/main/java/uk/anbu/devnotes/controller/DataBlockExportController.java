package uk.anbu.devnotes.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.anbu.devnotes.markdown.code.DataBlockTranslator;
import uk.anbu.devnotes.markdown.code.datablock.ParameterRegistry;
import uk.anbu.devnotes.markdown.code.datablock.YamlCodeblockConfig;
import uk.anbu.devnotes.service.ConfigService;
import uk.anbu.devnotes.service.EncryptionService;
import uk.anbu.devnotes.service.ExchangeRateService;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;

@RestController
@RequestMapping("/datablock")
@Slf4j
public class DataBlockExportController {

    private final ConfigService configService;
    private final ExchangeRateService exchangeRateService;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public DataBlockExportController(ConfigService configService, ExchangeRateService exchangeRateService) {
        this.configService = configService;
        this.exchangeRateService = exchangeRateService;
    }

    // ── Endpoint ──────────────────────────────────────────────────────────────

    @PostMapping(value = "/export-excel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void exportExcel(@RequestBody Map<String, Object> body, HttpServletResponse response) throws IOException {
        var markdownFile = extractString(body, "markdownFile");
        var datablockId  = extractString(body, "datablockId");
        var params       = extractParams(body);

        if (markdownFile == null || datablockId == null) {
            writeError(response, SC_BAD_REQUEST, "missing markdownFile or datablockId");
            return;
        }
        if (params.isEmpty()) {
            writeError(response, SC_BAD_REQUEST, "'params' must be an object");
            return;
        }

        var mdPath = resolveMdPath(markdownFile);
        if (!Files.isRegularFile(mdPath)) {
            writeError(response, SC_BAD_REQUEST, "markdown file not found: " + markdownFile);
            return;
        }

        Optional<YamlCodeblockConfig> config;
        try {
            config = findDataBlockConfig(mdPath, datablockId, params.get());
        } catch (IOException e) {
            log.error("Error reading markdown file for export", e);
            writeError(response, SC_INTERNAL_SERVER_ERROR, "Error reading markdown: " + e.getMessage());
            return;
        }
        if (config.isEmpty()) {
            writeError(response, SC_NOT_FOUND, "datablockId not found in file");
            return;
        }

        var encryptionError = encryptionError(config.get());
        if (encryptionError.isPresent()) {
            writeError(response, SC_FORBIDDEN, encryptionError.get());
            return;
        }

        prepareDownloadHeaders(response, mdPath);
        streamExcel(response, config.get(), params.get());
    }

    // ── Request parsing ───────────────────────────────────────────────────────

    private static String extractString(Map<String, Object> body, String key) {
        var raw = body.get(key);
        return (raw == null || raw.toString().isBlank()) ? null : raw.toString();
    }

    /**
     * Returns the {@code params} map from the request body.
     * <ul>
     *   <li>Key absent or {@code null} → empty map (no parameters)</li>
     *   <li>Key present but not a map → {@link Optional#empty()} (signals a bad request)</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private static Optional<Map<String, Object>> extractParams(Map<String, Object> body) {
        var raw = body.get("params");
        if (raw == null)                  return Optional.of(Map.of());
        if (raw instanceof Map<?, ?> map) return Optional.of((Map<String, Object>) map);
        return Optional.empty();
    }

    // ── File & config resolution ──────────────────────────────────────────────

    private Path resolveMdPath(String markdownFileStr) {
        var path = Paths.get(configService.getDocsDirectory()).resolve(markdownFileStr);
        return path.isAbsolute()
                ? path
                : Path.of("").toAbsolutePath().resolve(markdownFileStr).normalize();
    }

    /**
     * Walks every top-level node in the parsed markdown document, keeps only
     * {@code ```data} fenced blocks, parses each as {@link YamlCodeblockConfig}, and returns
     * the first one whose checksum matches {@code datablockId}.
     */
    private Optional<YamlCodeblockConfig> findDataBlockConfig(Path mdPath, String datablockId,
                                                               Map<String, Object> params) throws IOException {
        var document = Parser.builder().build().parse(Files.readString(mdPath));
        return Stream.iterate(document.getFirstChild(), Objects::nonNull, Node::getNext)
                .filter(FencedCodeBlock.class::isInstance)
                .map(FencedCodeBlock.class::cast)
                .filter(fcb -> fcb.getInfo() != null && "data".equals(fcb.getInfo().trim()))
                .flatMap(fcb -> parseConfig(fcb.getLiteral()).stream())
                .filter(config -> datablockId.equals(config.checksum(params)))
                .findFirst();
    }

    private Optional<YamlCodeblockConfig> parseConfig(String yaml) {
        try {
            return Optional.of(yamlMapper.readValue(yaml, YamlCodeblockConfig.class));
        } catch (Exception e) {
            log.debug("Skipping un-parseable data block", e);
            return Optional.empty();
        }
    }

    // ── Security ──────────────────────────────────────────────────────────────

    /** Returns an error message when the datasource password is encrypted but no key is loaded. */
    private Optional<String> encryptionError(YamlCodeblockConfig config) {
        var source = config.getSource();
        if (source == null || source.isBlank()) return Optional.empty();

        var dsName = source.contains("/")
                ? source.substring(source.lastIndexOf('/') + 1) : source;
        var ds = configService.getDataSourceConfig(dsName);

        if (ds != null && EncryptionService.isEncrypted(ds.password()) && !configService.isEncryptionKeySet()) {
            return Optional.of("Datasource '" + dsName + "' has an encrypted password. "
                    + "Please enter the encryption key at /config/encryption-key first.");
        }
        return Optional.empty();
    }

    // ── Excel streaming ───────────────────────────────────────────────────────

    private static void prepareDownloadHeaders(HttpServletResponse response, Path mdPath) {
        var baseName = mdPath.getFileName().toString().replaceFirst("[.][^.]+$", "");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + baseName + "-export.xlsx\"");
    }

    /** Executes the query and streams all rows directly into an xlsx download. */
    private void streamExcel(HttpServletResponse response, YamlCodeblockConfig config,
                             Map<String, Object> params) throws IOException {
        var translator = new DataBlockTranslator(
                configService::getDataSourceConfig, configService,
                new ParameterRegistry(), exchangeRateService);

        // SXSSFWorkbook keeps only 100 rows in memory; older rows are flushed to a temp file
        try (var workbook = new SXSSFWorkbook(100)) {
            workbook.setCompressTempFiles(true);
            var sheet = workbook.createSheet("Data");
            translator.executeQueryForExport(config, params,
                    sheetWriter(sheet, buildHeaderStyle(workbook)));
            workbook.write(response.getOutputStream());
        } catch (Exception e) {
            log.error("Error exporting data block to Excel", e);
            throw new IOException("Excel export failed: " + e.getMessage(), e);
        }
    }

    private static CellStyle buildHeaderStyle(SXSSFWorkbook workbook) {
        var style = workbook.createCellStyle();
        var font  = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    /** Returns a {@link ResultSetExtractor} that writes a header row followed by all data rows. */
    private static ResultSetExtractor<Void> sheetWriter(SXSSFSheet sheet, CellStyle headerStyle) {
        return rs -> {
            var colLabels = readColumnLabels(rs.getMetaData());
            writeHeaderRow(sheet, headerStyle, colLabels);
            writeDataRows(sheet, rs, colLabels);
            return null;
        };
    }

    private static List<String> readColumnLabels(ResultSetMetaData meta) throws SQLException {
        int count = meta.getColumnCount();
        var labels = new ArrayList<String>(count);
        for (int i = 1; i <= count; i++) labels.add(meta.getColumnLabel(i));
        return List.copyOf(labels);
    }

    private static void writeHeaderRow(SXSSFSheet sheet, CellStyle style, List<String> colLabels) {
        var row = sheet.createRow(0);
        for (int i = 0; i < colLabels.size(); i++) {
            var cell = row.createCell(i);
            cell.setCellValue(colLabels.get(i));
            cell.setCellStyle(style);
        }
    }

    private static void writeDataRows(SXSSFSheet sheet, ResultSet rs, List<String> colLabels) throws SQLException {
        int rowIdx = 1;
        while (rs.next()) {
            var row = sheet.createRow(rowIdx++);
            for (int i = 0; i < colLabels.size(); i++) {
                writeDataCell(row.createCell(i), colLabels.get(i), rs.getObject(i + 1));
            }
        }
    }

    private static void writeDataCell(Cell cell, String colLabel, Object value) {
        DataBlockTranslator.resolveTargetCurrency(colLabel)
                .filter(targetCcy -> value != null && !value.toString().isBlank())
                .ifPresentOrElse(
                        targetCcy -> writeCurrencyCell(cell, value.toString(), targetCcy),
                        () -> setCellValue(cell, value));
    }

    private static void writeCurrencyCell(Cell cell, String rawValue, String targetCurrency) {
        DataBlockTranslator.convertCurrencyValue(rawValue, targetCurrency)
                .ifPresentOrElse(
                        converted -> cell.setCellValue(converted.doubleValue()),
                        () -> cell.setCellValue(rawValue));  // malformed or no rate → raw text
    }

    // ── Cell value helpers ────────────────────────────────────────────────────

    private static void setCellValue(Cell cell, Object value) {
        switch (value) {
            case null                     -> cell.setBlank();
            case Boolean     b            -> cell.setCellValue(b);
            case BigDecimal  bd           -> cell.setCellValue(bd.doubleValue());
            case Number      n            -> cell.setCellValue(n.doubleValue());
            case java.sql.Timestamp ts    -> cell.setCellValue(ts.toLocalDateTime().toString());
            case java.sql.Date      d     -> cell.setCellValue(d.toLocalDate().toString());
            case Blob ignored             -> cell.setCellValue("<blob>");
            default                       -> cell.setCellValue(value.toString());
        }
    }

    // ── Error response ────────────────────────────────────────────────────────

    private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);
    }
}
