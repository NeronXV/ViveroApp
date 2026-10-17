package com.intutec.viveroapp.feature.plants.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun PlantScreen(onBack:()->Unit,vm:PlantViewModel=hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val p=state.selected
    val context=LocalContext.current
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var photoTarget by rememberSaveable { mutableStateOf("") }
    var chooseCategory by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){success->if(success)cameraPath?.let{vm.photo(Uri.parse(it),photoTarget)}}
    fun takePhoto(){
        try{
            val dir=File(context.filesDir,"product_images").apply{mkdirs()}
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",File(dir,"camera-${UUID.randomUUID()}.jpg"))
            cameraPath=uri.toString();photoTarget=vm.photoTarget();camera.launch(uri)
        }catch(_:Exception){photoError="No se pudo abrir la cámara. Puedes elegir una foto de galería."}
    }
    val cameraPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)takePhoto() else photoError="Permite usar la cámara o elige una foto de galería."}
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->uri?.let{vm.photo(it,photoTarget)}}
    val editable=!state.busy&&!state.pending&&(p?.status!="REVIEW"||state.canPublish)&&p?.status !in listOf("LINKED","PREPARED")
    BackHandler(p!=null||state.busy){if(!state.busy)leaving=true}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(if(p==null)"Plantas por completar" else "Paso ${state.step} de 3",style=MaterialTheme.typography.headlineSmall)
        state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        state.message?.let{Text(it)}
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(state.pending)Button(vm::recover,enabled=!state.busy){Text("Comprobar guardado")}
        if(!state.enabled)Text("Inicia sesión con acceso a Plantas.")
        else if(p==null){
            Button(vm::create,enabled=!state.busy&&!state.pending){Text("Añadir planta")}
            OutlinedButton(vm::retry,enabled=!state.busy){Text("Actualizar")}
            if(state.plants.isEmpty()&&!state.busy)Text("No hay plantas pendientes en esta lista.")
            state.plants.forEach{plant->OutlinedButton({vm.select(plant)},enabled=!state.busy&&!state.pending,modifier=Modifier.fillMaxWidth()) {Text("${plant.name} · ${if(plant.hasImage)"Completar información" else "Falta fotografía"}")}}
            TextButton(onBack){Text("Volver")}
        }else{
            if(state.step==1){
                Text("Identificar",style=MaterialTheme.typography.titleLarge)
                OutlinedTextField(p.name,{vm.change(p.copy(name=it))},label={Text("Nombre")},enabled=editable,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(p.presentation,{vm.change(p.copy(presentation=it))},label={Text("Presentación")},enabled=editable,modifier=Modifier.fillMaxWidth())
                OutlinedButton({chooseCategory=true},enabled=editable){Text(state.categories.find{it.id==p.categoryId}?.name?:"Elegir categoría")}
                if(!p.product&&p.name.trim().length>=3){
                    val term=p.name.trim().split(Regex("\\s+")).firstOrNull{it.length>=4}?:p.name.trim()
                    val matches=state.catalogProducts.filter{it.name.contains(p.name.trim(),ignoreCase=true)||it.name.contains(term,ignoreCase=true)}.take(4)
                    if(matches.isNotEmpty()){
                        Text("¿Ya existe? Selecciona la coincidencia",style=MaterialTheme.typography.titleMedium)
                        matches.forEach{candidate->
                            val thumbnail by produceState<android.graphics.Bitmap?>(null,candidate.imageId){value=candidate.imageId?.let{runCatching{vm.preview(it)}.getOrNull()}}
                            OutlinedButton({vm.change(p.copy(linkProductId=if(p.linkProductId==candidate.id)null else candidate.id))},
                                enabled=editable,modifier=Modifier.fillMaxWidth()){
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                    if(thumbnail!=null)Image(thumbnail!!.asImageBitmap(),"Foto de ${candidate.name}",Modifier.size(48.dp))
                                    else Text("🌿")
                                    Text("${candidate.name} · ${candidate.presentation}${if(p.linkProductId==candidate.id)" ✓" else ""}")
                                }
                            }
                        }
                        Text(if(p.linkProductId==null)"Si es una planta nueva, continúa sin elegir ninguna." else "Se completará la planta existente y se conservará su precio si dejas el campo vacío.")
                    }
                }
                var details by remember { mutableStateOf(false) }
                TextButton({details=!details}){Text("Más opciones")}
                if(details){
                    OutlinedTextField(p.description,{vm.change(p.copy(description=it))},label={Text("Cuidados o descripción")},enabled=editable,modifier=Modifier.fillMaxWidth())
                    if(!p.product)OutlinedTextField(p.code,{vm.change(p.copy(code=it))},label={Text("Código interno (opcional)")},enabled=editable,modifier=Modifier.fillMaxWidth())
                }
            }
            if(state.step==2){
                Text("Fotografía",style=MaterialTheme.typography.titleLarge)
                Text(if(p.photoPath!=null)"Fotografía seleccionada; se enviará al guardar." else if(p.hasImage)"La ficha ya tiene una fotografía. Puedes conservarla o reemplazarla." else "Añade una foto para reconocer esta planta.")
                val preview by produceState<android.graphics.Bitmap?>(null,p.photoPath){value=withContext(Dispatchers.IO){p.photoPath?.let{android.graphics.BitmapFactory.decodeFile(it)}}}
                preview?.let{Image(it.asImageBitmap(),"Foto seleccionada",Modifier.fillMaxWidth().height(220.dp))}
                photoError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                Button({if(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)takePhoto() else cameraPermission.launch(Manifest.permission.CAMERA)},enabled=editable){Text("Tomar foto")}
                OutlinedButton({photoTarget=vm.photoTarget();gallery.launch("image/*")},enabled=editable){Text("Elegir de galería")}
            }
            if(state.step==3){
                Text("Precio y confirmar",style=MaterialTheme.typography.titleLarge)
                Text("${p.name} · ${p.presentation}")
                OutlinedTextField(p.price,{vm.change(p.copy(price=it))},label={Text("Precio de venta (opcional)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),enabled=editable&&(!p.product||state.canPrice))
                Text("Esta acción no modifica existencias. Si falta confirmar el conteo inicial, la ficha quedará publicada y te lo indicaremos.")
                if(state.canPublish&&p.status !in listOf("DISABLED","LINKED","PREPARED"))Button({vm.save(publish=true)},enabled=editable&&state.canPrice){Text("Confirmar y publicar")}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                if(state.step>1)TextButton({vm.step(state.step-1)},enabled=!state.busy){Text("Atrás")}
                if(state.step<3)Button({vm.step(state.step+1)},enabled=editable){Text("Continuar")}
            }
            TextButton({vm.save(exit=true)},enabled=editable){Text("Guardar y salir")}
            if(state.error!=null&&!state.pending)TextButton({reload=true},enabled=!state.busy){Text("Consultar versión guardada")}
            TextButton({leaving=true},enabled=!state.busy){Text("Volver a la lista")}
        }
    }
    if(chooseCategory)AlertDialog(onDismissRequest={chooseCategory=false},title={Text("Categoría")},text={Column(Modifier.verticalScroll(rememberScrollState())){state.categories.forEach{c->TextButton({p?.let{vm.change(it.copy(categoryId=c.id))};chooseCategory=false}){Text(c.name)}}}},confirmButton={TextButton({chooseCategory=false}){Text("Cerrar")}})
    if(leaving)AlertDialog(onDismissRequest={leaving=false},title={Text("Volver a plantas")},text={Text("Puedes guardar antes de salir. Descartar elimina solo el avance local, nunca el inventario.")},confirmButton={TextButton({leaving=false;vm.save(exit=true)},enabled=editable){Text("Guardar y salir")}},dismissButton={TextButton({leaving=false;vm.close()},enabled=!state.busy&&!state.pending){Text("Descartar avance local")}})
    if(reload)AlertDialog(onDismissRequest={reload=false},title={Text("Cargar versión guardada")},text={Text("Reemplazará los cambios locales por la ficha del servidor.")},confirmButton={TextButton({reload=false;vm.reload()}){Text("Cargar")}},dismissButton={TextButton({reload=false}){Text("Cancelar")}})
}
