package com.alfred.agenda;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.alfred.agenda.account.AccountStore;
import com.alfred.agenda.account.UserProfile;
import com.alfred.agenda.data.AlfredRepository;
import com.alfred.agenda.data.NutritionPlan;
import com.alfred.agenda.data.entity.Task;
import com.alfred.agenda.notifications.AlarmScheduler;
import com.alfred.agenda.notifications.NotificationHelper;
import com.alfred.agenda.ui.auth.LoginFragment;
import com.alfred.agenda.ui.auth.OnboardingFragment;
import com.alfred.agenda.ui.common.AlfredBottomNav;
import com.alfred.agenda.ui.diet.DietFragment;
import com.alfred.agenda.ui.habits.HabitsFragment;
import com.alfred.agenda.ui.profile.ProfileFragment;
import com.alfred.agenda.ui.settings.SettingsFragment;
import com.alfred.agenda.ui.study.StudyFragment;
import com.alfred.agenda.ui.tasks.TasksFragment;
import com.alfred.agenda.ui.today.TodayFragment;
import com.alfred.agenda.util.AlfredMessages;
import com.alfred.agenda.util.Avatars;
import com.alfred.agenda.util.Honorific;
import com.alfred.agenda.util.UiUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;

/**
 * Activity única do app. Decide o que mostrar:
 *  - ninguém logado: tela de login;
 *  - primeiro acesso da conta: onboarding (nome, tratamento, peso e ícone de perfil);
 *  - senão, o app: cabeçalho (avatar à esquerda abre o Perfil; casinha à direita abre o Início)
 *    e a barra inferior com Trabalho, Pessoal, Estudos, Hábitos e Dieta.
 * Também cuida das permissões e do expurgo, depois do login.
 */
public class MainActivity extends AppCompatActivity {

    /** Extra usado pelas notificações para abrir uma aba específica. */
    public static final String EXTRA_TAB = "tab";

    // Os números não mudam: as notificações antigas guardam esses valores.
    public static final int TAB_TODAY = 0;
    public static final int TAB_DIET = 1;
    public static final int TAB_HABITS = 2;
    public static final int TAB_STUDY = 3;
    public static final int TAB_WORK = 4;
    public static final int TAB_PERSONAL = 5;
    public static final int TAB_SETTINGS = 6;
    public static final int TAB_PROFILE = 7;

    /** Ações que uma tela pode pedir a outra aba (ex.: o card vazio do Início cria uma tarefa). */
    public static final String ACTION_NEW_TASK = "new_task";
    public static final String ACTION_IMPORT_PLAN = "import_plan";

    private static final String STATE_TAB = "current_tab";
    private static final String STATE_PREVIOUS_TAB = "previous_tab";
    private static final String STATE_STARTED = "started";
    private static final String PREFS = "alfred_prefs";
    private static final String PREF_EXACT_ALARM_ASKED = "exact_alarm_asked";

    private static final String TAG_LOGIN = "auth_login";
    private static final String TAG_ONBOARDING = "auth_onboarding";

