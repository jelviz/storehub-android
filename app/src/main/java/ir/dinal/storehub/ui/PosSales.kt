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
import com.google.android.gms.mlkit.barcode.common.Barcode
import com.google.android.gms.mlkit.codescanner.GmsBarcodeScannerOptions
import com.google.android.gms.mlkit.codescanner.GmsBarcodeScanning
import ir.dinal.storehub.data.*
import kotlinx.coroutines.launch

@Composable fun PosScreen(activity:Activity,nav:NavHostController){val ctx=LocalContext.current;val store=remember{LocalStore.get(ctx)};val scope=rememberCoroutineScope();val cart=remember{mutableStateListOf<CartLine>()};var code by remember{mutableStateOf("")};var customer by remember{mutableStateOf("")};var mobile by remember{mutableStateOf("")};var payment by remember{mutableIntStateOf(2)};var msg by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)}
    fun addProduct(p:ProductEntity){val i=cart.indexOfFirst{it.product.id==p.id};if(i>=0)cart[i]=cart[i].copy(quantity=cart[i].quantity+1) else cart.add(CartLine(p,1.0))}
    fun lookup(c:String){scope.launch{store.findByCode(c)?.let{addProduct(it);code=""}?:run{msg="کالا با این کد پیدا نشد."}}}
    val scanner=remember{GmsBarcodeScanning.getClient(activity,GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build())}
    Shell(nav,"صندوق فروش"){
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){OutlinedTextField(code,{code=it},label={Text("بارکد / SKU")},modifier=Modifier.weight(1f),singleLine=true);Button({if(code.isNotBlank())lookup(code)}){Text("افزودن")};OutlinedButton({scanner.startScan().addOnSuccessListener{b->b.rawValue?.let(::lookup)}.addOnFailureListener{msg=it.message}}){Text("اسکن")}}
        LazyColumn(Modifier.weight(1f)){items(cart,key={it.product.id}){line->Card(Modifier.fillMaxWidth().padding(vertical=3.dp)){Row(Modifier.padding(10.dp).fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(line.product.name,fontWeight=FontWeight.Bold);Text("${money(line.product.price)} × ${line.quantity}")};Row{TextButton({val i=cart.indexOfFirst{it.product.id==line.product.id};if(i>=0&&cart[i].quantity>1)cart[i]=cart[i].copy(quantity=cart[i].quantity-1) else if(i>=0)cart.removeAt(i)}){Text("−")};TextButton({val i=cart.indexOfFirst{it.product.id==line.product.id};if(i>=0)cart[i]=cart[i].copy(quantity=cart[i].quantity+1)}){Text("+")}}}}}}
        Text("جمع: ${money(cart.sumOf{it.product.price*it.quantity})}",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        OutlinedTextField(customer,{customer=it},label={Text("نام مشتری (اختیاری)")},modifier=Modifier.fillMaxWidth());OutlinedTextField(mobile,{mobile=it},label={Text("موبایل مشتری (اختیاری)")},modifier=Modifier.fillMaxWidth());PaymentPicker(payment){payment=it};ErrorText(msg);Busy(busy)
        Button({scope.launch{busy=true;msg=null;runCatching{store.checkout(cart.toList(),payment,customer,mobile)}.onSuccess{id->cart.clear();customer="";mobile="";msg="فروش ثبت شد. شماره داخلی: $id"}.onFailure{msg=it.message};busy=false}},enabled=cart.isNotEmpty()&&!busy,modifier=Modifier.fillMaxWidth()){Text("ثبت فروش و کسر موجودی")}
    }
}

@Composable fun SalesScreen(nav:NavHostController){val ctx=LocalContext.current;val store=remember{LocalStore.get(ctx)};val scope=rememberCoroutineScope();var list by remember{mutableStateOf<List<SaleEntity>>(emptyList())};var details by remember{mutableStateOf<SaleDetails?>(null)};var error by remember{mutableStateOf<String?>(null)};val returnQty=remember{mutableStateMapOf<Long,String>()}
    suspend fun load(){list=store.sales()};LaunchedEffect(Unit){load()}
    Shell(nav,"فروش‌ها و مرجوعی"){ErrorText(error);LazyColumn(Modifier.weight(1f)){items(list,key={it.id}){s->Card(Modifier.fillMaxWidth().padding(vertical=3.dp)){Column(Modifier.padding(10.dp)){Text(s.invoiceNo,fontWeight=FontWeight.Bold);Text("مبلغ: ${money(s.total)} | مرجوعی: ${money(s.returnedTotal)}");TextButton({scope.launch{details=store.saleDetails(s.id);returnQty.clear()}}){Text("جزئیات / مرجوعی")}}}}}}
    details?.let{d->AlertDialog(onDismissRequest={details=null},title={Text("فاکتور ${d.sale.invoiceNo}")},text={LazyColumn(Modifier.heightIn(max=450.dp)){items(d.items,key={it.id}){i->Column(Modifier.padding(vertical=6.dp)){Text(i.name,fontWeight=FontWeight.Bold);Text("خرید: ${i.quantity} | مرجوع شده: ${i.returnedQuantity} | قابل مرجوعی: ${i.quantity-i.returnedQuantity}");OutlinedTextField(returnQty[i.id]?:"",{returnQty[i.id]=it},label={Text("تعداد مرجوعی")},singleLine=true)}}}},confirmButton={Button({scope.launch{val q=returnQty.mapValues{it.value.toDoubleOrNull()?:0.0};runCatching{store.returnSale(d.sale.id,q,"مرجوعی از اپ")}.onSuccess{details=null;load()}.onFailure{error=it.message}}}){Text("ثبت مرجوعی")}},dismissButton={TextButton({details=null}){Text("بستن")}})}
}
