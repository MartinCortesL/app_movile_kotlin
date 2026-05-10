@file:Suppress("PreviewAnnotationInFunctionWithParameters")

package com.example.proyecto

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.LocationManager
import android.media.ExifInterface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RawRes
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.example.proyecto.ui.theme.ProyectoTheme
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import androidx.core.net.toUri
import androidx.room.Room
import com.example.proyecto.db.AppDB
import com.example.proyecto.db.Observacion
import javax.crypto.SecretKey

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProyectoTheme {
                ProyectoApp()
            }
        }
    }
}
private var mediaPlayer: MediaPlayer? = null

@Preview(showSystemUi = true)
@Composable
fun ProyectoApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var showDetailScreen by rememberSaveable { mutableStateOf(true) }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    var resultadoClasificacion by remember { mutableStateOf<Modelo?>(null) }
    var especieSeleccionada by remember { mutableStateOf<Especie?>(null) }
    var especie_historial by remember { mutableStateOf<Observacion?>(null) }
    var previousDestination by remember { mutableStateOf(AppDestinations.HOME) }


    val context = LocalContext.current
    val termsAccepted by DataStoreManager
        .isTermsAccepted(context)
        .collectAsState(initial = false)
    val scope = rememberCoroutineScope()

    // Leemos el estado persistente de los permisos
    val camara_permission by DataStoreManager
        .isPermissionsGranted(context)
        .collectAsState(initial = false)

    // Definición del lanzador de la galería
    val MAX_SIZE_MB = 1.5 //tamaño maximo de la imagen
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val sizeMB = getFileSizeInMB(context, uri)

            if (sizeMB <= MAX_SIZE_MB) {
                // Persistir permiso
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                capturedImageUri = uri
                currentDestination = AppDestinations.PREV
            }else{
                //Rechazar imagen
                Toast.makeText(
                    context,
                    "La imagen pesa ${"%.2f".format(sizeMB)} MB. Máximo permitido: $MAX_SIZE_MB MB",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Instancia de la base de datos externa
    val db = remember {  Room.databaseBuilder(
        context.applicationContext,
        AppDB::class.java, "Observacion_v2"
    ).build()
    }
//
//    // Observar los datos (esto se puede usar en HistorialScreen)
//    val historialFotos by fotoDao.obtenerTodas().collectAsState(initial = emptyList())

    if (!termsAccepted) {
        // Pantalla fuera del menú
        DetailScreen(
            onBack = { scope.launch {
                DataStoreManager.setTermsAccepted(context)
            } }
        )

    } else
        {
            // Si ya aceptó términos, verificamos permisos
            if (camara_permission) {
                // DETALLE CLAVE: Ocultamos el menú si estamos en PREVIEW
                val showNavigationMenu = currentDestination != AppDestinations.PREV &&
                                         currentDestination != AppDestinations.LOAD &&
                                         currentDestination != AppDestinations.REST &&
                                         currentDestination != AppDestinations.FAIL
                val keepHistory = previousDestination == AppDestinations.HIST &&
                        currentDestination == AppDestinations.REST
                if (!keepHistory) {
                    especie_historial = null
                }
                val myItemColors = NavigationSuiteDefaults.itemColors(
                    navigationBarItemColors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationSuiteScaffold(
                    layoutType = if (showNavigationMenu) NavigationSuiteType.NavigationBar else NavigationSuiteType.None,
                    navigationSuiteColors = NavigationSuiteDefaults.colors(
                        navigationBarContainerColor = MaterialTheme.colorScheme.background,
                        navigationBarContentColor = MaterialTheme.colorScheme.background,
                    ),
                    containerColor = Color.White,
                    navigationSuiteItems = {
                        AppDestinations.entries.filter {
                            it != AppDestinations.HOME && it != AppDestinations.PREV && it != AppDestinations.LOAD &&
                                    it != AppDestinations.REST && it != AppDestinations.FAIL && it != AppDestinations.SPECIE
                        }.forEach {
                            item(
                                icon = {
                                    Icon(
                                        painter = painterResource(id = it.iconRes),
                                        contentDescription = it.label,
                                        modifier = Modifier.size(34.dp),
                                        tint = Color.Unspecified
                                    )
                                },
                                label = { Text(it.label, style = TextStyle(fontSize = 14.sp)) },
                                selected = it == currentDestination,
                                onClick = { currentDestination = it },
                                colors = myItemColors
                            )
                        }
                    }
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        floatingActionButton = {
                            if (
                                currentDestination != AppDestinations.HOME
                                && currentDestination != AppDestinations.PREV
                                && currentDestination != AppDestinations.LOAD
                                && currentDestination != AppDestinations.REST
                                && currentDestination != AppDestinations.FAIL
                                ) {
                                Boton_inicio(onClick = {
                                    currentDestination = AppDestinations.HOME
                                })
                            }
                        }
                    ) { innerPadding ->
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color.White // Fuerza a que todo lo que esté dentro sea blanco
                        ) {
                            when (currentDestination) {
                                AppDestinations.HOME -> {
                                    // Pasamos la lógica de navegación a la pantalla principal
                                    PantallaPrincipal(
                                        modifier = Modifier.padding(innerPadding),
                                        onImageCaptured = { uri ->
                                            previousDestination = currentDestination
                                            capturedImageUri = uri
                                            currentDestination = AppDestinations.PREV
                                        },
                                        onOpenGallery = { galleryLauncher.launch(arrayOf("image/*")) }
                                    )
                                }

                                AppDestinations.CON -> ConScreen(
                                    Modifier.padding(innerPadding),
                                    onGotoDetail = {
                                        showDetailScreen = false
                                        previousDestination = currentDestination
                                    })

                                AppDestinations.IMP -> ImpScreen(
                                    Modifier.padding(innerPadding),
                                    onFail = {
                                        previousDestination = currentDestination
                                    })

                                AppDestinations.GUIA -> GuiaScreen(
                                    Modifier.padding(innerPadding),
                                    onFail = {
                                        previousDestination = currentDestination
                                    })

                                AppDestinations.HIST -> HistScreen(
                                    Modifier.padding(innerPadding),
                                    db,
                                    onAvanza = { obs ->
                                        previousDestination = currentDestination
                                        especie_historial = obs
                                        currentDestination = AppDestinations.REST
                                    }
                                )

                                AppDestinations.LEGAL -> LegalScreen(Modifier.padding(innerPadding))
                                AppDestinations.PREV -> {
                                    capturedImageUri?.let { uri ->
                                        PhotoPreviewScreen(
                                            uri = uri,
                                            onBack = {
                                                currentDestination = AppDestinations.HOME
                                                previousDestination = currentDestination
                                            },
                                            onIdentify = {
                                                currentDestination = AppDestinations.LOAD
                                            }  // Nueva lambda)
                                        )
                                    }
                                }

                                AppDestinations.LOAD -> {
                                    capturedImageUri?.let { uri ->
                                        LoadScreen(
                                            uri,
                                            onResultReady = { resultado ->
                                                resultadoClasificacion = resultado
                                                previousDestination = currentDestination
                                                currentDestination = AppDestinations.REST
                                            },
                                            onFail = { currentDestination = AppDestinations.FAIL }
                                        )
                                    }
                                }

                                AppDestinations.REST -> ResultScreen(
                                    context,
                                    onBack = {
                                        currentDestination = AppDestinations.HOME
                                        previousDestination = currentDestination
                                    },
                                    resultado = resultadoClasificacion,
                                    onInfo = { especie ->
                                        especieSeleccionada = especie
                                        currentDestination = AppDestinations.SPECIE
                                    },
                                    db,
                                    especie_historial
                                )

                                AppDestinations.FAIL -> FailScreen(onBack = {
                                    currentDestination = AppDestinations.HOME
                                    previousDestination = currentDestination
                                })

                                AppDestinations.SPECIE -> SpecieScreen(
                                    {
                                        currentDestination = AppDestinations.HOME
                                        previousDestination = currentDestination
                                    },
                                    especieSeleccionada,
                                    context
                                )
                            }
                        }
                    }
                }
            }else{
                PermissionManager(onPermissionsResult = { granted ->
                    if (granted) {
                        scope.launch { DataStoreManager.setPermissionsGranted(context) }
                    }
                })
            }
        }
    }


