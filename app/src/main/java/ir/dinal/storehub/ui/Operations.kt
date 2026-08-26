package ir.dinal.storehub.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ir.dinal.storehub.data.*
import kotlinx.coroutines.launch

@Composable fun TransfersScreen(nav:NavHostController){val ctx=LocalContext.current;val store=remember{LocalStore.get(ctx)};val scope=rememberCoroutineScope();var products by remember{mutableStateOf<List<ProductEntity>>(emptyList())};var list by remember{mutableStateOf<List<TransferDetails>>(emptyList())};var productId by remember{mutableLongStateOf(0)};var qty by remember{mutableStateOf("")};var note by remember{mutableStateOf("")};var err by remember{mutableStateOf<String?>(null)}
    suspend fun load(){products=store.products();list=store.transfers();if(productId==0L)productId=products.firstOrNull()?.id?:0};LaunchedEffect(Unit){load()}
    Shell(nav,"انتقال دپو → مغازه"){
        Card{Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text("انتقال جدید",fontWeight=FontWeight.Bold);ProductPicker(products,productId){productId=it};OutlinedTextField(qty,{qty=it},label={Text("تعداد")},modifier=Modifier.fillMaxWidth());OutlinedTextField(note,{note=it},label={Text("توضیح")},modifier=Modifier.fillMaxWidth());Button({scope.launch{runCatching{store.createTransfer(productId,qty.toDoubleOrNull()?:0.0,note)}.onSuccess{qty="";note="";load()}.onFailure{err=it.message}}},Modifier.fillMaxWidth()){Text("ثبت درخواست انتقال")}}}
        ErrorText(err)
        LazyColumn(Modifier.weight(1f)){items(list,key={it.transfer.id}){d->Card(Modifier.fillMaxWidth().padding(vertical=3.dp)){Column(Modifier.padding(10.dp)){Text(d.transfer.transferNo,fontWeight=FontWeight.Bold);d.items.forEach{Text("${it.name}: ${it.quantity}")};Text("وضعیت: ${when(d.transfer.status){1->"ثبت شده";2->"خارج شده از دپو";else->"تحویل مغازه"}}");Row{if(d.transfer.status==1)TextButton({scope.launch{runCatching{store.dispatchTransfer(d.transfer.id)}.onSuccess{load()}.onFailure{err=it.message}}}){Text("ثبت خروج از دپو")};if(d.transfer.status==2)TextButton({scope.launch{runCatching{store.receiveTransfer(d.transfer.id)}.onSuccess{load()}.onFailure{err=it.message}}}){Text("تأیید تحویل مغازه")}}}}}}
    }
}

@Composable fun PurchasesScreen(nav:NavHostController){val ctx=LocalContext.current;val store=remember{LocalStore.get(ctx)};val scope=rememberCoroutineScope();var products by remember{mutableStateOf<List<ProductEntity>>(emptyList())};var purchases by remember{mutableStateOf<List<PurchaseDetails>>(emptyList())};var selected by remember{mutableLongStateOf(0)};var qty by remember{mutableStateOf("")};var cost by remember{mutableStateOf("")};val draft=remember{mutableStateListOf<PurchaseLineDraft>()};var supplier by remember{mutableStateOf("")};var mobile by remember{mutableStateOf("")};var date by remember{mutableStateOf(todayPersian())};var warehouse by remember{mutableIntStateOf(LocalStore.WAREHOUSE_DEPOT)};var payment by remember{mutableIntStateOf(2)};var note by remember{mutableStateOf("")};var err by remember{mutableStateOf<String?>(null)}
    suspend fun load(){products=store.products();purchases=store.purchases();if(selected==0L)selected=products.firstOrNull()?.id?:0};LaunchedEffect(Unit){load()}
    Shell(nav,"خریدهای بازار"){
        Card{Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text("ثبت خرید",fontWeight=FontWeight.Bold);OutlinedTextField(supplier,{supplier=it},label={Text("فروشنده")},modifier=Modifier.fillMaxWidth());OutlinedTextField(mobile,{mobile=it},label={Text("موبایل فروشنده")},modifier=Modifier.fillMaxWidth());OutlinedTextField(date,{date=it},label={Text("تاریخ شمسی")},modifier=Modifier.fillMaxWidth());WarehousePicker(warehouse){warehouse=it};PaymentPicker(payment){payment=it};HorizontalDivider();ProductPicker(products,selected){selected=it};Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){OutlinedTextField(qty,{qty=it},label={Text("تعداد")},modifier=Modifier.weight(1f));OutlinedTextField(cost,{cost=it},label={Text("قیمت خرید واحد")},modifier=Modifier.weight(1f))};OutlinedButton({val p=products.firstOrNull{it.id==selected};val q=qty.toDoubleOrNull()?:0.0;val c=cost.toDoubleOrNull()?:0.0;if(p!=null&&q>0){draft.add(PurchaseLineDraft(p.id,p.name,q,c));qty="";cost=""}},Modifier.fillMaxWidth()){Text("افزودن قلم")};draft.forEachIndexed{i,l->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("${l.name} × ${l.quantity} = ${money(l.quantity*l.unitCost)}",Modifier.weight(1f));TextButton({draft.removeAt(i)}){Text("حذف")}}};OutlinedTextField(note,{note=it},label={Text("توضیح")},modifier=Modifier.fillMaxWidth());Text("جمع خرید: ${money(draft.sumOf{it.quantity*it.unitCost})}",fontWeight=FontWeight.Bold);Button({scope.launch{runCatching{store.createPurchase(supplier,mobile,date,warehouse,payment,note,draft.toList())}.onSuccess{draft.clear();supplier="";mobile="";note="";load()}.onFailure{err=it.message}}},enabled=draft.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text("ثبت خرید")}}}
        ErrorText(err)
        LazyColumn(Modifier.weight(1f)){items(purchases,key={it.purchase.id}){d->Card(Modifier.fillMaxWidth().padding(vertical=3.dp)){Column(Modifier.padding(10.dp)){Text(d.purchase.purchaseNo,fontWeight=FontWeight.Bold);Text("${d.purchase.purchaseDatePersian} | ${LocalStore.warehouseName(d.purchase.warehouseId)} | ${money(d.purchase.total)}");Text(d.purchase.supplierName?:"بدون نام فروشنده");d.items.take(4).forEach{Text("${it.name}: ${it.quantity} × ${money(it.unitCost)}")};if(d.purchase.status==1)Button({scope.launch{runCatching{store.receivePurchase(d.purchase.id)}.onSuccess{load()}.onFailure{err=it.message}}}){Text("دریافت کالا و افزایش موجودی") } else Text("دریافت شده")}}}}
    }
}
