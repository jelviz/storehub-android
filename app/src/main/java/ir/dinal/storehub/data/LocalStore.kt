package ir.dinal.storehub.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

class LocalStore private constructor(private val context:Context){
    private val db=StoreDb.get(context); private val dao=db.dao()
    companion object{
        const val WAREHOUSE_STORE=1; const val WAREHOUSE_DEPOT=2
        @Volatile private var instance:LocalStore?=null
        fun get(context:Context)=instance?:synchronized(this){instance?:LocalStore(context.applicationContext).also{instance=it}}
        fun warehouseName(id:Int)=if(id==WAREHOUSE_DEPOT)"دپو" else "مغازه"
    }

    suspend fun dashboard():DashboardLocal{
        val products=dao.products();val inv=dao.allInventory().associateBy{it.productId to it.warehouseId};val storeProducts=products.filter{it.isEnabledForStore};val qs=storeProducts.map{it to (inv[it.id to WAREHOUSE_STORE]?.quantity?:0.0)}
        val start=LocalDate.now(ZoneId.of("Asia/Tehran")).atStartOfDay(ZoneId.of("Asia/Tehran")).toInstant().toEpochMilli();val todaySales=dao.sales().filter{it.createdAt>=start}.sumOf{it.total-it.returnedTotal}
        val todayEpoch=LocalDate.now(ZoneId.of("Asia/Tehran")).toEpochDay();val due=dao.checks().count{it.status==1 && it.dueEpochDay<=todayEpoch+it.reminderDaysBefore && it.dueEpochDay>=todayEpoch-1}
        val todayP=Jalali.format(Jalali.today());val ap=dao.appointments().count{it.status==1&&it.datePersian==todayP}
        return DashboardLocal(products.size,storeProducts.size,qs.count{it.second<=it.first.lowStockThreshold&&it.second>0},qs.count{it.second<=0},todaySales,dao.transfers().count{it.status<3},due,ap)
    }

    suspend fun products(q:String="")=dao.products(q.trim())
    suspend fun saveProduct(p:ProductEntity,openingQuantity:Double=0.0):Long=db.withTransaction{
        val id=if(p.id==0L){val x=dao.insertProduct(p.copy(internalCode=""));dao.setInternalCode(x,"M-$x");x}else{dao.updateProduct(p.copy(updatedAt=System.currentTimeMillis()));p.id}
        if(p.isEnabledForStore){dao.enableStore(id);ensureInventory(id,WAREHOUSE_STORE);if(openingQuantity!=0.0)changeInventory(id,WAREHOUSE_STORE,openingQuantity,1,"OPENING","موجودی اولیه")}
        id
    }
    suspend fun enableStore(productId:Long,opening:Double)=db.withTransaction{dao.enableStore(productId);ensureInventory(productId,WAREHOUSE_STORE);if(opening!=0.0)changeInventory(productId,WAREHOUSE_STORE,opening,1,"ENABLE","فعال‌سازی کالا")}

    suspend fun inventory(warehouseId:Int):List<InventoryRow>{val products=dao.products();val inv=dao.allInventory().associateBy{it.productId to it.warehouseId};return products.filter{if(warehouseId==WAREHOUSE_STORE)it.isEnabledForStore else true}.map{InventoryRow(it,warehouseId,inv[it.id to warehouseId]?.quantity?:0.0)}.sortedBy{it.product.name}}
    suspend fun adjust(productId:Long,warehouseId:Int,delta:Double,note:String?)=db.withTransaction{changeInventory(productId,warehouseId,delta,2,"ADJUST",note,allowNegative=false)}
    suspend fun movements(take:Int=400):List<MovementRow>{val names=dao.products().associate{it.id to it.name};return dao.movements(take).map{MovementRow(it,names[it.productId]?:"کالای حذف‌شده")}}

