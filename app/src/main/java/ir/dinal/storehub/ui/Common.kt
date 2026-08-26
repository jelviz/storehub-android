package ir.dinal.storehub.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ir.dinal.storehub.data.*
import java.text.NumberFormat
import java.util.Locale

val faLocale=Locale("fa","IR")
fun money(v:Double)=NumberFormat.getNumberInstance(faLocale).format(v)
fun todayPersian()=Jalali.format(Jalali.today())

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Shell(nav:NavHostController,title:String,content:@Composable ColumnScope.()->Unit){Scaffold(topBar={TopAppBar(title={Text(title)},navigationIcon={TextButton({nav.popBackStack()}){Text("بازگشت")}})}){pad->Column(Modifier.padding(pad).padding(12.dp).fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)}}
@Composable fun ErrorText(text:String?){if(!text.isNullOrBlank())Text(text,color=MaterialTheme.colorScheme.error)}
@Composable fun Busy(b:Boolean){if(b)LinearProgressIndicator(Modifier.fillMaxWidth())}

@Composable fun ProductPicker(products:List<ProductEntity>,selected:Long,onSelect:(Long)->Unit){var open by remember{mutableStateOf(false)};val name=products.firstOrNull{it.id==selected}?.name?:"انتخاب کالا";Box{OutlinedButton({open=true},Modifier.fillMaxWidth()){Text(name)};DropdownMenu(open,{open=false}){products.take(300).forEach{p->DropdownMenuItem(text={Text(p.name)},onClick={onSelect(p.id);open=false})}}}}
@Composable fun WarehousePicker(selected:Int,onSelect:(Int)->Unit){var open by remember{mutableStateOf(false)};Box{OutlinedButton({open=true},Modifier.fillMaxWidth()){Text(LocalStore.warehouseName(selected))};DropdownMenu(open,{open=false}){DropdownMenuItem(text={Text("مغازه")},onClick={onSelect(LocalStore.WAREHOUSE_STORE);open=false});DropdownMenuItem(text={Text("دپو")},onClick={onSelect(LocalStore.WAREHOUSE_DEPOT);open=false})}}}
@Composable fun PaymentPicker(selected:Int,onSelect:(Int)->Unit){val labels=mapOf(1 to "نقدی",2 to "کارتخوان",3 to "اقساطی",4 to "ترکیبی",5 to "سایر");var open by remember{mutableStateOf(false)};Box{OutlinedButton({open=true},Modifier.fillMaxWidth()){Text(labels[selected]?:"کارتخوان")};DropdownMenu(open,{open=false}){labels.forEach{(id,name)->DropdownMenuItem(text={Text(name)},onClick={onSelect(id);open=false})}}}}
