package com.alfred.agenda.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.alfred.agenda.data.entity.WaterLog;

/** Consumo de água por dia. */
@Dao
public interface WaterDao {

    /** Total do dia (null se ainda não bebeu nada registrado). */
    @Query("SELECT * FROM water_logs WHERE epochDay = :epochDay")
    LiveData<WaterLog> observe(long epochDay);

    @Query("SELECT * FROM water_logs WHERE epochDay = :epochDay")
    WaterLog getSync(long epochDay);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void put(WaterLog log);

    /** Expurgo: apaga dias antes de :limitDay. */
    @Query("DELETE FROM water_logs WHERE epochDay < :limitDay")
    int deleteOlderThan(long limitDay);
}
