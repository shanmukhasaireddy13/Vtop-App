package tk.therealsuji.vtopchennai.interfaces;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import tk.therealsuji.vtopchennai.models.Receipt;

@Dao
public interface ReceiptsDao {
    @Insert
    Completable insert(List<Receipt> receipts);

    @Query("DELETE FROM receipts")
    Completable deleteAll();

    @Query("DELETE FROM receipts")
    void deleteAllSync();

    @Insert
    void insertSync(List<Receipt> receipts);

    @Transaction
    default void replaceAll(List<Receipt> receipts) {
        deleteAllSync();
        insertSync(receipts);
    }

    @Query("SELECT * FROM receipts ORDER BY date DESC, number DESC")
    Single<List<Receipt>> getReceipts();
}