    private View header;
    private View navDivider;
    private TextView headerSubtitle;
    private ShapeableImageView headerAvatar;
    private ImageButton headerHome;
    private AlfredBottomNav bottomNav;
    private int currentTab = -1;
    private int previousTab = TAB_TODAY;
    /** true depois que as tarefas de abertura (permissões, expurgo...) rodaram nesta Activity. */
    private boolean started;
    @Nullable
    private String pendingAction;
    private int pendingActionTab = -1;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) UiUtils.toast(this, Honorific.str(this, R.string.notifications_denied));
                askExactAlarmIfNeeded();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        header = findViewById(R.id.header);
        navDivider = findViewById(R.id.nav_divider);
        headerSubtitle = findViewById(R.id.header_subtitle);
        headerAvatar = findViewById(R.id.header_avatar);
        headerHome = findViewById(R.id.header_home);
        bottomNav = findViewById(R.id.bottom_nav);

        // O app desenha atrás das barras do sistema (edge-to-edge, obrigatório no Android 15+).
        // Aqui aplicamos um recuo para nada ficar escondido atrás da barra de status/navegação.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        // Barra inferior: as 5 abas, nesta ordem
        bottomNav.addItem(TAB_WORK, R.drawable.ic_work, R.string.tab_work);
        bottomNav.addItem(TAB_PERSONAL, R.drawable.ic_personal, R.string.tab_personal);
        bottomNav.addItem(TAB_STUDY, R.drawable.ic_study, R.string.tab_study);
        bottomNav.addItem(TAB_HABITS, R.drawable.ic_habits, R.string.tab_habits);
        bottomNav.addItem(TAB_DIET, R.drawable.ic_diet, R.string.tab_diet);
        bottomNav.setOnSelectListener(this::showTab);

        headerAvatar.setOnClickListener(v -> showTab(TAB_PROFILE));
        headerHome.setOnClickListener(v -> showTab(TAB_TODAY));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                int back = backTarget();
                if (back >= 0) {
                    showTab(back);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        if (savedInstanceState != null) {
            currentTab = -1;
            previousTab = savedInstanceState.getInt(STATE_PREVIOUS_TAB, TAB_TODAY);
            started = savedInstanceState.getBoolean(STATE_STARTED, false);
        }
        route(savedInstanceState != null ? savedInstanceState.getInt(STATE_TAB, TAB_TODAY)
                : getIntent().getIntExtra(EXTRA_TAB, TAB_TODAY));
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, currentTab < 0 ? TAB_TODAY : currentTab);
        outState.putInt(STATE_PREVIOUS_TAB, previousTab);
        outState.putBoolean(STATE_STARTED, started);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        // App já aberto e o usuário tocou numa notificação: vai para a aba correspondente
        if (intent.hasExtra(EXTRA_TAB) && isInApp()) showTab(intent.getIntExtra(EXTRA_TAB, TAB_TODAY));
    }

    // ---------------- Login, onboarding e app ----------------

    /** Mostra o login, o onboarding ou o app, conforme a sessão e o perfil. */
    private void route(int tab) {
        if (!AccountStore.isLoggedIn(this)) {
            showAuthScreen(TAG_LOGIN);
        } else if (!UserProfile.current(this).onboarded) {
            showAuthScreen(TAG_ONBOARDING);
        } else {
            enterApp(tab);
        }
    }

    /** Login/onboarding ocupam a tela inteira, sem cabeçalho nem barra inferior. */
    private void showAuthScreen(String tag) {
        header.setVisibility(View.GONE);
        navDivider.setVisibility(View.GONE);
        bottomNav.setVisibility(View.GONE);
        currentTab = -1;
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (current == null || !tag.equals(current.getTag())) {
            Fragment screen = TAG_LOGIN.equals(tag) ? new LoginFragment() : new OnboardingFragment();
            getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.fragment_container, screen, tag)
                    .commitNow();
        }
    }

    private void enterApp(int tab) {
        header.setVisibility(View.VISIBLE);
        navDivider.setVisibility(View.VISIBLE);
        bottomNav.setVisibility(View.VISIBLE);
        refreshProfile();
        showTab(tab);

        AlfredRepository repo = AlfredRepository.get(this);
        if (!started) {
            started = true;
            requestPermissions();
            // "Teoria do expurgo": apaga o que tem mais de 2 anos
            repo.purgeOld(removed -> {
                if (removed > 0) UiUtils.toast(this, Honorific.plural(this, R.plurals.purge_done, removed, removed));
            });
        }
        // Garante que todos os lembretes estejam agendados (barato e evita alarmes perdidos)
        repo.rescheduleAll();
        // Se um cronômetro de estudo ficou rodando, volta a mostrar a notificação dele
        repo.refreshStudyNotification();
    }

    private boolean isInApp() {
        return header.getVisibility() == View.VISIBLE;
    }

    /** Chamado pela tela de login depois de entrar (ou criar a conta). */
    public void onLoggedIn() {
        started = false;
        route(TAB_TODAY);
    }

    /** Chamado pelo onboarding ao concluir o primeiro acesso. */
    public void onOnboardingDone() {
        route(TAB_TODAY);
        showMessage(Honorific.str(this, R.string.onboarding_welcome));
    }

    /**
     * Sai da conta: pausa o cronômetro, cancela os lembretes deste usuário,
     * fecha o banco dele e volta para o login.
     */
    public void logout() {
        AlfredRepository.get(this).logout(() -> {
            AccountStore.logout(this);
            AlfredRepository.reset();
            NutritionPlan.reset();
            previousTab = TAB_TODAY;
            started = false;
            route(TAB_TODAY);
        });
    }

    /** Atualiza o que depende do perfil (avatar do cabeçalho; os textos usam o tratamento ao desenhar). */
    public void refreshProfile() {
        UserProfile profile = UserProfile.current(this);
        headerAvatar.setImageResource(Avatars.drawable(profile.avatar));
        headerAvatar.setContentDescription(getString(R.string.profile_open, profile.name));
        if (currentTab >= 0) headerSubtitle.setText(subtitleFor(currentTab));
    }

    // ---------------- Navegação ----------------

    /**
     * Para onde o "voltar" leva: Configurações -> Perfil -> aba anterior; qualquer aba -> Início.
     * -1 = deixa o sistema tratar (fecha o app no Início, no login e no onboarding).
     */
    private int backTarget() {
        if (!isInApp()) return -1;
        if (currentTab == TAB_SETTINGS) return TAB_PROFILE;
        if (currentTab == TAB_PROFILE) return previousTab;
        if (currentTab != TAB_TODAY) return TAB_TODAY;
        return -1;
    }

    /** Mostra a aba pedida (se ainda não for a atual) e atualiza cabeçalho e barra inferior. */
    public void showTab(int tab) {
        if (!isInApp()) return;
        // Lembra de onde veio, para o "voltar" do Perfil
        if (tab == TAB_PROFILE && currentTab >= 0 && currentTab != TAB_PROFILE && currentTab != TAB_SETTINGS) {
            previousTab = currentTab;
        }

        String tag = "tab_" + tab;
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (current == null || !tag.equals(current.getTag())) {
            getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.fragment_container, createFragment(tab), tag)
                    .commitNow();
        }
        currentTab = tab;
        headerSubtitle.setText(subtitleFor(tab));
        bottomNav.setSelectedTab(tab);
        headerHome.setImageTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this,
                tab == TAB_TODAY ? R.color.highlight : R.color.text_secondary)));
        headerAvatar.setStrokeColor(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this,
                tab == TAB_PROFILE || tab == TAB_SETTINGS ? R.color.highlight : R.color.outline)));
    }

    /**
     * Abre uma aba pedindo uma ação a ela (ex.: o card vazio "Próximo compromisso pessoal"
     * abre Pessoal já com o formulário de nova tarefa). A aba consome a ação em consumeAction().
     */
    public void openTab(int tab, @Nullable String action) {
        pendingAction = action;
        pendingActionTab = tab;
        if (currentTab == tab) {
            // Recria a aba para ela ler a ação
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, createFragment(tab), "tab_" + tab)
                    .commitNow();
        } else {
            showTab(tab);
        }
    }

    /** Devolve (e limpa) a ação pendente para a aba, se houver. */
    @Nullable
    public String consumeAction(int tab) {
        if (pendingActionTab != tab) return null;
        String action = pendingAction;
        pendingAction = null;
        pendingActionTab = -1;
        return action;
    }

    private static Fragment createFragment(int tab) {
        switch (tab) {
            case TAB_DIET: return new DietFragment();
            case TAB_HABITS: return new HabitsFragment();
            case TAB_STUDY: return new StudyFragment();
            case TAB_WORK: return TasksFragment.newInstance(Task.CATEGORY_WORK);
            case TAB_PERSONAL: return TasksFragment.newInstance(Task.CATEGORY_PERSONAL);
            case TAB_SETTINGS: return new SettingsFragment();
            case TAB_PROFILE: return new ProfileFragment();
            default: return new TodayFragment();
        }
    }

    private String subtitleFor(int tab) {
        switch (tab) {
            case TAB_DIET: return getString(R.string.subtitle_diet);
            case TAB_HABITS: return getString(R.string.subtitle_habits);
            case TAB_STUDY: return Honorific.str(this, R.string.subtitle_study);
            case TAB_WORK: return getString(R.string.subtitle_work);
            case TAB_PERSONAL: return getString(R.string.subtitle_personal);
            case TAB_SETTINGS: return getString(R.string.settings_title);
            case TAB_PROFILE: return getString(R.string.profile_title);
            default: return getString(R.string.subtitle_today);
        }
    }

    // ---------------- Mensagens do Alfred ----------------

    /** Snackbar discreta acima da barra inferior. */
    public void showMessage(String text) {
        AlfredMessages.show(this, text, bottomNav);
    }

    /**
     * Depois de concluir uma meta com sucesso: mensagem especial se o dia ficou completo,
     * senão uma mensagem curta. Só é chamado quando a gravação deu certo.
     */
    public void celebrateGoal() {
        AlfredRepository.get(this).computeTodayProgress(progress ->
                showMessage(progress.allDone() ? AlfredMessages.allGoalsDone(this) : AlfredMessages.goalDone(this)));
    }

    /** true na versão de desenvolvimento (instalada com gradlew installDebug). */
    public boolean isDebugBuild() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    // ---------------- Permissões ----------------

    /** 1º notificações (Android 13+), depois alarmes exatos (Android 12+). */
    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !NotificationHelper.hasPermission(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            askExactAlarmIfNeeded();
        }
    }

    /**
     * A permissão de alarme exato não aparece num pop-up comum: é preciso abrir a tela
     * de Configurações. Perguntamos uma única vez para não importunar.
     */
    private void askExactAlarmIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        if (new AlarmScheduler(this).canScheduleExact()) return;

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(PREF_EXACT_ALARM_ASKED, false)) return;
        prefs.edit().putBoolean(PREF_EXACT_ALARM_ASKED, true).apply();

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.exact_alarm_title)
                .setMessage(Honorific.str(this, R.string.exact_alarm_msg))
                .setNegativeButton(R.string.exact_alarm_later, null)
                .setPositiveButton(R.string.exact_alarm_open, (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .show();
    }
}
