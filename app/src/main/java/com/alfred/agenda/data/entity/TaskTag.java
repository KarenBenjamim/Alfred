package com.alfred.agenda.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/**
 * Liga uma tarefa a uma tag (uma tarefa pode ter várias tags).
 * Apagar a tarefa ou a tag apaga a ligação junto (CASCADE).
 */
@Entity(tableName = "task_tags",
        primaryKeys = {"taskId", "tagId"},
        foreignKeys = {
                @ForeignKey(entity = Task.class, parentColumns = "id", childColumns = "taskId",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Tag.class, parentColumns = "id", childColumns = "tagId",
                        onDelete = ForeignKey.CASCADE)},
        indices = @Index("tagId"))
public class TaskTag {

    public long taskId;

    public long tagId;
}
