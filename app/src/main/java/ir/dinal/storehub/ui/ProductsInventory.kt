package ir.dinal.storehub.ui

import android.app.Activity
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
import ir.dinal.storehub.util.LabelPrinter
import kotlinx.coroutines.launch

@Composable fun ProductsScreen(activity:Activity,nav:NavHostController){val ctx=LocalContext.current;val store=remember{LocalStore.get(ctx)};val scope=rememberCoroutineScope();var list by remember{mutableStateOf<List<ProductEntity>>(emptyList())};var q by remember{mutableStateOf("")};var err by remember{mutableStateOf<String?>(null)};var showForm by remember{mutableStateOf(false)};var editing by remember{mutableStateOf<ProductEntity?>(null)}
    suspend fun load(){list=store.products(q)};LaunchedEffect(Unit){runCatching{load()}.onFailure{err=it.message}}
    Shell(nav,"کالاها"){
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){OutlinedTextField(q,{q=it},label={Text("نام / SKU / بارکد")},modifier=Modifier.weight(1f),singleLine=true);Button({scope.launch{load()}}){Text("جستجو")}}
        Button({editing=null;showForm=!showForm}){Text(if(showForm)"بستن فرم" else "کالای جدید")}
        if(showForm)ProductForm(editing){p,opening->scope.launch{runCatching{store.saveProduct(p,opening)}.onSuccess{showForm=false;editing=null;load()}.onFailure{err=it.message}}}
        ErrorText(err)
        LazyColumn(Modifier.weight(1f)){items(list,key={it.id}){p->Card(Modifier.fillMaxWidth().padding(vertical=3.dp)){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(p.name,fontWeight=FontWeight.Bold);Text("${p.sku?:p.barcode?:p.internalCode} | ${money(p.price)}");Text("${p.category?:"بدون دسته"} | ${if(p.source==ProductEntity.SOURCE_WOO)"ووکامرس" else "دستی"} | ${if(p.isEnabledForStore)"فعال در مغازه" else "فقط کاتالوگ"}");Row{TextButton({editing=p;showForm=true}){Text("ویرایش")};if(!p.isEnabledForStore)TextButton({scope.launch{store.enableStore(p.id,0.0);load()}}){Text("فعال‌سازی")};TextButton({LabelPrinter.print(activity,p)}){Text("چاپ لیبل")}}}}}}
    }
}

@Composable private fun ProductForm(initial:ProductEntity?,onSave:(ProductEntity,Double)->Unit){var name by remember(initial?.id){mutableStateOf(initial?.name?:"")};var sku by remember(initial?.id){mutableStateOf(initial?.sku?:"")};var barcode by remember(initial?.id){mutableStateOf(initial?.barcode?:"")};var category by remember(initial?.id){mutableStateOf(initial?.category?:"")};var price by remember(initial?.id){mutableStateOf(initial?.price?.toString()?:"")};var threshold by remember(initial?.id){mutableStateOf(initial?.lowStockThreshold?.toString()?:"1")};var enabled by remember(initial?.id){mutableStateOf(initial?.isEnabledForStore?:true)};var opening by remember(initial?.id){mutableStateOf("0")}
    Card{Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(if(initial==null)"ثبت کالای مستقل" else "ویرایش کالا",fontWeight=FontWeight.Bold);OutlinedTextField(name,{name=it},label={Text("نام")},modifier=Modifier.fillMaxWidth());OutlinedTextField(sku,{sku=it},label={Text("SKU")},modifier=Modifier.fillMaxWidth());OutlinedTextField(barcode,{barcode=it},label={Text("بارکد / QR")},modifier=Modifier.fillMaxWidth());OutlinedTextField(category,{category=it},label={Text("دسته‌بندی")},modifier=Modifier.fillMaxWidth());OutlinedTextField(price,{price=it},label={Text("قیمت")},modifier=Modifier.fillMaxWidth());OutlinedTextField(threshold,{threshold=it},label={Text("حد هشدار موجودی")},modifier=Modifier.fillMaxWidth());Row{Checkbox(enabled,{enabled=it});Text("فعال در مغازه")};if(initial==null&&enabled)OutlinedTextField(opening,{opening=it},label={Text("موجودی اولیه مغازه")},modifier=Modifier.fillMaxWidth());Button({if(name.isNotBlank())onSave((initial?:ProductEntity(name=name)).copy(name=name,sku=sku.ifBlank{null},barcode=barcode.ifBlank{null},category=category.ifBlank{null},price=price.toDoubleOrNull()?:0.0,lowStockThreshold=threshold.toIntOrNull()?:1,isEnabledForStore=enabled,updatedAt=System.currentTimeMillis()),opening.toDoubleOrNull()?:0.0)},Modifier.fillMaxWidth()){Text("ذخیره")}}}
}

