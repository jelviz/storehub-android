package ir.dinal.storehub.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities=[ProductEntity::class,InventoryEntity::class,InventoryMovementEntity::class,SaleEntity::class,SaleItemEntity::class,TransferEntity::class,TransferItemEntity::class,PurchaseEntity::class,PurchaseItemEntity::class,IssuedCheckEntity::class,AppointmentEntity::class],
    version=1,
    exportSchema=false
)
abstract class StoreDb:RoomDatabase(){
    abstract fun dao():StoreHubDao
    companion object{
        @Volatile private var instance:StoreDb?=null
        fun get(context:Context):StoreDb=instance?:synchronized(this){instance?:Room.databaseBuilder(context.applicationContext,StoreDb::class.java,"storehub-local.db").build().also{instance=it}}
    }
}
