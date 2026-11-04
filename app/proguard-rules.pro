# Regras do R8 para o build release. Room, Firebase e Credential Manager já trazem as
# próprias regras nas bibliotecas; aqui só o que o app usa por reflexão.

# Login com Google: o Credential Manager carrega o provedor do Play Services por nome
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }

# Logs de depuração fora do release (o AppLog já não escreve; isto remove as chamadas)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
