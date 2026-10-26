package com.alfred.agenda.ui.diet;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.alfred.agenda.data.AlfredRepository;
import com.alfred.agenda.data.NutritionPlan;
import com.alfred.agenda.data.PlanImport;
import com.alfred.agenda.data.entity.Meal;
import com.alfred.agenda.data.entity.MealItem;
import com.alfred.agenda.data.entity.MealLog;
import com.alfred.agenda.data.entity.MealWithItems;
import com.alfred.agenda.util.DateUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DietViewModel extends AndroidViewModel {

    private final AlfredRepository repo;
    private final long today = DateUtils.today();
    /** Leitura da planilha fora da thread principal (pode levar alguns segundos). */
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    public final LiveData<List<MealWithItems>> meals;
    public final LiveData<List<MealLog>> logsToday;

    /** Resultado da leitura da planilha, esperando a prévia (null = nada pendente). */
    private final MutableLiveData<PlanImport.Result> importResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> importing = new MutableLiveData<>(false);

    public DietViewModel(@NonNull Application app) {
        super(app);
        repo = AlfredRepository.get(app);
        meals = repo.observeMeals();
        logsToday = repo.observeMealLogs(today);
    }

    public LiveData<PlanImport.Result> importResult() {
        return importResult;
    }

    public LiveData<Boolean> importing() {
        return importing;
    }

    /** Lê a planilha escolhida e publica o resultado em importResult (para a prévia). */
    public void readSpreadsheet(Uri uri) {
        if (Boolean.TRUE.equals(importing.getValue())) return;
        importing.setValue(true);
        worker.execute(() -> {
            PlanImport.Result result = PlanFiles.readPlan(getApplication(), uri);
            importing.postValue(false);
            importResult.postValue(result);
        });
    }

    /** A prévia já foi mostrada: esquece o resultado (não reabre ao girar a tela). */
    public void consumeImportResult() {
        importResult.setValue(null);
    }

    /** Salva o plano confirmado na prévia. */
    public void confirmImport(NutritionPlan plan, Runnable onSuccess, Runnable onError) {
        repo.importPlan(plan, onSuccess, onError);
    }

    public void save(Meal meal, List<MealItem> items) {
        repo.saveMeal(meal, items);
    }

    public void delete(Meal meal) {
        repo.deleteMeal(meal);
    }

    /** @param onSuccess chamado só se a gravação deu certo (pode ser null) */
    public void setDone(long mealId, boolean done, @Nullable Runnable onSuccess) {
        repo.setMealDone(mealId, today, done, onSuccess);
    }

    @Override
    protected void onCleared() {
        worker.shutdown();
    }
}