    suspend fun findByCode(code:String):ProductEntity?=dao.productByCode(code.trim())
    suspend fun checkout(lines:List<CartLine>,paymentType:Int,customerName:String?,customerMobile:String?):Long=db.withTransaction{
        require(lines.isNotEmpty()){"سبد فروش خالی است."}
        val normalized=lines.groupBy{it.product.id}.map{(_,x)->x.first().copy(quantity=x.sumOf{it.quantity})}
        normalized.forEach{line->require(line.quantity>0);val q=dao.inventoryOne(line.product.id,WAREHOUSE_STORE)?.quantity?:0.0;require(q>=line.quantity){"موجودی ${line.product.name} کافی نیست."}}
        val total=normalized.sumOf{it.product.price*it.quantity};val no="S-${System.currentTimeMillis()}";val saleId=dao.insertSale(SaleEntity(invoiceNo=no,total=total,paymentType=paymentType,customerName=customerName?.ifBlank{null},customerMobile=customerMobile?.ifBlank{null}))
        dao.insertSaleItems(normalized.map{SaleItemEntity(saleId=saleId,productId=it.product.id,name=it.product.name,quantity=it.quantity,unitPrice=it.product.price,lineTotal=it.product.price*it.quantity)})
        normalized.forEach{changeInventory(it.product.id,WAREHOUSE_STORE,-it.quantity,3,no,"فروش ${no}")};saleId
    }
    suspend fun sales()=dao.sales()
    suspend fun saleDetails(id:Long)=dao.sale(id)?.let{SaleDetails(it,dao.saleItems(id))}
    suspend fun returnSale(saleId:Long,quantities:Map<Long,Double>,note:String?)=db.withTransaction{
        val sale=dao.sale(saleId)?:error("فاکتور پیدا نشد.");val items=dao.saleItems(saleId);var returnedValue=0.0
        items.forEach{item->val qty=quantities[item.id]?:0.0;if(qty>0){require(qty<=item.quantity-item.returnedQuantity){"تعداد مرجوعی ${item.name} بیشتر از مانده قابل مرجوعی است."};dao.updateSaleItem(item.copy(returnedQuantity=item.returnedQuantity+qty));changeInventory(item.productId,WAREHOUSE_STORE,qty,4,sale.invoiceNo,"مرجوعی ${note.orEmpty()}");returnedValue+=qty*item.unitPrice}}
        require(returnedValue>0){"تعداد مرجوعی وارد نشده است."};dao.updateSale(sale.copy(returnedTotal=sale.returnedTotal+returnedValue))
    }

    suspend fun transfers():List<TransferDetails> = dao.transfers().map{TransferDetails(it,dao.transferItems(it.id))}
    suspend fun createTransfer(productId:Long,quantity:Double,note:String?):Long=db.withTransaction{require(quantity>0);val p=dao.product(productId)?:error("کالا پیدا نشد");val id=dao.insertTransfer(TransferEntity(transferNo="T-${System.currentTimeMillis()}",note=note));dao.insertTransferItems(listOf(TransferItemEntity(transferId=id,productId=productId,name=p.name,quantity=quantity)));id}
    suspend fun dispatchTransfer(id:Long)=db.withTransaction{val t=dao.transfer(id)?:error("انتقال پیدا نشد");require(t.status==1){"این انتقال قابل خروج نیست."};val items=dao.transferItems(id);items.forEach{val q=dao.inventoryOne(it.productId,WAREHOUSE_DEPOT)?.quantity?:0.0;require(q>=it.quantity){"موجودی دپو برای ${it.name} کافی نیست."}};items.forEach{changeInventory(it.productId,WAREHOUSE_DEPOT,-it.quantity,5,t.transferNo,"خروج انتقال")};dao.updateTransfer(t.copy(status=2,dispatchedAt=System.currentTimeMillis()))}
    suspend fun receiveTransfer(id:Long)=db.withTransaction{val t=dao.transfer(id)?:error("انتقال پیدا نشد");require(t.status==2){"ابتدا خروج از دپو را ثبت کن."};dao.transferItems(id).forEach{dao.enableStore(it.productId);changeInventory(it.productId,WAREHOUSE_STORE,it.quantity,6,t.transferNo,"دریافت انتقال")};dao.updateTransfer(t.copy(status=3,receivedAt=System.currentTimeMillis()))}

    suspend fun purchases():List<PurchaseDetails> = dao.purchases().map{PurchaseDetails(it,dao.purchaseItems(it.id))}
    suspend fun createPurchase(supplier:String?,mobile:String?,datePersian:String,warehouseId:Int,paymentType:Int,note:String?,items:List<PurchaseLineDraft>):Long=db.withTransaction{
        require(Jalali.parse(datePersian)!=null){"تاریخ خرید نامعتبر است."};require(items.isNotEmpty()){"حداقل یک کالا اضافه کن."};val total=items.sumOf{it.quantity*it.unitCost};val id=dao.insertPurchase(PurchaseEntity(purchaseNo="P-${System.currentTimeMillis()}",supplierName=supplier?.ifBlank{null},supplierMobile=mobile?.ifBlank{null},purchaseDatePersian=datePersian,warehouseId=warehouseId,paymentType=paymentType,total=total,note=note));dao.insertPurchaseItems(items.map{PurchaseItemEntity(purchaseId=id,productId=it.productId,name=it.name,quantity=it.quantity,unitCost=it.unitCost,lineTotal=it.quantity*it.unitCost)});id
    }
    suspend fun receivePurchase(id:Long)=db.withTransaction{val p=dao.purchase(id)?:error("خرید پیدا نشد");require(p.status==1){"این خرید قبلاً دریافت شده است."};dao.purchaseItems(id).forEach{if(p.warehouseId==WAREHOUSE_STORE)dao.enableStore(it.productId);changeInventory(it.productId,p.warehouseId,it.quantity,7,p.purchaseNo,"دریافت خرید")};dao.updatePurchase(p.copy(status=2))}

