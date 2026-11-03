package com.alfred.agenda.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.json.JSONObject;
import org.junit.Test;

/** Importar um arquivo de backup: só aceita backups do Alfred e não duplica o que já foi migrado. */
public class BackupImportTest {

    private static JSONObject backup(Object... extra) throws Exception {
        JSONObject b = new JSONObject();
        b.put("app", "Alfred");
        b.put("formato", LocalBackup.FORMAT);
        b.put("usuario", "Milainy");
        b.put("tabelas", new JSONObject());
        for (int i = 0; i < extra.length; i += 2) b.put((String) extra[i], extra[i + 1]);
        return b;
    }

    private static void assertInvalid(JSONObject b) {
        try {
            LegacyMigration.validate(b);
            fail("deveria recusar: " + b);
        } catch (LegacyMigration.InvalidBackup expected) {
            // ok
        }
    }

    @Test
    public void alfredBackupIsAccepted() throws Exception {
        LegacyMigration.validate(backup());
    }

    @Test
    public void otherFilesAreRefused() throws Exception {
        assertInvalid(backup("app", "OutroApp"));
        assertInvalid(backup("formato", LocalBackup.FORMAT + 1)); // de uma versão mais nova do app
        assertInvalid(backup("formato", 0));
        JSONObject semTabelas = backup();
        semTabelas.remove("tabelas");
        assertInvalid(semTabelas);
        assertInvalid(new JSONObject("{}"));
    }

    @Test
    public void backupOfAnAccountUsesTheSameIdsAsMigratingIt() throws Exception {
        // "Trazer dados" da conta 1 usa o prefixo "u1"; o backup dela também: importar depois não duplica
        assertEquals("u1", LegacyMigration.scopeOf(backup("contaId", 1)));
        assertEquals(LegacyMigration.stableId("u1", "tasks", 5),
                LegacyMigration.stableId(LegacyMigration.scopeOf(backup("contaId", 1)), "tasks", 5));
    }

    @Test
    public void oldBackupWithoutAccountNumberUsesTheUsername() throws Exception {
        assertEquals("backup:milainy", LegacyMigration.scopeOf(backup()));
        assertEquals("backup:milainy", LegacyMigration.scopeOf(backup("usuario", "  MILAINY ")));
    }
}
