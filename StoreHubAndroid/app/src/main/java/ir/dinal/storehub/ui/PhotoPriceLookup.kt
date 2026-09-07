package ir.dinal.storehub.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import ir.dinal.storehub.data.LocalStore
import ir.dinal.storehub.data.PhotoPriceHit
import ir.dinal.storehub.publishing.RankedLocalHit
import kotlinx.coroutines.launch

@Composable
fun PhotoPriceLookupScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val store = remember { LocalStore.get(ctx) }
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<Uri?>(null) }
    var hits by remember { mutableStateOf<List<PhotoPriceHit>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }

    fun lookup(uri: Uri) {
        preview = uri
        scope.launch {
            busy = true
            err = null
            hint = null
            runCatching { store.photoPriceLookup(uri) }
                .onSuccess { result ->
                    hits = result.hits
                    hint = result.message
                }
                .onFailure { err = it.message }
            busy = false
        }
    }

    val takePhoto = rememberCameraLaunch { lookup(it) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) lookup(uri)
    }

    DinalScreen(nav, "قیمت با عکس") { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                DinalHero("قیمت برای مشتری حضوری", "عکس کالا را می‌گیری؛ در کاتالوگ همین فروشگاه می‌گردد و قیمت تومان، موجودی مغازه و کالاهای شبیه را نشان می‌دهد")
            }
            item {
                SectionCard("عکس کالا") {
                    preview?.let { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = "عکس جستجو",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp))
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = takePhoto, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("عکس بگیر")
                        }
                        OutlinedButton(onClick = { gallery.launch("image/*") }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("گالری")
                        }
                    }
                    Text("این جستجو داخل فروشگاه خودت است، نه دیجی‌کالا. ثبت هوشمند سایت جداست.", style = MaterialTheme.typography.bodySmall)
                }
            }
            item { Busy(busy); ErrorText(err); hint?.let { SuccessText(it) } }
            if (!busy && hits.isNotEmpty()) {
                item {
                    Text(
                        if (hits.any { it.kind != RankedLocalHit.SIMILAR }) "پیدا شد در فروشگاه تو" else "کالاهای شبیه در فروشگاه تو",
                        fontWeight = FontWeight.Bold
                    )
                }
                items(hits, key = { it.product.id }) { hit ->
                    PhotoPriceCard(hit, onAddToPos = {
                        nav.navigate("pos")
                        runCatching {
                            nav.getBackStackEntry("pos").savedStateHandle["photo_product_id"] = hit.product.id
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun PhotoPriceCard(hit: PhotoPriceHit, onAddToPos: () -> Unit) {
    val kindLabel = when (hit.kind) {
        RankedLocalHit.BARCODE -> "بارکد"
        RankedLocalHit.EXACT -> "همین کالا"
        else -> "مشابه"
    }
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ProductThumb(hit.product, size = 72.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(hit.product.name, fontWeight = FontWeight.Bold)
                    AssistChip(onClick = {}, label = { Text(kindLabel) })
                    if (hit.note.isNotBlank()) Text(hit.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!hit.product.isEnabledForStore) Text("در مغازه فعال نیست", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Text(toman(hit.product.price), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("موجودی مغازه: ${hit.storeAvailable.toQty()}  •  دپو: ${hit.depotAvailable.toQty()}")
            Button(onClick = onAddToPos, modifier = Modifier.fillMaxWidth(), enabled = hit.product.isEnabledForStore && hit.storeAvailable > 0) {
                Icon(Icons.Rounded.AddShoppingCart, null); Spacer(Modifier.width(6.dp)); Text("افزودن به صندوق")
            }
        }
    }
}