    suspend fun checks()=dao.checks()
    suspend fun saveCheck(id:Long=0,title:String,bank:String?,number:String?,payee:String?,amount:Double,duePersian:String,reminderDays:Int,status:Int=1,note:String?):Long{val e=IssuedCheckEntity(id=id,title=title,bankName=bank?.ifBlank{null},checkNumber=number?.ifBlank{null},payee=payee?.ifBlank{null},amount=amount,dueDatePersian=duePersian,dueEpochDay=Jalali.epochDay(duePersian),reminderDaysBefore=reminderDays,status=status,note=note);return if(id==0L)dao.insertCheck(e)else{dao.updateCheck(e);id}}
    suspend fun setCheckStatus(id:Long,status:Int){dao.check(id)?.let{dao.updateCheck(it.copy(status=status))}}

    suspend fun appointments()=dao.appointments()
    suspend fun saveAppointment(id:Long=0,title:String,person:String?,mobile:String?,location:String?,datePersian:String,time:String,reminderMinutes:Int,status:Int=1,note:String?):Long{val e=AppointmentEntity(id=id,title=title,personName=person?.ifBlank{null},mobile=mobile?.ifBlank{null},location=location?.ifBlank{null},datePersian=datePersian,time=time,startsAtEpochMillis=Jalali.epochMillis(datePersian,time),reminderMinutesBefore=reminderMinutes,status=status,note=note);return if(id==0L)dao.insertAppointment(e)else{dao.updateAppointment(e);id}}
    suspend fun setAppointmentStatus(id:Long,status:Int){dao.appointment(id)?.let{dao.updateAppointment(it.copy(status=status))}}

    suspend fun calendar(year:Int,month:Int):CalendarDataLocal{val prefix="%04d/%02d".format(year,month);return CalendarDataLocal(dao.checks().filter{it.dueDatePersian.startsWith(prefix)},dao.appointments().filter{it.datePersian.startsWith(prefix)},dao.purchases().filter{it.purchaseDatePersian.startsWith(prefix)})}

    suspend fun testWoo(settings:WooSettings)=withContext(Dispatchers.IO){WooClient(settings).test()}
    suspend fun syncWoo(onPage:((Int)->Unit)?=null):WooSyncResult=withContext(Dispatchers.IO){
        val settings=WooPrefs(context).settings();val remote=WooClient(settings).fetchAll(onPage);var add=0;var update=0;var failed=0
        db.withTransaction{
            remote.forEach{w->runCatching{val old=dao.productByWooId(w.wooId);if(old==null){val id=dao.insertProduct(ProductEntity(wooId=w.wooId,name=w.name,sku=w.sku,barcode=w.barcode,internalCode="",price=w.price,imageUrl=w.imageUrl,category=w.category,source=ProductEntity.SOURCE_WOO));dao.setInternalCode(id,"W-${w.wooId}");add++}else{dao.updateProduct(old.copy(name=w.name,sku=w.sku,barcode=w.barcode?:old.barcode,price=w.price,imageUrl=w.imageUrl,category=w.category,source=ProductEntity.SOURCE_WOO,updatedAt=System.currentTimeMillis()));update++}}.onFailure{failed++}}
        }
        WooSyncResult(add,update,failed,"${remote.size} کالا از ووکامرس دریافت شد. موجودی محلی تغییر نکرد.")
    }

    private suspend fun ensureInventory(productId:Long,warehouseId:Int){if(dao.inventoryOne(productId,warehouseId)==null)dao.upsertInventory(InventoryEntity(productId,warehouseId,0.0))}
    private suspend fun changeInventory(productId:Long,warehouseId:Int,delta:Double,type:Int,reference:String?,note:String?,allowNegative:Boolean=false){val cur=dao.inventoryOne(productId,warehouseId)?.quantity?:0.0;val next=cur+delta;if(!allowNegative)require(next>=-0.000001){"موجودی نمی‌تواند منفی شود."};dao.upsertInventory(InventoryEntity(productId,warehouseId,next));dao.movement(InventoryMovementEntity(productId=productId,warehouseId=warehouseId,type=type,quantityDelta=delta,balanceAfter=next,reference=reference,note=note))}
}