enum class AppDestinations(
    val label: String,
    val iconRes: Int,
) {
    HOME("Inicio", R.drawable.huella),
    CON("Con.", R.drawable.huella),
    IMP("Imp.", R.drawable.importancia),
    GUIA("Guía", R.drawable.guia),
    HIST("Hist.", R.drawable.historial),
    LEGAL("Legal", R.drawable.legal),
    PREV("Preview",R.drawable.huella),
    LOAD("Cargando",R.drawable.huella),
    REST("resultados",R.drawable.huella),
    FAIL("error",R.drawable.huella),
    SPECIE("especie", R.drawable.huella)
}

/*
    Funciones de diseño
 */
@Composable //pantalla de la conservación
fun ConScreen(modifier: Modifier = Modifier, onGotoDetail: () -> Unit) {
    val context = LocalContext.current
    val jsonString = remember {
        loadJSON(context, R.raw.fichas)
    }
    val root = JsonParser.parseString(jsonString).asJsonArray
    val conserva = root.firstOrNull() {
        it.asJsonObject.get("id").asString == "Conservacion"
    }?.asJsonObject

    var indice= 0

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp,5.dp)
            .background(Color.White),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item{
            Text(
                text = "Conservación de las especies",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Búhos, lechuzas y tecolotes (Orden Strigiformes)",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(15.dp))

            Image(
                painter = painterResource(id = R.drawable.conservacion_1),
                contentDescription = "Ejemplo de especies del Orden Strigiformes",
                modifier = Modifier.size(360.dp)
            )

            conserva?.let { l ->
                val sections = l.getAsJsonArray("aves")

                sections.forEach { element ->
                    val section = element
                    indice = indice + 1
                    Text(
                        text = section.asString,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Justify
                    )
                    Spacer(modifier = Modifier.height(5.dp))

                    if (indice == 3){
                        Image(
                            painter = painterResource(id = R.drawable.conservacion_2),
                            contentDescription = "Ejemplo de especies del Orden Strigiformes",
                            modifier = Modifier.size(360.dp)
                                .padding(10.dp,3.dp)
                        )
                    }else if(indice == 4){
                        Image(
                            painter = painterResource(id = R.drawable.conservacion_3),
                            contentDescription = "Ave de la especie: Strix occidentalis",
                            modifier = Modifier.size(360.dp)
                                .padding(10.dp,2.dp)
                        )
                    }
                }
            }

            Spacer( modifier = Modifier.height(40.dp))

            indice = 0
            Text(
                text = "Serpientes de Cascabel (Género Crotalus)",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(15.dp))
            Image(
                painter = painterResource(id = R.drawable.conservacion_5),
                contentDescription = "Ejemplo de Cascabel",
                modifier = Modifier.size(350.dp)
            )

            conserva?.let { l ->
                val sect = l.getAsJsonArray("serpientes")

                sect.forEach { element ->
                    val section = element
                    indice = indice + 1
                    Text(
                        text = section.asString,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Justify
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    if (indice == 2){
                        Image(
                            painter = painterResource(id = R.drawable.conservacion_4),
                            contentDescription = "Programa de accion para la conservación de las especies, SEMANART",
                            modifier = Modifier.size(350.dp)
                                .padding(10.dp,5.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(15.dp))
            Text(
                text = "Para más información",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(10.dp))
            conserva?.let { l ->
                val sect = l.getAsJsonArray("referencias")
                
                sect.forEach { element ->
                    val section = "* " + element.asString

                    Text(
                        text = section,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Justify
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                }

            }
        }
    }
}

@Composable //pantalla de la importancia
fun ImpScreen(modifier: Modifier = Modifier, onFail: () -> Unit) {
    val context = LocalContext.current
    val jsonString = remember {
        loadJSON(context, R.raw.fichas)
    }
    val root = JsonParser.parseString(jsonString).asJsonArray
    val conserva = root.firstOrNull() {
        it.asJsonObject.get("id").asString == "Importancia"
    }?.asJsonObject
    var indice = 0

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp,10.dp)
            .background(Color.White),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item{
            Text(
                text = "Importancia Ecólogica y Cultural",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Búhos, lechuzas y tecolotes (Orden Strigiformes)",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(10.dp))

            Image(
                painter = painterResource(id = R.drawable.importancia_1),
                contentDescription = "Imagen de ejemplos de espcies del Orden Strigiformes",
                modifier = Modifier.size(350.dp,400.dp)
                    .padding(10.dp,5.dp)
            )

            conserva?.let { l ->
                val sections = l.getAsJsonArray("aves")

                sections.forEach { element ->
                    val section = element
                    indice = indice + 1
                    Text(
                        text = section.asString,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Justify
                    )
                    Spacer(modifier = Modifier.height(5.dp))

                    if (indice == 5){
                        Image(
                            painter = painterResource(id = R.drawable.importancia_2),
                            contentDescription = "Búho comiendo un raton",
                            modifier = Modifier.size(370.dp)
                                .padding(10.dp,5.dp)
                        )
                    }
                }
            }

            Spacer( modifier = Modifier.height(30.dp))

            indice = 0
            Text(
                text = "Serpientes de Cascabel (Género Crotalus)",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(5.dp))

            Image(
                painter = painterResource(id = R.drawable.importancia_4),
                contentDescription = "Representación cultural: Quetzalcoalt",
                modifier = Modifier.size(370.dp)
                    .padding(10.dp,0.dp)
            )

            conserva?.let { l ->
                val sect = l.getAsJsonArray("serpientes")

                sect.forEach { element ->
                    val section = element

                    indice = indice + 1
                    Text(
                        text = section.asString,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Justify
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    if (indice == 5){
                        Image(
                            painter = painterResource(id = R.drawable.importancia_3),
                            contentDescription = "Ejemplo de especie de Cascabel",
                            modifier = Modifier.size(370.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(25.dp))
            Text(
                text = "Para más información",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(10.dp))
            conserva?.let { l ->
                val sect = l.getAsJsonArray("referencias")

                sect.forEach { element ->
                    val section = "* " + element.asString

                    Text(
                        text = section,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Justify
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                }

            }
        }
    }
}

@Composable
fun GuiaScreen(modifier: Modifier = Modifier,onFail: () -> Unit) {
    val context = LocalContext.current
    val jsonString = remember {
        loadJSON(context, R.raw.fichas)
    }
    val root = JsonParser.parseString(jsonString).asJsonArray
    val guia = root.firstOrNull() {
        it.asJsonObject.get("id").asString == "guia"
    }?.asJsonObject

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(15.dp,5.dp)
            .background(Color.White),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        item{
            guia?.let { l ->
                val info = l.getAsJsonPrimitive("descripcion")
                val acciones = l.getAsJsonObject("acciones")

                Text(
                    text = info.asString,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Justify
                )
                Spacer(modifier = Modifier.height(7.dp))
                /// primera parte
                Text(
                    text = "¿Qué hacer ante una mordedura de una serpiente de cascabel",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(15.dp))
                Text(
                    text = "Primeros síntomas",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(15.dp))

                val sint = l.getAsJsonArray("sintomas")
                for(i in 0..sint.size()-1){
                    Text(
                        text = sint.get(i).asString,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Justify
                    )
                    Spacer(modifier = Modifier.height(9.dp))
                }
                Spacer(modifier = Modifier.height(15.dp))
                Text(
                    text = "Primeros Auxilios",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(15.dp))
                val auxilios = acciones.getAsJsonArray("auxilios")
                for (i in 0..auxilios.size()-1){
                    Text(
                        text = auxilios.get(i).asString,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Justify
                    )
                    Spacer(modifier = Modifier.height(9.dp))
                }
                Spacer(modifier = Modifier.height(15.dp))
                Text(
                    text = "Acciones ha evitar",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(15.dp))
                val evitar = acciones.getAsJsonArray("evitar")
                for (i in 0..evitar.size()-1){
                    Text(
                        text = evitar.get(i).asString,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Justify
                    )
                    Spacer(modifier = Modifier.height(9.dp))
                }
                Spacer(modifier = Modifier.height(15.dp))
                Text(
                    text = "Recomendaciones",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(15.dp))
                val recom = acciones.getAsJsonArray("recomendaciones")
                for (i in 0..recom.size()-1){
                    Text(
                        text = recom.get(i).asString,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Justify
                    )
                    Spacer(modifier = Modifier.height(9.dp))
                }
                Spacer(modifier = Modifier.height(40.dp))
                /// segunda parte
                Text(
                    text = "¿Qué hacer ante un encuentro con una serpiente de cascabel",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(15.dp))

                Text(
                    text = "Lo que se debe hacer",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(15.dp))
                val si_hacer = acciones.getAsJsonArray("si_hacer")

                for( i in 0..2){
                    Text(
                        text = si_hacer.get(i).asString,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Justify
                    )
                    Spacer(modifier = Modifier.height(9.dp))
                }
                Spacer(modifier = Modifier.height(15.dp))
                Text(
                    text = "Lo que no se debe hacer",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(15.dp))
                val no_hacer = acciones.getAsJsonArray("no_hacer")
                for(i in 0..2){
                    Text(
                        text = no_hacer.get(i).asString,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Justify
                    )
                    Spacer(modifier = Modifier.height(9.dp))
                }
                Spacer(modifier = Modifier.height(15.dp))
                //Referencias
                Text(
                    text = "Para más información",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(10.dp))

                val sect = l.getAsJsonArray("referencias")

                sect.forEach { element ->
                    val section = "• " + element.asString

                    Text(
                        text = section,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Justify
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                }

            }
        }
    }

}

@Composable
fun HistScreen(modifier: Modifier = Modifier, db: AppDB, onAvanza: (Observacion) -> Unit) {
    var obs by remember { mutableStateOf<List<Observacion>>(emptyList()) }

    LaunchedEffect(Unit) {
        obs = withContext(Dispatchers.IO) {
            db.obsDAO().getAll()
        }
    }
    var page by remember { mutableStateOf(0) }
    val pageSize = 20
    val pagedObs = obs.drop(page * pageSize).take(pageSize)
    Column {
        Text(
            modifier = modifier
                .fillMaxWidth()
                .padding(10.dp)
                .background(Color.White),
            text = "Observaciones",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        LazyColumn(
            state = rememberLazyListState(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (pagedObs.isNotEmpty()){
                items(pagedObs.size) { index ->
                    val item = pagedObs[index]

                    val key = cifrado_256.stringToKey(obs[index].id)
                    val text = cifrado_256.decrypt(
                        obs[index].fecha,
                        key
                    )
                    OutlinedCard(
                        onClick = {
                           // val datos = Modelo(uri.toUri(),nombre,conf.toFloat())
                            onAvanza(item)
                        },
                        modifier = modifier.height(80.dp)
                            .fillMaxWidth()
                            .padding(10.dp,2.dp,10.dp,1.dp)
                            .semantics{// Describe qué hace la tarjeta
                                contentDescription = "Visualizar la observación"},
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    ){
                        Box(
                            modifier = modifier.fillMaxWidth()
                                .height(80.dp)
                                .padding(5.dp,2.dp),
                            //contentAlignment = Alignment.T,
                        ) {
                            Text(
                                text = "Observación ${index + 1 + page * pageSize}: " + text,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                item{
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp,2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { if (page > 0) page-- },
                            enabled = page > 0
                        ) {
                            Text("Anterior")
                        }

                        Button(
                            onClick = {
                                if ((page + 1) * pageSize < obs.size) page++
                            },
                            enabled = (page + 1) * pageSize < obs.size
                        ) {
                            Text("Siguiente")
                        }
                    }
                }
            }
        }
    }
}
//@Preview
@Composable
fun LegalScreen(modifier: Modifier = Modifier){
    val context = LocalContext.current
    val jsonString = remember {
        loadJSON(context, R.raw.fichas)
    }
    val root = JsonParser.parseString(jsonString).asJsonArray
    val legal = root.firstOrNull() {
        it.asJsonObject.get("id").asString == "legal"
    }?.asJsonObject

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp, 5.dp)
            .background(Color.White),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        legal?.let { l ->
            val sections = l.getAsJsonArray("sections")

            sections.forEach { element ->

                val section = element.asJsonObject
                val title = section.get("title").asString
                val content = section.get("content").asString

                item {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Justify
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
fun DetailScreen(onBack: () -> Unit) {
    val listState = rememberLazyListState()
    val hasReachedEnd by remember {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val totalItems = listState.layoutInfo.totalItemsCount

            if (visibleItems.isEmpty()) {
                false
            } else {
                val lastVisibleItem = visibleItems.last()
                lastVisibleItem.index == totalItems - 1
            }
        }
    }

    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val jsonString = remember {
        loadJSON(context, R.raw.fichas)
    }
    var isChecked by remember { mutableStateOf(false) }
    val root = JsonParser.parseString(jsonString).asJsonArray
    val legal = root.firstOrNull() {
        it.asJsonObject.get("id").asString == "legal"
    }?.asJsonObject

    Column(
        modifier = Modifier.fillMaxSize().background(Color.White),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        OutlinedCard(
            modifier = Modifier.height(450.dp).
                        width(400.dp)
                        .padding(15.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                legal?.let { l ->
                    val sections = l.getAsJsonArray("sections")

                    sections.forEach { element ->

                        val section = element.asJsonObject
                        val title = section.get("title").asString
                        val content = section.get("content").asString

                        item {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = content,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Justify
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.Start
        )
        {
            Text(
                text = "¿He leído y\n aceptado los\n términos y \ncondiciones?",
                modifier = Modifier
                    .padding(start = 16.dp, top = 10.dp),
                fontSize = 16.sp,
                textAlign = TextAlign.Justify
            )
            Row(
                modifier = Modifier.padding(start=16.dp,top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { checked ->
                        isChecked = checked

                        if (checked && hasReachedEnd) {
                            scope.launch {
                                DataStoreManager.setTermsAccepted(context)
                                onBack()
                            }
                        }
                    },
                    enabled = hasReachedEnd
                )
                Text("Aceptar")
            }
        }
    }
}
//@Preview
@Composable
fun PantallaPrincipal(modifier: Modifier = Modifier, onImageCaptured: (Uri) -> Unit, onOpenGallery: () -> Unit){
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp,10.dp)
            .background(Color.White),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedCard(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            border = BorderStroke(4.dp, MaterialTheme.colorScheme.primary)
        ) {
            // Aquí se muestra la cámara activa
            CameraView(onImageCaptured = onImageCaptured)
        }
        IconButton(
            onClick = onOpenGallery,
            modifier = Modifier.size(200.dp),
        ){
            Icon(
                painter = painterResource(id = R.drawable.gallery),
                contentDescription = "Entrar a la galería",
                modifier = Modifier.size(100.dp),
                tint = Color.Unspecified
            )
        }
    }
}

@Composable
fun LoadScreen(
    imageUri: Uri,
    onResultReady: (Modelo) -> Unit,
    onFail: () -> Unit
){

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        // Llamamos a la función de IA
        val resultado = clasificarImagen(context, imageUri)

        // Pequeña espera para que se vea el GIF
        kotlinx.coroutines.delay(1000)
       // Log.d("Confianza",resultado.confianza.toString())
       // Log.d("Imagen",resultado.uri.toString())
       // Log.d("Clase",resultado.nombreClase)
        // Pasamos el resultado a la siguiente pantalla
        if(resultado.confianza < 60.0F){
            onFail()
        }else{
            onResultReady(resultado)
        }
    }

    // 1. Configuramos el loader para que entienda GIFs
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
    Column(
        modifier = Modifier.fillMaxSize().background(Color.White),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        AsyncImage(
            model = R.drawable.loading, // Referencia a tu GIF local
            contentDescription = null,
            imageLoader = imageLoader,
            modifier = Modifier.size(320.dp),
        )
        Spacer(Modifier.height(40.dp))
        Text(
            text = "Procesando imagen",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Por favor\nEspere",
            style = MaterialTheme.typography.titleLarge
        )
    }
}
@Composable
fun ShareScreen(onBack: () -> Unit){
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Text(
            text = "Información Compartida Correctamente",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(40.dp))
        Icon(
            painter = painterResource(id = R.drawable.exito),
            contentDescription = "exito",
            modifier = Modifier.size(100.dp),
            tint = Color.Unspecified
        )
        Spacer(Modifier.height(40.dp))
        OutlinedCard(
            modifier = Modifier
                .height(300.dp)
                .width(300.dp)
                .padding(15.dp),
           colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ) { Text( text = "Imagen va aqui") }
        Spacer(Modifier.height(30.dp))
        Text(
            text = "Nombre Cientifico",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Fecha y hora",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Ubicación",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(30.dp))
        Button(
            onClick = onBack
        ) {
            Icon(
                painter = painterResource(id = R.drawable.left),
                contentDescription = "Volver",
                modifier = Modifier.size(25.dp),
                tint = Color.Unspecified
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "Volver",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
@Composable
fun FailScreen(onBack: () -> Unit){
    Column(
        modifier = Modifier.fillMaxSize().background(Color.White),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Spacer(Modifier.height(40.dp))
        Icon(
            painter = painterResource(id = R.drawable.alerta),
            contentDescription = "Error: Especie no identificada",
            modifier = Modifier.size(150.dp),
            tint = Color.Unspecified
        )
        Spacer(Modifier.height(40.dp))
        Text(
            text = "Especie no Identificada",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(60.dp))
        Text(
            text = "Pruebe tomando otra foto",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(60.dp))
        Button(
            onClick = onBack
        ) {
            Icon(
                painter = painterResource(id = R.drawable.left),
                contentDescription = "Volver",
                modifier = Modifier.size(25.dp),
                tint = Color.Unspecified
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "Volver",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun ResultScreen(
    context: Context,
    onBack: () -> Unit,
    resultado : Modelo?,
    onInfo: (especie: Especie) -> Unit,
    db: AppDB,
    especie_h: Observacion?)
{
    val location by DataStoreManager
        .isLocacionaccepted(context)
        .collectAsState(initial = false)
    var aceptado by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(location) {
        aceptado = location
    }
    val scope = rememberCoroutineScope()

    if(location){
        aceptado = true
    }
    else{
        PermissionLocation(onPermissionsResult = { granted ->
            if (granted) {
                scope.launch { DataStoreManager.setLocacionAccepted(context) }
            }
            aceptado = granted
        })
    }
    val textoNormal: String
    val especie : Especie
    var lat : String
    var lon : String
    var res : Float?
    var clase: String
    var key : SecretKey
    var uri : Uri?
    var fecha_hora : String

    if(especie_h != null){
        key = cifrado_256.stringToKey(especie_h.id)
        lat = cifrado_256.decrypt(especie_h.latitud.toString(),key)
        lon = cifrado_256.decrypt(especie_h.longitud.toString(), key)
        res = cifrado_256.decrypt(especie_h.confianza,key).toFloat()
        clase = cifrado_256.decrypt(especie_h.nom_cientifico,key)
        uri = especie_h.uri.toUri()
        fecha_hora = cifrado_256.decrypt(especie_h.fecha,key)
        textoNormal = clase
        especie = JSONespecies(context,textoNormal)
    }else{
        lat = ""
        lon = ""
        res = resultado?.confianza
        clase = resultado?.nombreClase.toString()
        uri = resultado?.uri
        fecha_hora = Fecha_Hora()
        textoNormal = resultado?.nombreClase ?: "especie"
        especie = JSONespecies(context,textoNormal)
    }
    Column(
        modifier = Modifier.fillMaxSize()
            .background(Color.White)
            .padding(10.dp,5.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Hay un ${"%.2f".format(res)}% de probabilidad\nde que sea un:",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(15.dp))
        Text(
            clase,
            style = MaterialTheme.typography.titleLarge
            )
        Spacer(Modifier.height(5.dp))
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp,5.dp)
                .sizeIn(
                    minWidth = 100.dp, // tamaño mínimo del card
                    minHeight = 100.dp,
                    maxWidth = 300.dp, // tamaño máximo
                    maxHeight = 270.dp
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ) {
          //  Log.e("uri",uri.toString())
            AsyncImage(
                model = uri,
                contentDescription = "animal identificado",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().
            padding(40.dp,0.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        )
        {
            //Spacer(Modifier.width(80.dp))
            IconButton(
                onClick = {onInfo(especie)},
                modifier = Modifier.size(45.dp),
            ){
                Icon(
                    painter = painterResource(id = R.drawable.info),
                    contentDescription = "Información",
                    modifier = Modifier.size(40.dp),
                    tint = Color.Unspecified
                )
            }
            //Spacer(Modifier.width(160.dp))
            IconButton(
                onClick = { showDialog = true },
                modifier = Modifier.size(40.dp)
            ){
                Icon(
                    painter = painterResource(id = R.drawable.share),
                    contentDescription = "Compartir",
                    modifier = Modifier.size(30.dp),
                    tint = Color.Unspecified
                )
            }
            if(showDialog){
                val animal = "Probabilidad de que la especie sea: ${clase}, es: ${"%.2f".format(res)}%"
                compartir(context,uri.toString(), animal,
                    {showDialog = false})
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(35.dp,0.dp),
            horizontalArrangement = Arrangement.SpaceBetween
            )
        {
            //Spacer(Modifier.width(60.dp))
            Text(
                "Información")
            //Spacer(Modifier.width(130.dp))
            Text(
                "Compartir"
            )
        }
        Spacer(Modifier.height(30.dp))
        Text(
            text = fecha_hora,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(15.dp))
        if(aceptado){
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED) {

                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (location != null) {
                    lat = "%.3f".format(location.latitude)   // redondea a 3 decimales
                    lon = "%.3f".format(location.longitude)
                    Text(text = "Latitud: $lat \nLongitud: $lon",
                        style = MaterialTheme.typography.titleMedium)
                } else {
                    Text(text = "Ubicación no disponible")
                }
            }
        }
        Spacer(Modifier.height(25.dp))
        Button(
            onClick = onBack
        ) {
            Icon(
                painter = painterResource(id = R.drawable.left),
                contentDescription = "Volver al inicio",
                modifier = Modifier.size(20.dp),
                tint = Color.Unspecified
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "Volver al inicio",
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(Modifier.height(50.dp))
        Text(
            text = "Los resultados mostrados solo son aproximaciones",
            style = MaterialTheme.typography.bodyMedium
        )
    }
    if(especie_h == null){
        val id = cifrado_256.generateKey()
        val id_string = cifrado_256.keyToString(id)
        val Obs = Observacion(
            id_string,
            cifrado_256.encrypt(especie.nombre_comun,id),
            cifrado_256.encrypt(especie.nombre_cientifico.toString(),id),
            cifrado_256.encrypt(Fecha_Hora(),id),
            cifrado_256.encrypt(Fecha_Hora(),id),
            cifrado_256.encrypt(lat,id),
            cifrado_256.encrypt(lon,id),
            cifrado_256.encrypt(resultado?.confianza.toString(),id),
            resultado?.uri.toString()
        )

        LaunchedEffect(Unit) {
            try{
                val obs = withContext(Dispatchers.IO) {
                    db.obsDAO().insert(Obs)
                }}
            catch (e: Exception){
                Log.e("DB",e.printStackTrace().toString())
            }
        }
    }
}
@Composable
fun SpecieScreen(onBack: () -> Unit,
                         especie: Especie?,
                         context: Context){
    // Buscamos el ID del drawable dinámicamente
    val image_1 = rememberResourceId(especie?.imagen_1, "raw")
    val image_2 = rememberResourceId(especie?.imagen_2,"raw")
    val mapa = rememberResourceId(especie?.mapa, "raw")
    LazyColumn(
        modifier = Modifier.padding(10.dp)
            .background(Color.White),
        //horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item{
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, 0.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ){
                Button(
                    onClick = onBack
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.left),
                        contentDescription = "Volver al inicio",
                        modifier = Modifier.size(15.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Inicio",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, 5.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${especie?.nombre_comun}",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Spacer(Modifier.height(15.dp))
        }
        item{
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ){
                Icon(
                    painter = painterResource(id = image_1),
                    contentDescription = "imagen de ejemplo",
                    modifier = Modifier.fillMaxWidth().
                        padding(15.dp,5.dp),
                    tint = Color.Unspecified
                )
                Icon(
                    painter = painterResource(id = image_2),
                    contentDescription = "imagen de ejemplo",
                    modifier = Modifier.fillMaxWidth()
                        .padding(15.dp,5.dp),
                    tint = Color.Unspecified
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        item{
            if(!especie?.canto.isNullOrBlank()){
                val canto = rememberResourceId(especie.canto,"raw")
                Row(
                    Modifier.fillMaxWidth().padding(15.dp,2.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {reproducirAudio(context,canto)},
                        colors = ButtonColors(Color.White,Color.Unspecified, Color.White, Color.White)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.audio),
                            contentDescription = "Audio",
                            modifier = Modifier.size(25.dp),
                            tint = Color.Unspecified
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "Escuchar",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        item{
            Text(
                "Nombre cientifico",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Start
            )
            Text(
                especie?.nombre_cientifico.toString(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Left
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Nombre en ingles",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Left
            )
            Text(
                especie?.nombre_ingles.toString(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Left
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Etimologia",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Left
            )
            Text(
                especie?.etimologia.toString(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Justify
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Estado de conservación",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Left
            )
            if(!especie?.canto.isNullOrBlank()){
                Text(
                    "IUCN: "+especie.conservacion[0],
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Left
                )
                Text(
                    "NOM-059-SEMANART-2010: Sin datos",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Left
                )
            }else{
                Text(
                    "IUCN: "+especie?.conservacion[0],
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Left
                )
                Text(
                    "NOM-059-SEMANART-2010: "+especie?.conservacion[1],
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Left
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Descripción",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Left
            )
            Text(
                especie?.descripcion?.joinToString(" ").toString(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Justify
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Tamaño",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Left
            )
            Text(
                especie?.tamano.toString(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Justify
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Reproducción",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Left
            )
            Text(
                especie?.reproduccion.toString(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Justify
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Alimentación",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Left
            )
            Text(
                especie?.alimentacion.toString(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Justify
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Habitat",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Left
            )
            Text(
                especie?.habitat.toString(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Justify
            )
            Spacer(Modifier.height(10.dp))
        }
        item{
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 10.dp ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Distribución",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(10.dp))
                Icon(
                    painter = painterResource(id = mapa),
                    contentDescription = "Mapa de distribucíon",
                    modifier = Modifier.size(370.dp),
                    tint = Color.Unspecified
                )
            }
            Spacer(Modifier.height(20.dp))
        }
        item{
            Text(
                "Referencias",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Left
            )
            Text(
                especie?.referencias.toString(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Justify
            )
        }
    }
}
@Composable
fun Boton_inicio(onClick: () -> Unit) {
    androidx.compose.material3.FloatingActionButton(
        onClick =  {onClick()},
        containerColor = MaterialTheme.colorScheme.tertiary,
        contentColor = Color.Unspecified
    ) {
        Icon(
            painter = painterResource(id = R.drawable.camera),
            contentDescription = "Ir al Inicio",
            modifier = Modifier.size(32.dp)
        )
    }
}
@Composable
fun CameraView(onImageCaptured: (Uri) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraController = remember { LifecycleCameraController(context).apply {
        // Esto vincula la cámara al ciclo de vida del componente
        setImageCaptureTargetSize(CameraController.OutputSize(Size(720, 960)))
        bindToLifecycle(lifecycleOwner)}
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PreviewView(context).apply {
                    controller = cameraController
                }
            }
        )
        // Botón de captura dentro de la cámara
        IconButton(
            onClick = {

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, timeStamp)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.P) {
                        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ProyectoApp")
                    }
                }

                val outputOptions = ImageCapture.OutputFileOptions.Builder(
                    context.contentResolver,
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ).build()


                cameraController.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            // La URI devuelta ahora es una URI de MediaStore (Galería)
                            output.savedUri?.let {  uri ->
                                //val uriString = uri.toString()
                                //saveUri(uriString)   // 👈 guardas para persistencia
                                onImageCaptured(uri)
                            }
                        }
                        override fun onError(exception: ImageCaptureException) {
                            Toast.makeText(context, "Error al guardar", Toast.LENGTH_SHORT)
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.circulo),
                contentDescription = "Tomar Fotografía",
                modifier = Modifier.size(100.dp),
                tint = Color.Unspecified
            )
        }
    }
}
@Composable
fun PhotoPreviewScreen(uri: Uri, onBack: () -> Unit, onIdentify: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally
    )
    {
        Spacer(modifier = Modifier.height(20.dp))
        AsyncImage(
            model = uri,
            contentDescription = "Foto capturada por el usuario",
            modifier = Modifier
                .padding(15.dp)
                .size(360.dp, 500.dp)
        )
        Button(onClick = onBack, modifier = Modifier.padding(16.dp)) {
            Icon(
                painter = painterResource(id = R.drawable.left),
                contentDescription = "volver al inicio",
                modifier = Modifier.size(25.dp),
                tint = Color.Unspecified
            )
            Text("Volver al Inicio")
        }
        Button(onIdentify,
            Modifier.padding(16.dp)
                )
        {
            Text("Identificar")
            Icon(
                painter = painterResource(id = R.drawable.right),
                contentDescription = "avanzar para identificar a la especie",
                modifier = Modifier.size(25.dp),
                tint = Color.Unspecified
            )
        }
    }
}
@Composable
fun PermissionDialog(
    permissionText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Permiso Requerido") },
        text = { Text(text = permissionText) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Aceptar")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
@Composable
fun PermissionManager(
    onPermissionsResult: (Boolean) -> Unit
) {
    var showCameraDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onPermissionsResult(isGranted)
    }

    // Diálogo para Cámara
    if (showCameraDialog) {
        PermissionDialog(
            permissionText = "Esta aplicación necesita acceder a la cámara del dispositivo para tomar fotografias, necesarias para la identificación de las especies.",
            onDismiss = { showCameraDialog = false },
            onConfirm = {
                showCameraDialog = false
                permissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        )
    }

    // Al iniciar, activamos la secuencia de diálogos
    LaunchedEffect(Unit) {
        showCameraDialog = true
    }
}
@Composable
fun PermissionLocation(
    onPermissionsResult: (Boolean) -> Unit
) {
    var showLocationDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onPermissionsResult(isGranted)
    }

    // Diálogo para la ubicación
    if (showLocationDialog) {
        PermissionDialog(
            permissionText = "Esta aplicación necesita acceder a la ubicación del dispositivo para conocer el lugar donde se encontró a la especie.",
            onDismiss = { showLocationDialog = false },
            onConfirm = {
                showLocationDialog = false
                permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
            }
        )
    }

    // Al iniciar, activamos la secuencia de diálogos
    LaunchedEffect(Unit) {
        showLocationDialog = true
    }
}
@Composable
fun compartir(context: Context, uri : String, string: String, onDismiss: () -> Unit){

    AlertDialog(
        onDismissRequest = { onDismiss()},
        modifier = Modifier.background(Color.White),
        //contentColor = MaterialTheme.colorScheme.tertiary,
        title = { Text("Compartir") },
        text = { Text("¿Donde desea compartir la observación?",
                 style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = { compartiriNaturalist(context, uri.toUri(), string)
                    onDismiss()
            },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = Color.Black
                )
                    ) {
                Text("INaturalist")
            }
        },
        dismissButton = {
            Button(onClick = { compartirResultado(context, uri.toUri(), string)
                    onDismiss()
            },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = Color.Black
                )
                ) {
                Text("Redes Sociales")
            }
        }
    )
}
/*
       Funciones de operaciones
 */
//funcion para cargar un json
fun loadJSON(context: Context, @RawRes rawId: Int): String {
    return context.resources.openRawResource(rawId)
        .bufferedReader()
        .use { it.readText() }
}
//funcion para la IA
suspend fun clasificarImagen(
    context: Context,
    imageUri: Uri,
    modelResId: Int = R.raw.resnet50_mobile,
    labelsResId: Int = R.raw.labels
): Modelo = withContext(Dispatchers.IO) {
    var module: Module? = null
    try {
        // 1. Copiar el modelo de RAW a un archivo temporal para que PyTorch pueda leerlo
        val modelPath = getRawFilePath(context, modelResId, "modelo_temp.ptl")
        module = LiteModuleLoader.load(modelPath)

        // 2. Cargar el Bitmap
        val inputStream = context.contentResolver.openInputStream(imageUri) //carga la imagen
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
            ?: throw IllegalArgumentException("No se pudo decodificar la imagen")

        // 3. REDIMENSIONAR MANUALMENTE
        // Esto asegura que el modelo reciba exactamente 224x224 píxeles
        //val scaledBitmap = originalBitmap.scrop(224, 224)
        //val scaledBitmap = resizeAndCropCenter(originalBitmap)
        // A. Corregir la rotación física del sensor
        val rotatedBitmap = fixRotation(context, imageUri, originalBitmap)
        // B. Hacerla cuadrada (Center Crop)
        val squaredBitmap = centerCrop(rotatedBitmap)
        // C. Redimensionar a 224x224 (tamaño exacto de ResNet)
        val finalBitmap = Bitmap.createScaledBitmap(squaredBitmap, 224, 224, true)

        // 3. Preprocesamiento (Conversión a NCHW y Normalización ImageNet)
        // PyTorch espera [1, 3, 224, 224] con media y desviación estándar
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            finalBitmap,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )
        // Liberar memoria de bitmaps que ya no usaremos
        if (rotatedBitmap != originalBitmap) originalBitmap.recycle()
        if (squaredBitmap != rotatedBitmap) rotatedBitmap.recycle()
        // No reciclar finalBitmap hasta después de la inferencia si es necesario

        // 4. Inferencia
        val outputTensor = module.forward(IValue.from(inputTensor)).toTensor()
        val scores = outputTensor.dataAsFloatArray

        // 5. Post-procesamiento
        val labels = context.resources.openRawResource(labelsResId)
            .bufferedReader().use { it.readLines() }

        //val probs = softmax(scores)
        val maxIdx = scores.indices.maxByOrNull { scores[it] } ?: -1
      //  Log.d("DEBUG", "Logits: ${scores.contentToString()}")
        //Log.d("DEBUG", "Probs: ${probs.contentToString()}")

      //  Log.e("correcto","Todo se compiló correctamente " + maxIdx.toString())
        return@withContext Modelo(
            uri = imageUri,
            nombreClase = labels.getOrElse(maxIdx) { "Desconocido" },
            confianza = (if (maxIdx != -1) scores[maxIdx] else 0f) * 100f
        )

    } catch (e: Exception) {
        Log.e("TFLite_Error", "Error en clasificarImagen: ${e.message}", e)
        Modelo(uri = imageUri, nombreClase = "Error: ${e.localizedMessage}", confianza = 0.0f)
    }
}
//funcion para obtener un archivo de raw
fun getRawFilePath(context: Context, resId: Int, fileName: String): String {
    val file = File(context.filesDir, fileName)
    // Opcional: Si el archivo ya existe, puedes elegir no sobrescribirlo para ahorrar tiempo
    context.resources.openRawResource(resId).use { inputStream ->
        FileOutputStream(file).use { outputStream ->
            inputStream.copyTo(outputStream)
        }
    }
    return file.absolutePath
}
//funcion para obtener la fecha y hora actual
fun Fecha_Hora(): String{
    val fechaHoraActual = LocalDateTime.now()
    val formato = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
    val fechaFormateada = fechaHoraActual.format(formato)

    return fechaFormateada
}
//funcion para obtener la especie
fun JSONespecies(context: Context,id: String): Especie{
    return try {
        // Reemplazar espacios por _
        val idFormateado = id.replace(" ", "_")

        // Abrir el JSON desde res/raw
        val inputStream = context.resources.openRawResource(R.raw.especies)
        val reader = InputStreamReader(inputStream)

        // Tipo de lista para Gson
        val listType = object : TypeToken<List<Especie>>() {}.type
        val especies: List<Especie> = Gson().fromJson(reader, listType)

        // Buscar la especie por id
        especies.find { it.id == idFormateado } ?: Especie(
            id = idFormateado,
            nombre_cientifico = "Desconocido",
            nombre_comun = "Desconocido",
            nombre_ingles = "Unknown",
            etimologia = "No disponible",
            conservacion = listOf("No disponible"),
            descripcion = listOf("No disponible"),
            tamano = "No disponible",
            reproduccion = "No disponible",
            alimentacion = "No disponible",
            habitat = "No disponible",
            imagen_1 = "",
            imagen_2 = "",
            mapa = "",
            canto = "",
            referencias = "No disponible"
        )
    } catch (e: Exception) {
        e.printStackTrace()
        // En caso de error, devolver especie por defecto
        Especie(
            id = id.replace(" ", "_"),
            nombre_cientifico = "Desconocido",
            nombre_comun = "Desconocido",
            nombre_ingles = "Unknown",
            etimologia = "No disponible",
            conservacion = listOf("No disponible"),
            descripcion = listOf("No disponible"),
            tamano = "No disponible",
            reproduccion = "No disponible",
            alimentacion = "No disponible",
            habitat = "No disponible",
            imagen_1 = "",
            imagen_2 = "",
            mapa = "",
            canto = "",
            referencias = "No disponible"
        )
    }
}
//funcion para obtener el id de raw
@Composable
fun rememberResourceId(name: String?, type: String): Int {
    val context = LocalContext.current
    return remember(name) {
        context.resources.getIdentifier(name, type, context.packageName)
    }
}
// funcion para reproducir el canto del ave
fun reproducirAudio(context: Context, audioResId: Int) {
    mediaPlayer?.release() // libera el anterior

    mediaPlayer = MediaPlayer.create(context, audioResId)

    mediaPlayer?.apply {
        setOnCompletionListener {
            it.release()
            mediaPlayer = null
        }
        start()
    }
}
// abrir imagen
fun getFileSizeInMB(context: Context, uri: Uri): Double {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    val sizeIndex = cursor?.getColumnIndex(OpenableColumns.SIZE)

    var sizeInBytes = 0L

    cursor?.use {
        if (it.moveToFirst() && sizeIndex != null && sizeIndex != -1) {
            sizeInBytes = it.getLong(sizeIndex)
        }
    }

    return sizeInBytes / (1024.0 * 1024.0)
}
// Corrige la rotación basada en los metadatos EXIF
private fun fixRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
    val inputStream = context.contentResolver.openInputStream(uri)
    val exifInterface = ExifInterface(inputStream!!)
    val orientation = exifInterface.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )
    inputStream.close()

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        else -> return bitmap // No requiere rotación
    }

    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

// Recorta el centro para que la imagen sea cuadrada (evita deformación)
private fun centerCrop(bitmap: Bitmap): Bitmap {
    val size = Math.min(bitmap.width, bitmap.height)
    val x = (bitmap.width - size) / 2
    val y = (bitmap.height - size) / 2
    return Bitmap.createBitmap(bitmap, x, y, size, size)
}
//función para compartir en redes sociales
fun compartirResultado(context: Context, imageUri: Uri, texto: String) {
    val sendIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        // Añadimos el texto (por ejemplo, el nombre de la especie y la confianza)
        putExtra(Intent.EXTRA_TEXT, texto)
        // Añadimos la URI de la imagen
        putExtra(Intent.EXTRA_STREAM, imageUri)
        // Definimos el tipo como imagen (puedes ser más específico como "image/jpeg")
        type = "image/*"
        // Permitimos que otras apps lean esta URI
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val shareIntent = Intent.createChooser(sendIntent, "Compartir clasificación")
    context.startActivity(shareIntent)
}
//funcion para compartir en iNaturalist
fun compartiriNaturalist(context: Context, imageUri: Uri, especieSugerida: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, imageUri)
        // iNaturalist y otras apps de ciencia ciudadana leen el EXTRA_TEXT
        // para pre-rellenar notas o comentarios
        putExtra(Intent.EXTRA_TEXT, "Sugerencia de identificación: $especieSugerida")

        // Intentamos forzar que se abra iNaturalist si está instalada
        setPackage("org.inaturalist.android")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Si el usuario no tiene la app instalada, lo mandamos a la Play Store
        val playStoreIntent = Intent(Intent.ACTION_VIEW,
            Uri.parse("market://details?id=org.inaturalist.android"))
        context.startActivity(playStoreIntent)
    }
}