# Alfred 🎩

Agenda pessoal para Android inspirada no mordomo do Batman. Tudo fica salvo no próprio celular, sem servidor.

**Login e perfil.** As contas são locais: cada conta tem usuário e senha, e os dados (tarefas, dieta, hábitos, estudos) ficam separados por conta. A senha nunca é gravada, só um hash PBKDF2 com sal. No primeiro acesso de cada conta, o Alfred pergunta nome, forma de tratamento ("Senhor", "Senhora", "Senhorita" ou "Pelo nome"), peso e ícone de perfil. Todas as mensagens usam o tratamento escolhido (ex.: "Bom dia, Senhor Bruno" / "Bom dia, Bruno"). O avatar no canto superior esquerdo abre o **Perfil**, onde se edita tudo isso, ficam as **Configurações** e o botão **Sair**. Em *Configurações → Aparência* dá para escolher o tema **Escuro** (padrão), **Claro** ou **Seguir o sistema**.

**Navegação:** casinha no canto superior direito = **Início**; barra inferior = **Trabalho, Pessoal, Estudos, Hábitos e Dieta**.

| Tela | O que faz |
|---|---|
| **Início** | Nesta ordem: **resumo do dia** (anel e barras por categoria), **água** (copo que enche até a meta), **próximo compromisso pessoal** e **de trabalho** (o pendente de maior prioridade), **hábitos** (só as bolinhas da semana) e **compromissos do dia** (uma bolinha por tarefa de hoje; cheia = concluída; todas concluídas = amarelas). Tocar em hábitos ou numa linha de compromissos abre a aba correspondente. |
| **Hábitos** | **Água de hoje**: meta = peso do perfil × 35 ml/kg (ou o valor da aba "Água" da planilha), botões +200/+300/+500 ml e "Ajustar"; zera sozinha a cada dia. Abaixo, hábitos diários com bolinhas de segunda a domingo. Vem com 5 hábitos sugeridos. |
| **Dieta** | No primeiro acesso, pede a **planilha Excel (.xlsx)** da nutricionista no formato do template (botão **Baixar template Excel** salva uma cópia em Downloads). Antes de salvar, mostra uma **prévia** e avisa se sobraram linhas de exemplo do template. Depois: refeições, substituições, atalho **"Grupo X"** para a lista de substituição, **Água**, **Orientações** e **Importar nova planilha**. |
| **Estudos** | **Tema → Módulo → Assunto.** O **cronômetro** fica no assunto e guarda o tempo por dia (continua com o app fechado). Cada **módulo** soma o tempo dos seus assuntos (total e hoje) e mostra o **progresso** pelos assuntos marcados como concluídos (menu ⋮ do assunto). Toque no cabeçalho do módulo para recolher/abrir. |
| **Trabalho** | Modos Tarefas / Agenda, filtro **De / Até** com calendário, períodos **Manhã / Tarde**. |
| **Pessoal** | Igual ao Trabalho, com **Manhã / Tarde / Noite** e **tags coloridas** (o cartão, sem borda, recebe o degradê das tags). |

As tarefas avisam no dia da entrega, no início do período: manhã 08:00, tarde 13:00, noite 18:00.

**Expurgo:** sempre que o app abre, o Alfred apaga o que tem mais de 2 anos (tarefas, registros de refeições e hábitos, e o tempo de estudo por dia). Refeições, hábitos e assuntos de estudo cadastrados ficam.

**Tarefas de teste:** na versão de desenvolvimento, *Perfil → Configurações → Criar / Remover tarefas de teste*. Elas começam com "[Teste]" e incluem uma tarefa de 3 anos atrás, que some no próximo expurgo.

---

## 1. Pré-requisitos

Você precisa de:

- **JDK 17 ou mais novo** (recomendado: JDK 21)
- **Android SDK** com: `platform-tools`, `platforms;android-37.0` e `build-tools;37.0.0`
- Não precisa de Android Studio. O Gradle já vem no projeto (`gradlew`), e na primeira vez ele baixa sozinho o que faltar.

> Nesta máquina, os itens abaixo **já foram instalados e configurados**:
> - JDK 21 em `%LOCALAPPDATA%\Java\jdk-21.0.12.1+1`
> - Android SDK em `%LOCALAPPDATA%\Android\Sdk`
> - variáveis de usuário `JAVA_HOME`, `ANDROID_HOME` e o `PATH` (inclui o `adb`)
>
> **Feche e abra o VS Code de novo** para os terminais enxergarem essas variáveis.

### Instalando em outro computador (Windows, sem permissão de administrador)

