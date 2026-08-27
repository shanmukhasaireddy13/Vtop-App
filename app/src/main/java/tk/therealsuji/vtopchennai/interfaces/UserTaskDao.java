package tk.therealsuji.vtopchennai.interfaces;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import tk.therealsuji.vtopchennai.models.UserTask;

@Dao
public interface UserTaskDao {
    @Insert
    Completable insert(UserTask userTask);

    @Insert
    Completable insertAll(List<UserTask> userTasks);

    @Update
    Completable update(UserTask userTask);

    @Delete
    Completable delete(UserTask userTask);

    @Query("SELECT * FROM user_tasks WHERE day_of_week = :dayOfWeek ORDER BY start_time ASC")
    Single<List<UserTask>> getTasksForDay(int dayOfWeek);

    @Query("SELECT * FROM user_tasks ORDER BY day_of_week ASC, start_time ASC")
    Single<List<UserTask>> getAllTasks();

    @Query("DELETE FROM user_tasks")
    Completable deleteAll();
}
