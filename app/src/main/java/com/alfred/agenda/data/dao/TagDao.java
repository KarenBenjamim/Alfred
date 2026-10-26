package com.alfred.agenda.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.alfred.agenda.data.entity.Tag;
import com.alfred.agenda.data.entity.TaskTag;

import java.util.List;

@Dao
public interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    LiveData<List<Tag>> observeAll();

    /** Todas as ligações tarefa-tag (são poucas; o filtro por tarefa é feito na tela). */
    @Query("SELECT * FROM task_tags")
    LiveData<List<TaskTag>> observeTaskTags();

    @Insert
    long insert(Tag tag);

    @Update
    void update(Tag tag);

    @Delete
    void delete(Tag tag);

    @Query("DELETE FROM task_tags WHERE taskId = :taskId")
    void clearTaskTags(long taskId);

    @Insert
    void insertTaskTags(List<TaskTag> links);
}
