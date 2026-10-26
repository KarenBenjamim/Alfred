package com.alfred.agenda.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.alfred.agenda.util.XlsxReader;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Importação da planilha da dieta, com arquivos reais:
 *  - o template embutido (assets/plano_alimentar_template.xlsx), que vem com linhas de exemplo;
 *  - o plano preenchido em doc/template/Plano_Alimentar.xlsx (se existir na máquina);
 *  - planilhas montadas aqui para cada tipo de erro.
 */
public class PlanImportTest {

    // Os testes rodam com a pasta do módulo "app" como diretório atual
    private static final File TEMPLATE = new File("src/main/assets/plano_alimentar_template.xlsx");
    private static final File REAL_PLAN = new File("../doc/template/Plano_Alimentar.xlsx");

    private static XlsxReader.Workbook read(File f) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            return XlsxReader.read(in);
        }
    }

    // ---------------- Arquivos reais ----------------

    @Test
    public void templateSheetsAreReadByName() throws Exception {
        XlsxReader.Workbook wb = read(TEMPLATE);
        assertTrue(wb.sheetNames().contains("Instruções")); // existe, mas é ignorada
        XlsxReader.Sheet plan = PlanImport.find(wb, "Plano Alimentar");
        assertNotNull(plan);
        assertEquals("Horário", plan.cell(4, 0));
        assertEquals("Substituições diretas", plan.cell(4, 5));
        assertEquals("07:30", plan.cell(5, 0));
    }

    @Test
    public void templateImportsButEverythingIsAnExample() throws Exception {
        XlsxReader.Workbook template = read(TEMPLATE);
        Set<String> examples = PlanImport.exampleSignatures(template);
        PlanImport.Result r = PlanImport.parse(template, examples, "template.xlsx", 0);

        assertTrue(r.errors.toString(), r.ok());
        assertTrue(r.exampleRows > 0);
        // Só havia exemplos no Plano Alimentar: sem eles não sobra refeição
        assertNull(r.planWithoutExamples);
        assertNotNull(r.plan);
        assertFalse(r.plan.meals.isEmpty());
        // "Ver receita em Observações" não é um grupo: vira orientação da refeição
        boolean recipeNote = false;
        for (NutritionPlan.PlanMeal m : r.plan.meals) {
            if (m.notes.contains("Ver receita em Observações")) recipeNote = true;
        }
        assertTrue(recipeNote);
        assertEquals(35, r.plan.waterMlPerKg);
    }

    @Test
    public void realPlanImportsCompletely() throws Exception {
        assumeTrue("plano real não está nesta máquina", REAL_PLAN.exists());
        Set<String> examples = PlanImport.exampleSignatures(read(TEMPLATE));
        PlanImport.Result r = PlanImport.parse(read(REAL_PLAN), examples, "Plano_Alimentar.xlsx", 20000);

        assertTrue(r.errors.toString(), r.ok());
        assertEquals("o plano real não tem linhas de exemplo", 0, r.exampleRows);
        NutritionPlan plan = r.plan;
        assertNotNull(plan);
        assertEquals("Plano_Alimentar.xlsx", plan.source);
        assertEquals(20000, plan.importedAt);

        // Refeições agrupadas por horário + nome, na ordem do dia
        NutritionPlan.PlanMeal breakfast = plan.meals.get(0);
        assertEquals(8 * 60, breakfast.timeMinutes);
        assertEquals("Café da manhã", breakfast.name);
        assertEquals(4, breakfast.items.size());
        // "Goma de tapioca ... | Banana da terra ..." -> duas substituições
        NutritionPlan.PlanItem cuscuz = breakfast.items.get(3);
        assertEquals("Cuscuz de milho", cuscuz.food.food);
        assertEquals(2, cuscuz.substitutions.size());
        assertEquals(12, cuscuz.group);
        assertEquals("2 porções", cuscuz.portions);

        boolean hasOption2 = false;
        for (NutritionPlan.PlanMeal m : plan.meals) {
            if (m.name.equals("Lanche - Opção 2")) {
                hasOption2 = true;
                assertTrue(m.isAlternativeOption());
            }
            if (m.name.equals("Almoço")) assertEquals("Consumir primeiro os vegetais crus.", m.notes);
        }
        assertTrue(hasOption2);

        // Lista de substituição: grupos "N - Nome" e "—" na Qtd vira vazio
        assertNotNull(plan.group(4));
        assertFalse(plan.group(1).foods.isEmpty());
        assertFalse(plan.notes.isEmpty());
        assertEquals(35, plan.waterMlPerKg);
    }

    // ---------------- Erros de formato ----------------

    @Test
    public void missingSheetIsExplained() throws Exception {
        Map<String, String[][]> sheets = validSheets();
        sheets.remove("Água");
        PlanImport.Result r = PlanImport.parse(build(sheets), Collections.emptySet(), "x.xlsx", 0);
        assertFalse(r.ok());
        assertTrue(r.errors.get(0), r.errors.get(0).contains("\"Água\""));
    }

    @Test
    public void sheetsAreFoundIgnoringAccentsAndCase() throws Exception {
        Map<String, String[][]> sheets = new LinkedHashMap<>();
        for (Map.Entry<String, String[][]> e : validSheets().entrySet()) {
            sheets.put(e.getKey().toUpperCase().replace("Á", "A"), e.getValue());
        }
        PlanImport.Result r = PlanImport.parse(build(sheets), Collections.emptySet(), "x.xlsx", 0);
        assertTrue(r.errors.toString(), r.ok());
    }

    @Test
    public void wrongHeaderPointsToTheColumn() throws Exception {
        Map<String, String[][]> sheets = validSheets();
        sheets.get("Plano Alimentar")[3][5] = "Trocas";
        PlanImport.Result r = PlanImport.parse(build(sheets), Collections.emptySet(), "x.xlsx", 0);
        assertFalse(r.ok());
        assertTrue(r.errors.get(0), r.errors.get(0).contains("coluna F")
                && r.errors.get(0).contains("Substituições diretas"));
    }

    @Test
    public void badRowsAreReportedWithLineNumbers() throws Exception {
        Map<String, String[][]> sheets = validSheets();
        sheets.get("Plano Alimentar")[4][0] = "8 horas"; // linha 5
        sheets.get("Lista de Substituição")[4][0] = "Carnes"; // linha 5, sem "N - "
        PlanImport.Result r = PlanImport.parse(build(sheets), Collections.emptySet(), "x.xlsx", 0);
        assertFalse(r.ok());
        String all = r.errors.toString();
        assertTrue(all, all.contains("linha 5: horário \"8 horas\" inválido"));
        assertTrue(all, all.contains("\"N - Nome\""));
    }

    @Test
    public void emptyPlanIsAnError() throws Exception {
        Map<String, String[][]> sheets = validSheets();
        sheets.put("Plano Alimentar", new String[][]{{"Plano"}, {}, {},
                {"Horário", "Refeição", "Alimento (opção principal)", "Medida caseira", "Qtd",
                        "Substituições diretas", "Ou escolha da lista"}});
        PlanImport.Result r = PlanImport.parse(build(sheets), Collections.emptySet(), "x.xlsx", 0);
        assertFalse(r.ok());
        assertTrue(r.errors.get(0).contains("Nenhuma refeição"));
    }

    @Test
    public void notAnXlsxFileIsRejected() {
        try {
            XlsxReader.read(new ByteArrayInputStream("isto não é uma planilha".getBytes(StandardCharsets.UTF_8)));
        } catch (XlsxReader.FormatException expected) {
            return;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        throw new AssertionError("deveria recusar");
    }

    @Test
    public void excelTimeFractionsAreAccepted() {
        assertEquals(7 * 60 + 30, PlanImport.parseTime("0.3125"));
        assertEquals(8 * 60, PlanImport.parseTime("08:00"));
        assertEquals(8 * 60, PlanImport.parseTime("8h"));
        assertEquals(-1, PlanImport.parseTime("25:00"));
    }

    @Test
    public void undeclaredGroupIsOnlyAWarning() throws Exception {
        Map<String, String[][]> sheets = validSheets();
        sheets.get("Plano Alimentar")[4][6] = "1 porção do Grupo 9";
        PlanImport.Result r = PlanImport.parse(build(sheets), Collections.emptySet(), "x.xlsx", 0);
        assertTrue(r.errors.toString(), r.ok());
        assertTrue(r.warnings.toString(), r.warnings.toString().contains("Grupo 9"));
    }

    // ---------------- Montagem de planilhas de teste ----------------

    /** Planilha mínima válida (linhas 1 a 3 de título, cabeçalho na 4, dados na 5). */
    private static Map<String, String[][]> validSheets() {
        Map<String, String[][]> sheets = new LinkedHashMap<>();
        sheets.put("Plano Alimentar", new String[][]{{"Plano"}, {}, {},
                {"Horário", "Refeição", "Alimento (opção principal)", "Medida caseira", "Qtd",
                        "Substituições diretas", "Ou escolha da lista"},
                {"08:00", "Café da manhã", "Ovo", "2 unidades", "100 g", "Frango - 50 g | Queijo - 30 g",
                        "1 porção do Grupo 4"}});
        sheets.put("Água", new String[][]{{"Água"}, {}, {}, {"Peso (kg)", "70"}, {"ml por kg", "35"}});
        sheets.put("Lista de Substituição", new String[][]{{"Lista"}, {}, {},
                {"Grupo", "Alimento", "Medida caseira (1 porção)", "Qtd"},
                {"4 - Carnes e Proteínas", "Frango", "1 filé", "100 g"}});
        sheets.put("Observações", new String[][]{{"Obs"}, {}, {}, {"Tema", "Orientação"},
                {"Café da manhã", "Sem açúcar."}});
        return sheets;
    }

    /** Gera um .xlsx de verdade (zip com o XML do Excel) e lê com o XlsxReader. */
    private static XlsxReader.Workbook build(Map<String, String[][]> sheets) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            StringBuilder wb = new StringBuilder("<workbook xmlns:r=\"r\"><sheets>");
            StringBuilder rels = new StringBuilder("<Relationships>");
            int i = 1;
            for (Map.Entry<String, String[][]> e : sheets.entrySet()) {
                wb.append("<sheet name=\"").append(e.getKey()).append("\" sheetId=\"").append(i)
                        .append("\" r:id=\"rId").append(i).append("\"/>");
                rels.append("<Relationship Id=\"rId").append(i).append("\" Target=\"worksheets/sheet")
                        .append(i).append(".xml\"/>");
                StringBuilder sheet = new StringBuilder("<worksheet><sheetData>");
                String[][] rows = e.getValue();
                for (int r = 0; r < rows.length; r++) {
                    sheet.append("<row r=\"").append(r + 1).append("\">");
                    for (int c = 0; c < rows[r].length; c++) {
                        if (rows[r][c] == null) continue;
                        sheet.append("<c r=\"").append((char) ('A' + c)).append(r + 1)
                                .append("\" t=\"inlineStr\"><is><t>").append(rows[r][c]).append("</t></is></c>");
                    }
                    sheet.append("</row>");
                }
                sheet.append("</sheetData></worksheet>");
                put(zip, "xl/worksheets/sheet" + i + ".xml", sheet.toString());
                i++;
            }
            put(zip, "xl/workbook.xml", wb.append("</sheets></workbook>").toString());
            put(zip, "xl/_rels/workbook.xml.rels", rels.append("</Relationships>").toString());
        }
        return XlsxReader.read(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static void put(ZipOutputStream zip, String name, String xml) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(xml.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
