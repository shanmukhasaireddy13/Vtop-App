package tk.therealsuji.vtopchennai.interfaces;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import tk.therealsuji.vtopchennai.models.Attendance;

@Dao
public interface AttendanceDao {
    @Insert
    Completable insert(List<Attendance> attendance);

    @Query("DELETE FROM attendance")
    Completable delete();

    @Query("DELETE FROM attendance")
    void deleteSync();

    @Insert
    void insertSync(List<Attendance> attendance);

    @Transaction
    default void replaceAll(List<Attendance> attendance) {
        deleteSync();
        insertSync(attendance);
    }
}