@Composable fun InventoryScreen(nav:NavHostController){val ctx=LocalContext.current;val store=remember{LocalStore.get(ctx)};val scope=rememberCoroutineScope();var warehouse by remember{mutableIntStateOf(LocalStore.WAREHOUSE_STORE)};var list by remember{mutableStateOf<List<InventoryRow>>(emptyList())};var selected by remember{mutableStateOf<InventoryRow?>(null)};var err by remember{mutableStateOf<String?>(null)}
    suspend fun load(){list=store.inventory(warehouse)};LaunchedEffect(warehouse){load()}
    Shell(nav,"انبار و موجودی"){WarehousePicker(warehouse){warehouse=it};ErrorText(err);LazyColumn(Modifier.weight(1f)){items(list,key={it.product.id}){r->Card(Modifier.fillMaxWidth().padding(vertical=3.dp)){Row(Modifier.padding(12.dp).fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(r.product.name,fontWeight=FontWeight.Bold);Text("موجودی: ${r.quantity} | هشدار: ${r.product.lowStockThreshold}")};TextButton({selected=r}){Text("تعدیل")}}}}}}
    selected?.let{row->InventoryAdjustDialog(row,onDismiss={selected=null}){delta,note->scope.launch{runCatching{store.adjust(row.product.id,warehouse,delta,note)}.onSuccess{selected=null;load()}.onFailure{err=it.message}}}}
}

@Composable private fun InventoryAdjustDialog(row:InventoryRow,onDismiss:()->Unit,onSave:(Double,String?)->Unit){var delta by remember{mutableStateOf("")};var note by remember{mutableStateOf("")};AlertDialog(onDismissRequest=onDismiss,title={Text("تعدیل ${row.product.name}")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("موجودی فعلی: ${row.quantity}");OutlinedTextField(delta,{delta=it},label={Text("تغییر موجودی؛ مثال 5 یا -2")});OutlinedTextField(note,{note=it},label={Text("توضیح")})}},confirmButton={Button({delta.toDoubleOrNull()?.let{onSave(it,note.ifBlank{null})}}){Text("ثبت")}},dismissButton={TextButton(onDismiss){Text("انصراف")}})}

@Composable fun HistoryScreen(nav:NavHostController){val ctx=LocalContext.current;var rows by remember{mutableStateOf<List<MovementRow>>(emptyList())};LaunchedEffect(Unit){rows=LocalStore.get(ctx).movements()};Shell(nav,"تاریخچه ورود و خروج"){LazyColumn(Modifier.weight(1f)){items(rows,key={it.movement.id}){r->Card(Modifier.fillMaxWidth().padding(vertical=3.dp)){Column(Modifier.padding(10.dp)){Text(r.productName,fontWeight=FontWeight.Bold);Text("${LocalStore.warehouseName(r.movement.warehouseId)} | تغییر: ${r.movement.quantityDelta} | مانده: ${r.movement.balanceAfter}");r.movement.note?.let{Text(it)}}}}}}}
