package com.boywick.wallpaper

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

private val Bg = Color(0xFF02030A)
private val Surf = Color(0xFF0B1230)
private val Blue = Color(0xFF1F5FFF)

data class Wall(val id: String, val thumb: String, val full: String, val author: String)

object Repo {
    private val client = OkHttpClient()

    fun search(query: String, categories: String, page: Int = 1): List<Wall> {
        val q = URLEncoder.encode(query, "UTF-8")
        val url = "https://wallhaven.cc/api/v1/search?q=$q&categories=$categories&purity=100" +
            "&ratios=portrait&atleast=1080x1920&sorting=toplist&topRange=1y&page=$page"
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Erro ${r.code}")
            val arr = JSONObject(r.body!!.string()).getJSONArray("data")
            return List(arr.length()) { i ->
                val p = arr.getJSONObject(i)
                Wall(
                    id = p.getString("id"),
                    thumb = p.getJSONObject("thumbs").getString("large"),
                    full = p.getString("path"),
                    author = "Wallhaven"
                )
            }
        }
    }

    fun bitmap(url: String): Bitmap {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Erro ${r.code}")
            val bytes = r.body!!.bytes()
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            var sample = 1
            while (maxOf(o.outWidth, o.outHeight) / sample > 4096) sample *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o2)
                ?: throw IOException("Imagem inválida")
        }
    }
}

fun saveToGallery(ctx: Context, bmp: Bitmap, name: String) {
    val v = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BoywickWallpaper")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)
        ?: throw IOException("Não foi possível guardar")
    ctx.contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
}

fun setWallpaper(ctx: Context, bmp: Bitmap, flag: Int) {
    WallpaperManager.getInstance(ctx).setBitmap(bmp, null, true, flag)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Blue, background = Bg, surface = Surf)) {
                App()
            }
        }
    }
}

@Composable
fun App() {
    val cats = listOf(
        Triple("Todos", "", "111"),
        Triple("Carros", "cars", "100"),
        Triple("Natureza", "nature", "100"),
        Triple("Anime", "", "010"),
        Triple("Abstrato", "abstract", "100"),
        Triple("Paisagem", "landscape", "100")
    )
    var cat by remember { mutableStateOf(0) }
    var walls by remember { mutableStateOf<List<Wall>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Wall?>(null) }

    LaunchedEffect(cat) {
        loading = true; error = null; walls = emptyList()
        try {
            walls = withContext(Dispatchers.IO) { Repo.search(cats[cat].second, cats[cat].third) }
        } catch (e: Exception) {
            error = e.message ?: "Sem ligação"
        }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        Text(
            "BOYWICK WALLPAPER", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp,
            modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 2.dp)
        )
        Text(
            "Wallpapers 4K · Baixar · Definir", color = Color(0xFF8A98C9), fontSize = 13.sp,
            modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 10.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            itemsIndexed(cats) { i, c ->
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(if (i == cat) Blue else Surf)
                        .clickable { cat = i }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) { Text(c.first, color = Color.White) }
            }
        }
        Box(Modifier.fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Blue)
                error != null -> Text(
                    error!!, color = Color(0xFFFF8A8A),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(walls, key = { it.id }) { w ->
                        AsyncImage(
                            model = w.thumb, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(16.dp))
                                .background(Surf).clickable { selected = w }
                        )
                    }
                }
            }
        }
    }
    selected?.let { Preview(it) { selected = null } }
}

@Composable
fun Preview(w: Wall, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var showOpts by remember { mutableStateOf(false) }

    fun act(okMsg: String, job: (Bitmap) -> Unit) {
        scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { job(Repo.bitmap(w.full)) }
                Toast.makeText(ctx, okMsg, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
            busy = false
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Bg).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = w.thumb, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.weight(1f).aspectRatio(9f / 16f).clip(RoundedCornerShape(20.dp))
            )
            Text("Fonte: Wallhaven", color = Color(0xFF8A98C9), fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp))
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Blue)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { act("Guardado em Imagens/BoywickWallpaper") { saveToGallery(ctx, it, "boywick_${w.id}") } },
                    enabled = !busy, modifier = Modifier.weight(1f)
                ) { Text("BAIXAR 4K") }
                Button(onClick = { showOpts = !showOpts }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("DEFINIR")
                }
            }
            if (showOpts) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "INICIAL" to WallpaperManager.FLAG_SYSTEM,
                        "BLOQUEIO" to WallpaperManager.FLAG_LOCK,
                        "AMBOS" to (WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                    ).forEach { (label, flag) ->
                        OutlinedButton(
                            onClick = { showOpts = false; act("Wallpaper definido!") { setWallpaper(ctx, it, flag) } },
                            enabled = !busy, modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(4.dp)
                        ) { Text(label, fontSize = 12.sp) }
                    }
                }
            }
            TextButton(onClick = onClose) { Text("Fechar", color = Color(0xFF8A98C9)) }
        }
    }
}
