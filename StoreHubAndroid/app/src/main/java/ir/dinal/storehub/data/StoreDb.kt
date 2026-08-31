package ir.dinal.storehub.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities=[ProductEntity::class,InventoryEntity::class,InventoryMovementEntity::class,SaleEntity::class,SaleItemEntity::class,TransferEntity::class,TransferItemEntity::class,PurchaseEntity::class,PurchaseItemEntity::class,IssuedCheckEntity::class,AppointmentEntity::class],
    version=2,
    exportSchema=false
)
abstract class StoreDb:RoomDatabase(){
    abstract fun dao():StoreHubDao
    companion object{
        @Volatile private var instance:StoreDb?=null

        private val MIGRATION_1_2=object:Migration(1,2){
            override fun migrate(db:SupportSQLiteDatabase){
                db.execSQL("ALTER TABLE ProductEntity ADD COLUMN productUrl TEXT")
            }
        }

        fun get(context:Context):StoreDb=instance?:synchronized(this){
            instance?:Room.databaseBuilder(context.applicationContext,StoreDb::class.java,"storehub-local.db")
                .addMigrations(MIGRATION_1_2)
                .build()
                .also{instance=it}
        }
    }
}