1. **JDK:** baixe o zip do [Microsoft Build of OpenJDK 21](https://learn.microsoft.com/java/openjdk/download) e extraia, por exemplo, em `%LOCALAPPDATA%\Java`.
2. **Android SDK:** baixe as *Command line tools only* na [página do Android Studio](https://developer.android.com/studio#command-line-tools-only) e extraia de modo que fique assim:
   ```
   %LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat
   ```
3. **Variáveis de ambiente** (PowerShell; ajuste o caminho do JDK):
   ```powershell
   [Environment]::SetEnvironmentVariable('JAVA_HOME', "$env:LOCALAPPDATA\Java\jdk-21.0.12.1+1", 'User')
   [Environment]::SetEnvironmentVariable('ANDROID_HOME', "$env:LOCALAPPDATA\Android\Sdk", 'User')
   $p = [Environment]::GetEnvironmentVariable('Path', 'User')
   [Environment]::SetEnvironmentVariable('Path', "$p;$env:LOCALAPPDATA\Java\jdk-21.0.12.1+1\bin;$env:LOCALAPPDATA\Android\Sdk\platform-tools;$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin", 'User')
   ```
   Feche e abra o terminal.
4. **Licenças e pacotes do SDK:**
   ```powershell
   sdkmanager --licenses          # responda "y" para todas
   sdkmanager --install platform-tools platforms/android-37.0 build-tools/37.0.0
   ```
5. **local.properties** (opcional, se `ANDROID_HOME` estiver configurado). Na raiz do projeto, crie o arquivo com o caminho do SDK, escapando `:` e `\`:
   ```properties
   sdk.dir=C\:\\Users\\SEU_USUARIO\\AppData\\Local\\Android\\Sdk
   ```
   Esse arquivo é pessoal e fica fora do git (`.gitignore`).

---

## 2. Como compilar

No terminal do VS Code, na pasta do projeto:

```powershell
.\gradlew.bat assembleDebug        # Windows (PowerShell / cmd)
./gradlew assembleDebug            # Git Bash, Linux ou macOS
```

O APK fica em:

```
app\build\outputs\apk\debug\app-debug.apk
```

A primeira compilação demora alguns minutos (baixa o Gradle e as bibliotecas). As próximas levam segundos.

Outros comandos úteis:

| Comando | Para quê |
|---|---|
| `.\gradlew.bat clean` | Apaga a pasta `build` (use se algo ficar estranho depois de mover arquivos) |
| `.\gradlew.bat lintDebug` | Procura problemas comuns; relatório em `app\build\reports\lint-results-debug.html` |
| `.\gradlew.bat installDebug` | Compila **e** instala no celular conectado, de uma vez |

---

## 3. Como ativar a depuração USB no celular

1. Abra **Configurações → Sobre o telefone**.
2. Toque **7 vezes** em **Número da versão** (em alguns aparelhos fica em *Informações do software*; na Xiaomi é *Versão do MIUI/HyperOS*). Vai aparecer "Você agora é um desenvolvedor".
3. Volte e entre em **Configurações → Sistema → Opções do desenvolvedor**. Em Samsung/Xiaomi, procure "Opções do desenvolvedor" na busca das configurações.
4. Ative **Depuração USB**.
5. Conecte o celular no PC com um cabo **de dados** (alguns cabos só carregam).
6. No celular, aparece "Permitir depuração USB?". Marque *Sempre permitir neste computador* e toque em **Permitir**.

> Xiaomi/Redmi/POCO: ative também **"Instalar via USB"** nas opções do desenvolvedor (exige login na conta Mi).

---

## 4. Como instalar no celular via cabo (adb)

```powershell
adb devices
```
O celular deve aparecer como `device`. Se aparecer `unauthorized`, desbloqueie a tela e aceite o aviso do passo 6 acima.

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```
O `-r` substitui a versão anterior **mantendo seus dados**.

Ao abrir o Alfred pela primeira vez:

1. Permita as **notificações**.
2. Quando o Alfred pedir, abra **"Alarmes e lembretes"** e ative para o Alfred. Sem isso, os avisos podem chegar com alguns minutos de atraso. Para ativar depois: *Configurações → Apps → Alfred → Alarmes e lembretes*.
3. Em aparelhos que matam apps em segundo plano (Xiaomi, Samsung, Motorola...), coloque o Alfred como **"Sem restrições"** em *Configurações → Apps → Alfred → Bateria*. Assim os lembretes não somem.

> **Atenção:** desinstalar o app (`adb uninstall com.alfred.agenda`) apaga todos os dados. Para atualizar, use sempre `adb install -r`.

---

## Planilha da dieta

O plano não vem mais embutido no app: cada conta importa a sua planilha na aba **Dieta** (ou em *Configurações → Importar planilha da dieta*). O formato é o do template (`app/src/main/assets/plano_alimentar_template.xlsx`, cópia de `doc/template/`):

- abas lidas pelo **nome**: "Plano Alimentar", "Água", "Lista de Substituição" e "Observações" (a aba "Instruções" é ignorada);
- cabeçalhos na **linha 4**, dados a partir da **linha 5**;
- em "Substituições diretas", as opções separadas por `" | "`;
- em "Ou escolha da lista", o formato `2,5 porções do Grupo 4`;
- grupos da lista no formato `4 - Carnes e Proteínas`.

Se algo estiver fora do formato, o app explica o problema com aba, linha e coluna. Quando a nutricionista mudar o plano, use **Importar nova planilha**.

---

## 5. Estrutura do projeto

```
app/src/main/java/com/alfred/agenda/
├── AlfredApp.java              # Início do app: cria os canais de notificação
├── MainActivity.java           # Login/onboarding/app, cabeçalho, barra inferior, permissões
├── account/
│   ├── AccountStore.java       # Contas locais e sessão (um banco por conta)
│   ├── PasswordHasher.java     # Hash PBKDF2 da senha
│   └── UserProfile.java        # Nome, tratamento, peso e avatar de cada conta
├── data/
│   ├── entity/                 # Tabelas do banco (Room): Meal, Habit, Task, Tag, StudySubject...
│   ├── dao/                    # Consultas SQL de cada tabela
│   ├── AppDatabase.java        # Banco SQLite da conta logada ("alfred.db" para a 1ª conta)
│   ├── TestData.java           # Tarefas de exemplo (só em debug)
│   ├── PlanImport.java         # Lê e valida a planilha da dieta (abas, cabeçalhos, exemplos)
│   ├── NutritionPlan.java      # Plano importado, guardado num arquivo privado por conta
│   └── AlfredRepository.java   # Único ponto de acesso aos dados + agenda os alarmes
├── notifications/
│   ├── AlarmScheduler.java     # Calcula o próximo horário e agenda no AlarmManager
│   ├── AlarmReceiver.java      # Dispara a notificação e reagenda a próxima
│   ├── BootReceiver.java       # Reagenda tudo depois que o celular reinicia
│   └── NotificationHelper.java # Canais e montagem das notificações
├── ui/
│   ├── auth/                   # Login e primeiro acesso (onboarding)
│   ├── profile/                # Perfil (avatar, tratamento, peso, sair)
│   ├── today/                  # Tela Início
│   ├── diet/                   # Aba Dieta (Fragment, ViewModel, Adapter, Dialog)
│   ├── habits/                 # Aba Hábitos (e as regras de "todos concluídos")
│   ├── study/                  # Aba Estudos (temas, assuntos, cronômetro, histórico)
│   └── tasks/                  # Abas Trabalho e Pessoal (mesma tela, muda a categoria)
└── util/
    ├── AlfredPhrases.java      # Todas as frases do Alfred: acrescente as suas aqui
    ├── Honorific.java          # Forma de tratamento: o único lugar que monta "senhor"/"Bruno"...
    ├── WaterGoal.java          # Meta de água pelo peso
    ├── XlsxReader.java         # Leitor de .xlsx sem bibliotecas
    ├── DateUtils.java          # Formatação e cálculo de datas
    ├── LiveDataUtils.java
    ├── WeekDots.java           # Bolinhas Seg..Dom (Hábitos e Hoje)
    ├── TagColors.java          # Paleta de 8 cores das tags
    └── UiUtils.java            # Seletores de data/hora, confirmação de exclusão

app/src/main/res/
├── layout/                     # Telas e itens de lista (XML)
├── values/strings.xml          # Textos da interface ({voc}/{Voc} = forma de tratamento)
├── values/colors.xml           # Paleta (preto, cinza, amarelo nos destaques e as 8 cores das tags)
├── values/themes.xml           # Tema Material 3 (claro e escuro; cores em values/ e values-night/)
├── drawable/                   # Ícones vetoriais
├── drawable-nodpi/             # Avatares e arte do Alfred usada dentro do app (cópias reduzidas de doc/img)
└── mipmap-anydpi/              # Ícone do app (gravata-borboleta, vetores em drawable/ic_launcher_*)
```

**Fluxo dos dados:** Tela (Fragment) → ViewModel → AlfredRepository → DAO → SQLite.
As telas observam `LiveData`, então qualquer alteração no banco aparece na hora em todas as abas.

### Dicas para evoluir

- **Novas frases do Alfred:** edite `util/AlfredPhrases.java`. Cada lista sorteia uma opção. Use `{voc}` (ou `{Voc}` no início da frase) onde vai o tratamento, sempre como vocativo entre vírgulas, e exiba com `Honorific.str()`/`Honorific.apply()`.
- **Mudou uma entidade (novo campo/tabela)?** Aumente `version` em `AppDatabase.java` e crie uma `Migration`. Sem isso, o app fecha ao abrir em quem já tinha a versão anterior.
- **Horário dos avisos:** tarefas em `PERIOD_REMINDER_MINUTES` (`Task.java`).
- **Cores:** `res/values/colors.xml`. O cinza do dia a dia é `accent`, o amarelo dos destaques é `highlight` e as cores das tags são `tag_*`.
