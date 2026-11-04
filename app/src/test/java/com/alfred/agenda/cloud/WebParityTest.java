package com.alfred.agenda.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Garante que os nomes de coleção do CloudSchema.java, do web/js/schema.js e da lista
 * isKnownCollection do firestore.rules são os mesmos (uma coleção esquecida em um dos
 * três lados = gravações recusadas ou dados invisíveis).
 */
public class WebParityTest {

    private static String read(String relative) throws Exception {
        // Gradle roda os testes com a pasta do módulo (app/) como diretório de trabalho
        File f = new File(relative);
        if (!f.exists()) f = new File("..", relative);
        if (!f.exists()) f = new File("../..", relative);
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static Map<String, String> javaCollections() {
        Map<String, String> m = new HashMap<>();
        m.put("USERS", CloudSchema.USERS);
        m.put("TASKS", CloudSchema.TASKS);
        m.put("TAGS", CloudSchema.TAGS);
        m.put("MEALS", CloudSchema.MEALS);
        m.put("PLAN", CloudSchema.PLAN);
        m.put("HABITS", CloudSchema.HABITS);
        m.put("WATER", CloudSchema.WATER);
        m.put("TOPICS", CloudSchema.TOPICS);
        m.put("MODULES", CloudSchema.MODULES);
        m.put("SUBJECTS", CloudSchema.SUBJECTS);
        return m;
    }

    @Test
    public void webCollectionNamesEqualAndroidOnes() throws Exception {
        String js = read("web/js/schema.js");
        Matcher block = Pattern.compile("export const COL = \\{(.*?)\\};", Pattern.DOTALL).matcher(js);
        assertTrue("COL não encontrado em schema.js", block.find());
        Matcher kv = Pattern.compile("([A-Z]+):\\s*'([^']+)'").matcher(block.group(1));
        Map<String, String> web = new HashMap<>();
        while (kv.find()) web.put(kv.group(1), kv.group(2));
        assertEquals(javaCollections(), web);
    }

    @Test
    public void firestoreRulesAllowExactlyTheCollectionsTheAppUses() throws Exception {
        String rules = read("firestore.rules");
        Matcher list = Pattern.compile("col in \\[(.*?)\\]", Pattern.DOTALL).matcher(rules);
        assertTrue("isKnownCollection não encontrado em firestore.rules", list.find());
        Set<String> allowed = new LinkedHashSet<>();
        Matcher q = Pattern.compile("'([^']+)'").matcher(list.group(1));
        while (q.find()) allowed.add(q.group(1));

        Set<String> used = new LinkedHashSet<>(javaCollections().values());
        used.remove(CloudSchema.USERS); // a raiz não é subcoleção
        assertEquals(used, allowed);
    }

    @Test
    public void webAndAndroidAgreeOnTheAvatarAndColorCounts() throws Exception {
        String js = read("web/js/schema.js");
        // 4 avatares e 8 cores de tag/matéria: o clamp 0..7 da web e o Android precisam concordar
        assertEquals(4, count(js, "'img/"));
        int tags = js.indexOf("TAG_COLORS = [");
        assertEquals(8, count(js.substring(tags, js.indexOf("];", tags)), "', '#"));
        int study = js.indexOf("STUDY_COLORS = [");
        assertEquals(8, count(js.substring(study, js.indexOf("];", study)), "', '#"));
        // 5 grupos de tag do Trabalho, iguais a Tag.GROUP_COUNT
        int groups = js.indexOf("WORK_GROUPS = [");
        assertEquals(com.alfred.agenda.data.entity.Tag.GROUP_COUNT,
                count(js.substring(groups, js.indexOf("];", groups)), "  ['"));
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) n++;
        return n;
    }
}
