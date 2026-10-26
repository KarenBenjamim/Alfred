package com.alfred.agenda.ui.diet;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.alfred.agenda.data.PlanImport;
import com.alfred.agenda.util.DateUtils;
import com.alfred.agenda.util.XlsxReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Set;

/**
 * Arquivos da dieta:
 *  - leitura da planilha escolhida pelo usuário (com validação, para a prévia);
 *  - cópia do template (assets/plano_alimentar_template.xlsx) para a pasta Downloads.
 * Nenhuma permissão de armazenamento é necessária: a planilha vem pelo seletor de arquivos
 * do sistema e, no Android 10+, a cópia vai para Downloads pelo MediaStore.
 */
public final class PlanFiles {

    public static final String TEMPLATE_ASSET = "plano_alimentar_template.xlsx";
    public static final String TEMPLATE_NAME = "Plano_Alimentar_Template.xlsx";
    public static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** Tipos aceitos no seletor (alguns gerenciadores marcam .xlsx como binário genérico). */
    public static final String[] PICK_MIME_TYPES = {XLSX_MIME, "application/vnd.ms-excel", "application/octet-stream"};

    /** Planilhas maiores que isto não são de um plano alimentar. */
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    private PlanFiles() {
    }

    // ---------------- Importar ----------------

    /** Lê e valida a planilha. Chame fora da thread principal. Nunca lança exceção. */
    public static PlanImport.Result readPlan(Context ctx, Uri uri) {
        String name = displayName(ctx, uri);
        if (name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".xls")) {
            return PlanImport.failure("O arquivo \"" + name + "\" está no formato antigo do Excel (.xls). "
                    + "Abra no Excel ou no Google Planilhas e salve como .xlsx.");
        }
        long size = size(ctx, uri);
        if (size > MAX_FILE_BYTES) {
            return PlanImport.failure("O arquivo é grande demais para um plano alimentar ("
                    + (size / (1024 * 1024)) + " MB). Use o template do Alfred.");
        }
        XlsxReader.Workbook workbook;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return PlanImport.failure("Não consegui abrir o arquivo escolhido.");
            workbook = XlsxReader.read(in);
        } catch (XlsxReader.FormatException e) {
            return PlanImport.failure("O arquivo não é uma planilha Excel (.xlsx) válida"
                    + (name == null ? "" : ": \"" + name + "\"") + ". Use o template do Alfred, "
                    + "salvo no formato .xlsx.");
        } catch (IOException | SecurityException e) {
            return PlanImport.failure("Não consegui ler o arquivo escolhido. Tente copiá-lo para o celular "
                    + "(por exemplo, para Downloads) e importar de novo.");
        }
        try {
            return PlanImport.parse(workbook, templateExamples(ctx), name == null ? "planilha.xlsx" : name,
                    DateUtils.today());
        } catch (RuntimeException e) {
            // Qualquer surpresa no conteúdo vira mensagem, nunca um fechamento do app
            return PlanImport.failure("A planilha tem um conteúdo que não consegui interpretar. "
                    + "Confira se ela segue o template do Alfred.");
        }
    }

    /** Linhas de exemplo do template embutido, para a prévia avisar se foram esquecidas na planilha. */
    private static Set<String> templateExamples(Context ctx) {
        try (InputStream in = ctx.getAssets().open(TEMPLATE_ASSET)) {
            return PlanImport.exampleSignatures(XlsxReader.read(in));
        } catch (IOException | XlsxReader.FormatException e) {
            return Collections.emptySet();
        }
    }

    @Nullable
    private static String displayName(Context ctx, Uri uri) {
        try (Cursor c = ctx.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getString(0);
        } catch (RuntimeException ignored) {
            // sem nome, segue sem ele
        }
        return null;
    }

    private static long size(Context ctx, Uri uri) {
        try (Cursor c = ctx.getContentResolver().query(uri, new String[]{OpenableColumns.SIZE},
                null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
        } catch (RuntimeException ignored) {
            // tamanho desconhecido: o leitor também limita
        }
        return -1;
    }

    // ---------------- Template ----------------

    /**
     * Android 10+: salva uma cópia do template na pasta Downloads (sem pedir permissão).
     * Devolve o endereço do arquivo criado. Chame fora da thread principal.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    public static Uri saveTemplateToDownloads(Context ctx) throws IOException {
        ContentResolver resolver = ctx.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, TEMPLATE_NAME);
        values.put(MediaStore.Downloads.MIME_TYPE, XLSX_MIME);
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        values.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("MediaStore recusou o arquivo");
        try {
            copyTemplate(ctx, uri);
        } catch (IOException e) {
            resolver.delete(uri, null, null);
            throw e;
        }
        values.clear();
        values.put(MediaStore.Downloads.IS_PENDING, 0);
        resolver.update(uri, values, null, null);
        return uri;
    }

    /** Copia o template para um arquivo escolhido pelo usuário (Android 8 e 9). */
    public static void copyTemplate(Context ctx, Uri target) throws IOException {
        try (InputStream in = ctx.getAssets().open(TEMPLATE_ASSET);
             OutputStream out = ctx.getContentResolver().openOutputStream(target)) {
            if (out == null) throw new IOException("Destino indisponível");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }
}
