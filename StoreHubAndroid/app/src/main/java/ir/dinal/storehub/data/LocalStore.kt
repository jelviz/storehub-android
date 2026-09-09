package ir.dinal.storehub.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import ir.dinal.storehub.inventory.AlertLevel
import ir.dinal.storehub.inventory.ChannelCodes
import ir.dinal.storehub.inventory.ChannelIds
import ir.dinal.storehub.inventory.IntegrationMode
import ir.dinal.storehub.inventory.InventoryMath
import ir.dinal.storehub.inventory.InventoryService
import ir.dinal.storehub.inventory.OrderImportResult
import ir.dinal.storehub.inventory.OrderService
import ir.dinal.storehub.inventory.RefType
import ir.dinal.storehub.inventory.StockMutation
import ir.dinal.storehub.inventory.StocktakeStatus
import ir.dinal.storehub.inventory.TxType
import ir.dinal.storehub.inventory.WarehouseIds
import ir.dinal.storehub.inventory.wooChannelId
import ir.dinal.storehub.sync.ChannelStockPusher
import ir.dinal.storehub.sync.CommerceChannelConnector
import ir.dinal.storehub.sync.MarketplaceConnector
import ir.dinal.storehub.sync.StockPushResult
import ir.dinal.storehub.sync.WooCommerceConnector
import ir.dinal.storehub.util.MoneyFormat
import ir.dinal.storehub.worker.NotificationHelper
import ir.dinal.storehub.worker.ReminderScheduler
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalStore private constructor(private val context:Context){
    private val db=StoreDb.get(context); private val dao=db.dao()
    private val inventory=InventoryService(dao){ id, title, text -> NotificationHelper.show(context, id, title, text) }
    private val orders=OrderService(dao, inventory, { id, title, text -> NotificationHelper.show(context, id, title, text) }) { productId, qty, note ->
        createTransfer(productId, qty, note)
    }
    companion object{
        const val WAREHOUSE_STORE=WarehouseIds.STORE; const val WAREHOUSE_DEPOT=WarehouseIds.DEPOT
        @Volatile private var instance:LocalStore?=null
        fun get(context:Context)=instance?:synchronized(this){instance?:LocalStore(context.applicationContext).also{instance=it}}
        fun warehouseName(id:Int)=if(id==WAREHOUSE_DEPOT)"دپو" else "مغازه"
    }

    suspend fun dashboard():DashboardLocal{
        val products=dao.products();val inv=dao.allInventory().associateBy{it.productId to it.warehouseId}
        val storeProducts=products.filter{it.isEnabledForStore}
        fun level(productId:Long, warehouseId:Int, fallbackMin:Double):String{
            val row=inv[productId to warehouseId]
            val available=InventoryMath.available(row?.quantity?:0.0, row?.reserved?:0.0, row?.damaged?:0.0)
            val min=if((row?.minStock?:0.0)>0) row!!.minStock else fallbackMin
            return InventoryMath.alertLevel(available, row?.warningThreshold?:min, min, row?.criticalStock?:0.0)
        }
        val storeLevels=storeProducts.map{level(it.id, WAREHOUSE_STORE, it.lowStockThreshold.toDouble())}
        val depotLevels=products.map{level(it.id, WAREHOUSE_DEPOT, 0.0)}
        val start=LocalDate.now(ZoneId.of("Asia/Tehran")).atStartOfDay(ZoneId.of("Asia/Tehran")).toInstant().toEpochMilli()
        val todaySales=dao.sales().filter{it.createdAt>=start}.sumOf{it.total-it.returnedTotal}
        val todayEpoch=LocalDate.now(ZoneId.of("Asia/Tehran")).toEpochDay()
        val due=dao.checks().count{it.status==1 && it.dueEpochDay<=todayEpoch+it.reminderDaysBefore && it.dueEpochDay>=todayEpoch-1}
        val todayP=Jalali.format(Jalali.today());val ap=dao.appointments().count{it.status==1&&it.datePersian==todayP}
        val lowStore=storeLevels.count{it==AlertLevel.LOW || it==AlertLevel.WARNING}
        val out=storeLevels.count{it==AlertLevel.OUT_OF_STOCK}
        return DashboardLocal(
            products=products.size,
            storeProducts=storeProducts.size,
            lowStock=lowStore,
            outOfStock=out,
            todaySales=todaySales,
            pendingTransfers=dao.transfers().count{it.status<3},
            dueChecks=due,
            todayAppointments=ap,
            lowStoreStock=lowStore,
            lowDepotStock=depotLevels.count{it==AlertLevel.LOW || it==AlertLevel.WARNING || it==AlertLevel.CRITICAL},
            transferRequired=dao.openTransferSuggestionCount(),
            purchaseRequired=dao.openPurchaseSuggestionCount(),
            criticalStock=storeLevels.count{it==AlertLevel.CRITICAL}+depotLevels.count{it==AlertLevel.CRITICAL},
            unreadAlerts=dao.unreadNotificationCount(),
            failedSync=dao.failedSyncCount(),
            openOrders=dao.openOrderCount(),
            fulfillmentQueue=dao.fulfillmentQueueCount()
        )
    }

    suspend fun products(q:String="")=dao.products(q.trim())
    suspend fun saveProduct(p:ProductEntity,openingQuantity:Double=0.0):Long=db.withTransaction{
        val id=if(p.id==0L){val x=dao.insertProduct(p.copy(internalCode="", createdAt=if(p.createdAt==0L) System.currentTimeMillis() else p.createdAt));dao.setInternalCode(x,"M-$x");x}else{dao.updateProduct(p.copy(updatedAt=System.currentTimeMillis()));p.id}
        if(p.isEnabledForStore){
            dao.enableStore(id)
            inventory.ensureInventory(id,WAREHOUSE_STORE)
            if(openingQuantity!=0.0) inventory.mutate(id,WAREHOUSE_STORE, StockMutation(onHandDelta=openingQuantity), TxType.OPENING, RefType.OPENING, note="موجودی اولیه")
        }
        id
    }
    suspend fun enableStore(productId:Long,opening:Double)=db.withTransaction{
        dao.enableStore(productId)
        inventory.ensureInventory(productId,WAREHOUSE_STORE)
        if(opening!=0.0) inventory.mutate(productId,WAREHOUSE_STORE, StockMutation(onHandDelta=opening), TxType.OPENING, RefType.ENABLE, note="فعال‌سازی کالا")
    }

    suspend fun inventory(warehouseId:Int):List<InventoryRow>{
        val products=dao.products()
        val inv=dao.allInventory().associateBy{it.productId to it.warehouseId}
        val policies=dao.channelPolicies().associateBy{it.channelId}
        fun availableAt(productId:Long, warehouseId:Int):Double{
            val row=inv[productId to warehouseId]
            return InventoryMath.available(row?.quantity?:0.0, row?.reserved?:0.0, row?.damaged?:0.0)
        }
        fun channelQty(productId:Long, channelId:Long):Double{
            val policy=policies[channelId] ?: return 0.0
            return InventoryMath.channelAvailable(policy.policyType, availableAt(productId, WAREHOUSE_STORE), availableAt(productId, WAREHOUSE_DEPOT), policy.safetyStock)
        }
        return products.filter{if(warehouseId==WAREHOUSE_STORE)it.isEnabledForStore else true}.map{ p ->
            val row=inv[p.id to warehouseId] ?: InventoryEntity(p.id, warehouseId)
            row.toRow(p, channelQty(p.id, ChannelIds.WOO_1), channelQty(p.id, ChannelIds.SNAPP))
        }.sortedBy{it.product.name}
    }
    suspend fun adjust(productId:Long,warehouseId:Int,delta:Double,note:String?)=db.withTransaction{
        require(!note.isNullOrBlank()){"برای تعدیل موجودی باید علت را بنویسی. عدد را مستقیم عوض نکن."}
        inventory.mutate(productId, warehouseId, StockMutation(onHandDelta=delta), TxType.ADJUSTMENT, RefType.ADJUSTMENT, note=note)
    }
    suspend fun updateStockPolicy(
        productId:Long, warehouseId:Int, minStock:Double, targetStock:Double, maxStock:Double,
        warningThreshold:Double, criticalStock:Double, safetyStock:Double, reorderPoint:Double, targetTotal:Double
    )=inventory.updatePolicy(productId, warehouseId, minStock, targetStock, maxStock, warningThreshold, criticalStock, safetyStock, reorderPoint, targetTotal)
    suspend fun movements(take:Int=400):List<MovementRow>{val names=dao.products().associate{it.id to it.name};return dao.movements(take).map{MovementRow(it,names[it.productId]?:"کالای حذف‌شده")}}

    suspend fun findByCode(code:String):ProductEntity? {
        val value=code.trim()
        dao.productByCode(value)?.let{return it}
        dao.productByUrl(value)?.let{return it}
        if(value.startsWith("http://") || value.startsWith("https://")){
            val wooId=runCatching{Uri.parse(value).getQueryParameter("p")?.toLongOrNull()}.getOrNull()
            if(wooId!=null) dao.productByWooId(wooId)?.let{return it}
        }
        return null
    }
    suspend fun checkout(lines:List<CartLine>,paymentType:Int,customerName:String?,customerMobile:String?):Long=db.withTransaction{
        require(lines.isNotEmpty()){"سبد فروش خالی است."}
        val normalized=lines.groupBy{it.product.id}.map{(_,x)->x.first().copy(quantity=x.sumOf{it.quantity})}
        normalized.forEach{line->
            require(line.quantity>0)
            val snap=inventory.snapshot(line.product.id, WAREHOUSE_STORE)
            require(snap.available+0.000001>=line.quantity){"موجودی قابل فروش ${line.product.name} کافی نیست."}
        }
        val total=normalized.sumOf{it.product.price*it.quantity};val no="S-${System.currentTimeMillis()}"
        val saleId=dao.insertSale(SaleEntity(invoiceNo=no,total=total,paymentType=paymentType,customerName=customerName?.ifBlank{null},customerMobile=customerMobile?.ifBlank{null}))
        dao.insertSaleItems(normalized.map{SaleItemEntity(saleId=saleId,productId=it.product.id,name=it.product.name,quantity=it.quantity,unitPrice=it.product.price,lineTotal=it.product.price*it.quantity)})
        normalized.forEach{inventory.sellFromStore(it.product.id, it.quantity, saleId, no)}
        saleId
    }
    suspend fun sales()=dao.sales()
    suspend fun saleDetails(id:Long)=dao.sale(id)?.let{SaleDetails(it,dao.saleItems(id))}
    suspend fun returnSale(saleId:Long,quantities:Map<Long,Double>,note:String?)=db.withTransaction{
        val sale=dao.sale(saleId)?:error("فاکتور پیدا نشد.");val items=dao.saleItems(saleId);var returnedValue=0.0
        items.forEach{item->
            val qty=quantities[item.id]?:0.0
            if(qty>0){
                require(qty<=item.quantity-item.returnedQuantity){"تعداد مرجوعی ${item.name} بیشتر از مانده قابل مرجوعی است."}
                dao.updateSaleItem(item.copy(returnedQuantity=item.returnedQuantity+qty))
                inventory.mutate(item.productId, WAREHOUSE_STORE, StockMutation(onHandDelta=qty), TxType.RETURN, RefType.SALE, saleId, sale.invoiceNo, "مرجوعی ${note.orEmpty()}")
                returnedValue+=qty*item.unitPrice
            }
        }
        require(returnedValue>0){"تعداد مرجوعی وارد نشده است."};dao.updateSale(sale.copy(returnedTotal=sale.returnedTotal+returnedValue))
    }

    suspend fun transfers():List<TransferDetails> = dao.transfers().map{TransferDetails(it,dao.transferItems(it.id))}
    suspend fun createTransfer(productId:Long,quantity:Double,note:String?):Long=db.withTransaction{
        require(quantity>0);val p=dao.product(productId)?:error("کالا پیدا نشد")
        val id=dao.insertTransfer(TransferEntity(transferNo="T-${System.currentTimeMillis()}",note=note,sourceWarehouseId=WAREHOUSE_DEPOT,destinationWarehouseId=WAREHOUSE_STORE))
        dao.insertTransferItems(listOf(TransferItemEntity(transferId=id,productId=productId,name=p.name,quantity=quantity)));id
    }
    suspend fun dispatchTransfer(id:Long)=db.withTransaction{
        val t=dao.transfer(id)?:error("انتقال پیدا نشد");require(t.status==1){"این انتقال قابل خروج نیست."}
        val items=dao.transferItems(id)
        items.forEach{
            val snap=inventory.snapshot(it.productId, WAREHOUSE_DEPOT)
            require(snap.available+0.000001>=it.quantity){"موجودی دپو برای ${it.name} کافی نیست."}
        }
        items.forEach{
            inventory.mutate(it.productId, WAREHOUSE_DEPOT, StockMutation(onHandDelta=-it.quantity), TxType.TRANSFER_OUT, RefType.TRANSFER, id, t.transferNo, "خروج انتقال")
            inventory.mutate(it.productId, WAREHOUSE_STORE, StockMutation(inTransitDelta=it.quantity), TxType.TRANSFER_OUT, RefType.TRANSFER, id, t.transferNo, "در مسیر به مغازه")
        }
        dao.updateTransfer(t.copy(status=2,dispatchedAt=System.currentTimeMillis(),approvedAt=System.currentTimeMillis()))
    }
    suspend fun receiveTransfer(id:Long)=db.withTransaction{
        val t=dao.transfer(id)?:error("انتقال پیدا نشد");require(t.status==2){"ابتدا خروج از دپو را ثبت کن."}
        dao.transferItems(id).forEach{
            dao.enableStore(it.productId)
            val store=inventory.snapshot(it.productId, WAREHOUSE_STORE)
            val transit=minOf(store.inTransit, it.quantity)
            inventory.mutate(it.productId, WAREHOUSE_STORE, StockMutation(onHandDelta=it.quantity, inTransitDelta=-transit), TxType.TRANSFER_IN, RefType.TRANSFER, id, t.transferNo, "دریافت انتقال")
        }
        dao.updateTransfer(t.copy(status=3,receivedAt=System.currentTimeMillis()))
        orders.retryOpenOrders()
    }

    suspend fun purchases():List<PurchaseDetails> = dao.purchases().map{PurchaseDetails(it,dao.purchaseItems(it.id), it.supplierId?.let{sid->dao.supplier(sid)})}
    suspend fun suppliers()=dao.suppliers()
    suspend fun saveSupplier(id:Long=0, name:String, phone:String?, address:String?, notes:String?):Long{
        require(name.isNotBlank()){"نام تأمین‌کننده لازم است."}
        val e=SupplierEntity(id=id, name=name.trim(), phone=phone?.ifBlank{null}, address=address?.ifBlank{null}, notes=notes?.ifBlank{null})
        return if(id==0L) dao.insertSupplier(e) else { dao.updateSupplier(e); id }
    }
    private suspend fun upsertSupplier(name:String?, phone:String?):Long?{
        val n=name?.trim().orEmpty(); if(n.isBlank()) return null
        val mobile=phone?.ifBlank{null}
        dao.supplierByNamePhone(n, mobile.orEmpty())?.let{return it.id}
        return dao.insertSupplier(SupplierEntity(name=n, phone=mobile))
    }
    suspend fun createPurchase(supplier:String?,mobile:String?,datePersian:String,warehouseId:Int,paymentType:Int,note:String?,items:List<PurchaseLineDraft>,invoiceNumber:String?=null):Long=db.withTransaction{
        require(Jalali.parse(datePersian)!=null){"تاریخ خرید نامعتبر است."};require(items.isNotEmpty()){"حداقل یک کالا اضافه کن."}
        val supplierId=upsertSupplier(supplier, mobile)
        val total=items.sumOf{it.quantity*it.unitCost}
        val id=dao.insertPurchase(PurchaseEntity(purchaseNo="P-${System.currentTimeMillis()}",supplierName=supplier?.ifBlank{null},supplierMobile=mobile?.ifBlank{null},purchaseDatePersian=datePersian,warehouseId=warehouseId,paymentType=paymentType,total=total,note=note,supplierId=supplierId,invoiceNumber=invoiceNumber?.ifBlank{null}))
        dao.insertPurchaseItems(items.map{PurchaseItemEntity(purchaseId=id,productId=it.productId,name=it.name,quantity=it.quantity,unitCost=it.unitCost,lineTotal=it.quantity*it.unitCost)});id
    }
    suspend fun updatePurchase(id:Long,supplier:String?,mobile:String?,datePersian:String,warehouseId:Int,paymentType:Int,note:String?,items:List<PurchaseLineDraft>,invoiceNumber:String?=null)=db.withTransaction{
        val p=dao.purchase(id)?:error("خرید پیدا نشد.")
        require(p.status==1){"خرید دریافت‌شده قابل ویرایش نیست."}
        require(Jalali.parse(datePersian)!=null){"تاریخ خرید نامعتبر است."}
        require(items.isNotEmpty()){"حداقل یک کالا اضافه کن."}
        val supplierId=upsertSupplier(supplier, mobile)
        val total=items.sumOf{it.quantity*it.unitCost}
        dao.updatePurchase(p.copy(supplierName=supplier?.ifBlank{null},supplierMobile=mobile?.ifBlank{null},purchaseDatePersian=datePersian,warehouseId=warehouseId,paymentType=paymentType,total=total,note=note,supplierId=supplierId,invoiceNumber=invoiceNumber?.ifBlank{null}))
        dao.deletePurchaseItems(id)
        dao.insertPurchaseItems(items.map{PurchaseItemEntity(purchaseId=id,productId=it.productId,name=it.name,quantity=it.quantity,unitCost=it.unitCost,lineTotal=it.quantity*it.unitCost)})
        id
    }
    suspend fun receivePurchase(id:Long)=db.withTransaction{
        val p=dao.purchase(id)?:error("خرید پیدا نشد");require(p.status==1){"این خرید قبلاً دریافت شده است."}
        dao.purchaseItems(id).forEach{
            if(p.warehouseId==WAREHOUSE_STORE) dao.enableStore(it.productId)
            inventory.mutate(it.productId, p.warehouseId, StockMutation(onHandDelta=it.quantity), TxType.PURCHASE, RefType.PURCHASE, id, p.purchaseNo, "دریافت خرید", lastPurchaseCost=it.unitCost)
        }
        dao.updatePurchase(p.copy(status=2))
        orders.retryOpenOrders()
    }

    suspend fun lastSupplier(productId:Long)=dao.lastSupplierForProduct(productId)
    suspend fun priceIntelligence(productId:Long):PriceIntelligence{
        val purchases=dao.purchases().filter{it.status==2}
        val now=System.currentTimeMillis()
        val costs=ArrayList<Pair<Long,Double>>()
        purchases.forEach{ p ->
            dao.purchaseItems(p.id).filter{it.productId==productId && it.unitCost>0}.forEach{ costs += p.createdAt to it.unitCost }
        }
        fun avg(since:Long):Double{
            val slice=costs.filter{it.first>=since}.map{it.second}
            return if(slice.isEmpty()) 0.0 else slice.average()
        }
        val inv=dao.allInventory().filter{it.productId==productId && it.lastPurchaseCost>0}
        val lastCost=inv.maxOfOrNull{it.lastPurchaseCost} ?: costs.maxByOrNull{it.first}?.second ?: 0.0
        return PriceIntelligence(
            lastPurchasePrice=lastCost,
            averagePurchasePrice30Days=avg(now-30L*24*60*60*1000),
            averagePurchasePrice90Days=avg(now-90L*24*60*60*1000),
            minimumPurchasePrice=costs.minOfOrNull{it.second}?:0.0,
            maximumPurchasePrice=costs.maxOfOrNull{it.second}?:0.0,
            lastSalePrice=dao.lastSalePrices(productId).firstOrNull()?:0.0,
            lastSupplierName=dao.lastSupplierForProduct(productId)?.name ?: purchases.firstOrNull{ p -> dao.purchaseItems(p.id).any{it.productId==productId} }?.supplierName
        )
    }

    suspend fun alerts(refresh:Boolean=false):AlertCenter{
        if(refresh) inventory.evaluateAllAlerts()
        return AlertCenter(dao.notifications(), dao.openTransferSuggestions(), dao.openPurchaseSuggestions())
    }
    suspend fun markAlertRead(id:Long)=dao.markNotificationRead(id, System.currentTimeMillis())

    suspend fun checks()=dao.checks()
    suspend fun saveCheck(id:Long=0,title:String,bank:String?,number:String?,payee:String?,amount:Double,duePersian:String,reminderDays:Int,status:Int=1,note:String?):Long{
        val e=IssuedCheckEntity(id=id,title=title,bankName=bank?.ifBlank{null},checkNumber=number?.ifBlank{null},payee=payee?.ifBlank{null},amount=amount,dueDatePersian=duePersian,dueEpochDay=Jalali.epochDay(duePersian),reminderDaysBefore=reminderDays,status=status,note=note)
        val savedId=if(id==0L) dao.insertCheck(e) else { dao.updateCheck(e); id }
        if(status==1) ReminderScheduler.scheduleCheck(context,e.copy(id=savedId)) else ReminderScheduler.cancelCheck(context,savedId)
        return savedId
    }
    suspend fun setCheckStatus(id:Long,status:Int){dao.check(id)?.let{dao.updateCheck(it.copy(status=status));if(status==1)ReminderScheduler.scheduleCheck(context,it.copy(status=status)) else ReminderScheduler.cancelCheck(context,id)}}

    suspend fun appointments()=dao.appointments()
    suspend fun saveAppointment(id:Long=0,title:String,person:String?,mobile:String?,location:String?,datePersian:String,time:String,reminderMinutes:Int,status:Int=1,note:String?):Long{
        val e=AppointmentEntity(id=id,title=title,personName=person?.ifBlank{null},mobile=mobile?.ifBlank{null},location=location?.ifBlank{null},datePersian=datePersian,time=time,startsAtEpochMillis=Jalali.epochMillis(datePersian,time),reminderMinutesBefore=reminderMinutes,status=status,note=note)
        val savedId=if(id==0L) dao.insertAppointment(e) else { dao.updateAppointment(e); id }
        if(status==1) ReminderScheduler.scheduleAppointment(context,e.copy(id=savedId)) else ReminderScheduler.cancelAppointment(context,savedId)
        return savedId
    }
    suspend fun setAppointmentStatus(id:Long,status:Int){dao.appointment(id)?.let{dao.updateAppointment(it.copy(status=status));if(status==1)ReminderScheduler.scheduleAppointment(context,it.copy(status=status)) else ReminderScheduler.cancelAppointment(context,id)}}

    suspend fun calendar(year:Int,month:Int):CalendarDataLocal{val prefix="%04d/%02d".format(year,month);return CalendarDataLocal(dao.checks().filter{it.dueDatePersian.startsWith(prefix)},dao.appointments().filter{it.datePersian.startsWith(prefix)},dao.purchases().filter{it.purchaseDatePersian.startsWith(prefix)})}

    suspend fun testWoo(settings:WooSettings)=withContext(Dispatchers.IO){WooClient(settings).test()}
    suspend fun syncWoo(onPage:((Int)->Unit)?=null):WooSyncResult=withContext(Dispatchers.IO){
        val settings=WooPrefs(context).settings();val remote=WooClient(settings).fetchAll(onPage);var add=0;var update=0;var failed=0
        db.withTransaction{
            remote.forEach{w->runCatching{
                val old=dao.productByWooId(w.wooId)
                if(old==null){
                    val id=dao.insertProduct(ProductEntity(wooId=w.wooId,name=w.name,sku=w.sku,barcode=w.barcode,internalCode="",price=w.price,imageUrl=w.imageUrl,productUrl=w.productUrl,category=w.category,source=ProductEntity.SOURCE_WOO))
                    dao.setInternalCode(id,"W-${w.wooId}")
                    inventory.upsertWooMapping(id, w.wooId, w.sku)
                    add++
                }else{
                    dao.updateProduct(old.copy(name=w.name,sku=w.sku,barcode=w.barcode?:old.barcode,price=w.price,imageUrl=w.imageUrl,productUrl=w.productUrl?:old.productUrl,category=w.category,source=ProductEntity.SOURCE_WOO,updatedAt=System.currentTimeMillis()))
                    inventory.upsertWooMapping(old.id, w.wooId, w.sku)
                    update++
                }
            }.onFailure{failed++}}
        }
        WooSyncResult(add,update,failed,"${remote.size} کالا از ووکامرس دریافت شد. موجودی محلی تغییر نکرد.")
    }

    suspend fun mapPublishedProduct(productId:Long, siteIndex:Int, externalId:Long?, sku:String?)=
        inventory.upsertWooMapping(productId, externalId, sku, wooChannelId(siteIndex))

    suspend fun shopOrders():List<ShopOrderDetails>{
        val names=dao.channels().associate{it.id to it.name}
        return dao.orders().map{ ShopOrderDetails(it, dao.orderItems(it.id), names[it.channelId] ?: "کانال") }
    }
    suspend fun shopOrder(id:Long):ShopOrderDetails?{
        val o=dao.order(id)?:return null
        val name=dao.channels().firstOrNull{it.id==o.channelId}?.name ?: "کانال"
        return ShopOrderDetails(o, dao.orderItems(id), name)
    }
    suspend fun importOnlineOrders():OrderImportResult=withContext(Dispatchers.IO){
        val drafts=commerceConnectors().values.flatMap{ runCatching{ it.getOrders() }.getOrDefault(emptyList()) }
        db.withTransaction{ orders.ingest(drafts) }
    }
    suspend fun retryOrderStock()=db.withTransaction{ orders.retryOpenOrders() }
    suspend fun startPicking(id:Long)=db.withTransaction{ orders.startPicking(id) }
    suspend fun confirmPicked(id:Long)=db.withTransaction{ orders.confirmPicked(id) }
    suspend fun startPacking(id:Long)=db.withTransaction{ orders.startPacking(id) }
    suspend fun confirmPacked(id:Long)=db.withTransaction{ orders.confirmPacked(id) }
    suspend fun shipOrder(id:Long){
        val order=db.withTransaction{
            val current=dao.order(id)?:error("سفارش پیدا نشد.")
            orders.ship(id)
            current
        }
        runCatching { commerceConnectors()[order.channelId]?.updateOrderStatus(order.externalOrderId, "completed") }
    }
    suspend fun cancelOrder(id:Long, note:String?){
        val order=db.withTransaction{
            val current=dao.order(id)?:error("سفارش پیدا نشد.")
            orders.cancel(id, note)
            current
        }
        runCatching { commerceConnectors()[order.channelId]?.updateOrderStatus(order.externalOrderId, "cancelled") }
    }

    suspend fun startStocktake(warehouseId:Int, note:String?):Long=
        dao.insertStocktake(StocktakeSessionEntity(sessionNo="ST-${System.currentTimeMillis()}", warehouseId=warehouseId, note=note))
    suspend fun stocktakes():List<StocktakeDetails> = dao.stocktakes().map{ StocktakeDetails(it, dao.stocktakeItems(it.id)) }
    suspend fun addStocktakeCount(sessionId:Long, productId:Long, counted:Double, hint:String?):Long=db.withTransaction{
        val session=dao.stocktake(sessionId)?:error("انبارگردانی پیدا نشد.")
        require(session.status==StocktakeStatus.OPEN){"این شمارش قبلاً تأیید شده است."}
        val product=dao.product(productId)?:error("کالا پیدا نشد.")
        inventory.ensureInventory(productId, session.warehouseId)
        val system=inventory.snapshot(productId, session.warehouseId).onHand
        val existing=dao.stocktakeItem(sessionId, productId)
        if(existing!=null){
            dao.updateStocktakeItem(existing.copy(countedQty=counted, systemQty=system, hint=hint?:existing.hint))
            existing.id
        }else dao.insertStocktakeItem(StocktakeItemEntity(sessionId=sessionId, productId=productId, name=product.name, systemQty=system, countedQty=counted, hint=hint))
    }
    suspend fun updateStocktakeItemQty(itemId:Long, counted:Double)=db.withTransaction{
        require(counted>=0){"تعداد نامعتبر است."}
        val item=dao.stocktakeItemById(itemId)?:error("قلم شمارش پیدا نشد.")
        val session=dao.stocktake(item.sessionId)?:error("انبارگردانی پیدا نشد.")
        require(session.status==StocktakeStatus.OPEN){"این شمارش تأیید شده و قابل ویرایش نیست."}
        val system=inventory.snapshot(item.productId, session.warehouseId).onHand
        dao.updateStocktakeItem(item.copy(countedQty=counted, systemQty=system))
    }
    suspend fun removeStocktakeItem(itemId:Long)=db.withTransaction{
        val item=dao.stocktakeItemById(itemId)?:error("قلم شمارش پیدا نشد.")
        val session=dao.stocktake(item.sessionId)?:error("انبارگردانی پیدا نشد.")
        require(session.status==StocktakeStatus.OPEN){"این شمارش تأیید شده و قابل ویرایش نیست."}
        dao.deleteStocktakeItem(itemId)
    }
    suspend fun confirmStocktake(sessionId:Long)=db.withTransaction{
        val session=dao.stocktake(sessionId)?:error("انبارگردانی پیدا نشد.")
        require(session.status==StocktakeStatus.OPEN){"این شمارش قبلاً تأیید شده است."}
        val items=dao.stocktakeItems(sessionId)
        require(items.isNotEmpty()){"حداقل یک کالا بشمار."}
        items.forEach{
            val delta=it.countedQty-it.systemQty
            if(kotlin.math.abs(delta)>0.000001){
                inventory.mutate(
                    it.productId, session.warehouseId, StockMutation(onHandDelta=delta),
                    TxType.STOCKTAKING_DIFFERENCE, RefType.STOCKTAKE, sessionId, session.sessionNo,
                    "انبارگردانی ${session.sessionNo}"+(it.hint?.let{h->" • $h"}?:"" )
                )
            }
        }
        dao.updateStocktake(session.copy(status=StocktakeStatus.CONFIRMED, confirmedAt=System.currentTimeMillis()))
        orders.retryOpenOrders()
    }

    suspend fun channelStockRows():List<ChannelStockRow>{
        val names=dao.products().associate{it.id to it.name}
        val channels=dao.channels().associateBy{it.id}
        return dao.allSyncQueue().map{
            ChannelStockRow(it, names[it.productId]?:"کالا", channels[it.channelId]?.name?:"کانال", channels[it.channelId]?.integrationMode?:IntegrationMode.DISABLED)
        }
    }
    suspend fun enqueueAllChannelStock(){
        dao.products().forEach{ inventory.enqueueChannelStock(it.id) }
    }
    suspend fun pushChannelStock():StockPushResult=withContext(Dispatchers.IO){
        enqueueAllChannelStock()
        val pusher=ChannelStockPusher(dao, commerceConnectors())
        val result=pusher.flush()
        MarketplacePrefs(context).endpoints().forEach{ ep ->
            dao.setChannelMode(ep.channelId, if(ep.configured) IntegrationMode.API else IntegrationMode.MANUAL)
        }
        result
    }
    suspend fun markChannelStockManual(id:Long)=ChannelStockPusher(dao, emptyMap()).markManualSent(id)
    suspend fun saveMarketplace(endpoint:MarketplaceEndpoint){
        MarketplacePrefs(context).save(endpoint)
        dao.setChannelMode(endpoint.channelId, if(endpoint.configured) IntegrationMode.API else IntegrationMode.MANUAL)
    }
    fun marketplaceEndpoints()=MarketplacePrefs(context).endpoints()

    private fun commerceConnectors():Map<Long, CommerceChannelConnector>{
        val map=HashMap<Long, CommerceChannelConnector>()
        PublishingPrefs(context).sites().forEach{ site ->
            if(site.baseUrl.startsWith("https://") && site.consumerKey.startsWith("ck_") && site.consumerSecret.startsWith("cs_")){
                val code=when(site.index){ 2->ChannelCodes.WOO_2; 3->ChannelCodes.WOO_3; else->ChannelCodes.WOO_1 }
                map[wooChannelId(site.index)] = WooCommerceConnector(site.toWooSettings(), code)
            }
        }
        val woo=WooPrefs(context).settings()
        if(!map.containsKey(ChannelIds.WOO_1) && woo.baseUrl.startsWith("https://") && woo.consumerKey.startsWith("ck_")){
            map[ChannelIds.WOO_1] = WooCommerceConnector(woo, ChannelCodes.WOO_1)
        }
        MarketplacePrefs(context).endpoints().filter{ it.configured }.forEach{ ep ->
            val code=if(ep.channelId==ChannelIds.TAPSI) ChannelCodes.TAPSI else ChannelCodes.SNAPP
            map[ep.channelId] = MarketplaceConnector(ep.baseUrl, ep.token, code)
        }
        return map
    }

    suspend fun photoPriceLookup(uri: android.net.Uri): PhotoPriceLookup {
        val products = dao.products()
        val prefs = PublishingPrefs(context)
        val ranked = ir.dinal.storehub.publishing.LocalPhotoMatcher.matchRanked(context, uri, products)
        val storeQty = HashMap<Long, Double>()
        val depotQty = HashMap<Long, Double>()
        products.forEach { p ->
            val s = dao.inventoryOne(p.id, WAREHOUSE_STORE)
            val d = dao.inventoryOne(p.id, WAREHOUSE_DEPOT)
            storeQty[p.id] = InventoryMath.available(s?.quantity ?: 0.0, s?.reserved ?: 0.0, s?.damaged ?: 0.0)
            depotQty[p.id] = InventoryMath.available(d?.quantity ?: 0.0, d?.reserved ?: 0.0, d?.damaged ?: 0.0)
        }
        val hits = ranked.map {
            PhotoPriceHit(
                product = it.product,
                storeAvailable = storeQty[it.product.id] ?: 0.0,
                depotAvailable = depotQty[it.product.id] ?: 0.0,
                kind = it.kind,
                note = it.note
            )
        }
        val message = when {
            hits.isNotEmpty() -> null
            !prefs.hasOpenAiKey() -> "بارکد روی عکس خوانده نشد. برای پیدا کردن از روی ظاهر کالا، کلید هوش مصنوعی را در تنظیمات ۳ سایت بگذار."
            else -> "این کالا در کاتالوگ همین فروشگاه پیدا نشد. اول باید کالا را در StoreHub ثبت کرده باشی."
        }
        return PhotoPriceLookup(hits = hits, usedAi = prefs.hasOpenAiKey(), message = message)
    }

    /**
     * Builds a compact local context for the in-app DINAL assistant.
     * Only StoreHub data is included; ChatGPT app conversations are never read.
     */
    suspend fun assistantContext(question:String=""):String{
        val dash=dashboard()
        val storeRows=inventory(WAREHOUSE_STORE)
        val depotRows=inventory(WAREHOUSE_DEPOT)
        val depotById=depotRows.associateBy{it.product.id}
        val tokens=question.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+")," ")
            .split(' ')
            .map{it.trim()}
            .filter{it.length>=3}
            .distinct()
            .take(12)

        fun score(row:InventoryRow):Int{
            val hay=listOfNotNull(row.product.name,row.product.sku,row.product.barcode,row.product.category,row.product.internalCode)
                .joinToString(" ").lowercase()
            return (tokens.count { hay.contains(it) } * 3) + if (row.alertLevel != AlertLevel.NORMAL) 1 else 0
        }

        val matched=storeRows.map{it to score(it)}.filter{it.second>0}.sortedByDescending{it.second}.take(40).map{it.first}
        val focusRows=(if(matched.isNotEmpty()) matched else storeRows.sortedWith(compareBy<InventoryRow>{it.alertLevel==AlertLevel.NORMAL}.thenBy{it.available}).take(35))

        val today=LocalDate.now(ZoneId.of("Asia/Tehran")).toEpochDay()
        val openChecks=checks().filter{it.status==1}.sortedBy{it.dueEpochDay}.take(15)
        val recentSales=sales().take(20)
        val recentPurchases=purchases().take(10)
        val upcomingAppointments=appointments().filter{it.status==1}.take(10)

        fun money(v:Double)=MoneyFormat.tomanPlain(v)
        return buildString{
            appendLine("تاریخ امروز: ${Jalali.format(Jalali.today())}")
            appendLine("کالاها: ${dash.products} | فعال مغازه: ${dash.storeProducts} | کم‌موجود فروشگاه: ${dash.lowStoreStock} | ناموجود: ${dash.outOfStock} | کم‌موجود دپو: ${dash.lowDepotStock}")
            appendLine("فروش امروز: ${money(dash.todaySales)} | انتقال باز: ${dash.pendingTransfers} | پیشنهاد انتقال: ${dash.transferRequired} | پیشنهاد خرید: ${dash.purchaseRequired}")
            appendLine()
            appendLine("کالاهای مرتبط/مهم (نام | کد | قیمت | قابل‌فروش مغازه | دپو | رزرو):")
            focusRows.forEach{r->
                val depot=depotById[r.product.id]
                appendLine("- ${r.product.name} | ${r.product.internalCode} | ${money(r.product.price)} | ${r.available} | ${depot?.available?:0.0} | ${r.reserved}")
            }
            if(openChecks.isNotEmpty()){
                appendLine()
                appendLine("چک‌های باز (سررسید | مبلغ | عنوان/ذی‌نفع | فاصله روز):")
                openChecks.forEach{c->appendLine("- ${c.dueDatePersian} | ${money(c.amount)} | ${c.payee?:c.title} | ${c.dueEpochDay-today}")}
            }
            if(recentSales.isNotEmpty()){
                appendLine()
                appendLine("فروش‌های اخیر:")
                recentSales.forEach{s->appendLine("- ${s.invoiceNo} | خالص ${money(s.total-s.returnedTotal)}")}
            }
            if(recentPurchases.isNotEmpty()){
                appendLine()
                appendLine("خریدهای اخیر:")
                recentPurchases.forEach{p->appendLine("- ${p.purchase.purchaseDatePersian} | ${p.purchase.supplierName?:"بدون نام تأمین‌کننده"} | ${money(p.purchase.total)} | وضعیت ${p.purchase.status}")}
            }
            if(upcomingAppointments.isNotEmpty()){
                appendLine()
                appendLine("قرارهای باز:")
                upcomingAppointments.forEach{a->appendLine("- ${a.datePersian} ${a.time} | ${a.title}${a.personName?.let{" | $it"}.orEmpty()}")}
            }
        }
    }
}
