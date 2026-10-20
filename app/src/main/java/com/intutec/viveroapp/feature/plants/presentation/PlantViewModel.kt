package com.intutec.viveroapp.feature.plants.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intutec.viveroapp.core.session.SessionStore
import com.intutec.viveroapp.core.session.authorizedSession
import com.intutec.viveroapp.feature.plants.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class PlantUiState(val plants:List<PlantWork> = emptyList(),val categories:List<PlantCategory> = emptyList(),val catalogProducts:List<PlantWork> = emptyList(),val selected:PlantWork?=null,val step:Int=1,val busy:Boolean=false,val pending:Boolean=false,val error:String?=null,val message:String?=null,val enabled:Boolean=false,val canPublish:Boolean=false,val canPrice:Boolean=false,val canDirect:Boolean=false,val history:List<String>?=null)
@HiltViewModel
class PlantViewModel @Inject constructor(private val gateway:PlantGateway,private val sessions:SessionStore,@param:ApplicationContext private val context:Context):ViewModel(){
    private val mutable=MutableStateFlow(PlantUiState())
    val state=mutable.asStateFlow()
    private var generation=0
    private var operation:Job?=null
    private var remembering:Job?=null
    private val photoSession=UUID.randomUUID().toString()
    fun photoTarget():String = "$photoSession:$generation:${state.value.selected?.product}:${state.value.selected?.id}"
    suspend fun preview(imageId:Long):Bitmap?=withContext(Dispatchers.IO){
        gateway.preview(imageId)?.let{BitmapFactory.decodeByteArray(it,0,it.size)}
    }
    suspend fun preview(plant:PlantWork):Bitmap?=withContext(Dispatchers.IO){
        plant.photoPath?.let { BitmapFactory.decodeFile(it) }
            ?:gateway.preview(plant)?.let { BitmapFactory.decodeByteArray(it,0,it.size) }
    }
    init {viewModelScope.launch {sessions.backend.collectLatest { s->
        generation++
        operation?.cancel();remembering?.cancel()
        val enabled=s.authorizedSession("MANAGE_PRODUCTS",true)!=null
        mutable.value=PlantUiState(enabled=enabled,canPublish=s.context?.canOperate("PUBLISH_CATALOGING")==true,canPrice=s.context?.canOperate("MANAGE_PRICES")==true)
        if(enabled){mutable.update{it.copy(busy=true)};try{load()}catch(e:CancellationException){throw e}catch(e:Exception){mutable.update{it.copy(error=e.message?:"No se pudo cargar Plantas.")}}finally{mutable.update{it.copy(busy=false)}}}
    }}}
    private suspend fun load(){
        val list=gateway.list();val remembered=gateway.remembered();val pending=gateway.hasPending()
        mutable.update{it.copy(plants=list.plants,categories=list.categories,catalogProducts=list.catalogProducts,canDirect=list.canDirect,selected=remembered,pending=pending)}
    }
    private fun run(action:suspend()->Unit){if(state.value.busy||!state.value.enabled)return
        val epoch=generation;mutable.update{it.copy(busy=true,error=null,message=null)}
        operation=viewModelScope.launch {try{remembering?.join();action()}catch(e:CancellationException){throw e}catch(e:Exception){if(epoch==generation){val saved=runCatching{gateway.remembered()}.getOrNull();mutable.update{it.copy(error=e.message?:"No se pudo guardar.",selected=saved?:it.selected)}}}finally{if(epoch==generation){val pending=runCatching{gateway.hasPending()}.getOrDefault(true);mutable.update{it.copy(busy=false,pending=pending)}}}}
    }
    fun retry()=run{load()}
    fun select(plant:PlantWork)=run{val p=gateway.open(plant);gateway.remember(p);mutable.update{it.copy(selected=p,step=1)}}
    fun create()=run{check(!gateway.hasPending());val p=PlantWork();gateway.remember(p);mutable.update{it.copy(selected=p,step=1)}}
    fun change(p:PlantWork){if(state.value.busy||state.value.pending)return;mutable.update{it.copy(selected=p,message=null)};remembering?.cancel();remembering=viewModelScope.launch(start=CoroutineStart.UNDISPATCHED){try{gateway.remember(p)}catch(e:CancellationException){throw e}catch(_:Exception){mutable.update{it.copy(error="No se pudo conservar el avance en este dispositivo.")}}}}
    fun step(value:Int){if(!state.value.busy)mutable.update{it.copy(step=value)}}
    fun save(publish:Boolean=false,exit:Boolean=false)=run{
        val p=state.value.selected?:return@run
        val saved=gateway.save(p,publish)
        mutable.update{it.copy(selected=saved,message=if(publish&&saved.readyForSale)"Ficha publicada y lista para venta."
            else if(publish&&saved.needsInitialCount)"Ficha publicada. Falta confirmar el conteo inicial${if(saved.price.isBlank())" y el precio" else ""}."
            else if(publish)"Ficha publicada. Revisa el precio o la preparación comercial para venderla." else "Cambios guardados.")}
        if(exit||publish||saved.status in listOf("PREPARED","LINKED")){gateway.remember(null);val list=gateway.list();mutable.update{it.copy(selected=null,plants=list.plants,categories=list.categories,catalogProducts=list.catalogProducts,canDirect=list.canDirect,step=1)}}
    }
    fun history(productId:Long?=null)=run{val rows=gateway.history(productId);mutable.update{it.copy(history=rows)}}
    fun closeHistory(){mutable.update{it.copy(history=null)}}
    fun count(quantity:String,reason:String)=run{
        check(state.value.canDirect);val plant=state.value.selected?:return@run
        val saved=gateway.count(plant,quantity,reason);mutable.update{it.copy(selected=saved,message="Ajuste aplicado y registrado en el historial.")}
        val current=gateway.open(saved);gateway.remember(current);mutable.update{it.copy(selected=current)}
    }
    fun recover()=run {val result=gateway.recover();mutable.update{it.copy(selected=result?:it.selected,message="Operación comprobada. Consulta la ficha antes de continuar.")}}
    fun reload()=run{val p=state.value.selected?:return@run;val current=gateway.open(p);gateway.remember(current);mutable.update{it.copy(selected=current,step=1)}}
    fun close()=run{check(!gateway.hasPending());gateway.remember(null);mutable.update{it.copy(selected=null,step=1)}}
    fun photo(uri:Uri,target:String)=run{
        check(target==photoTarget()) { "La ficha o la sesión cambió. Selecciona de nuevo la fotografía." }
        val p=state.value.selected?:return@run
        val file=withContext(Dispatchers.IO){
            val bytes=context.contentResolver.openInputStream(uri)!!.use{input->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var count=input.read(buffer)
                while(count!=-1){out.write(buffer,0,count);require(out.size()<=20*1024*1024){"Elige una foto más pequeña."};count=input.read(buffer)};out.toByteArray()
            }
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            require(bounds.outWidth>0&&bounds.outHeight>0){"No se pudo leer la foto."}
            val options=BitmapFactory.Options().apply{inSampleSize=1;while(bounds.outWidth/inSampleSize>2048||bounds.outHeight/inSampleSize>2048)inSampleSize*=2}
            val decoded=requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)){"No se pudo leer la foto."}
            val orientation=runCatching{android.media.ExifInterface(bytes.inputStream()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)}.getOrDefault(1)
            val matrix=android.graphics.Matrix().apply{when(orientation){2->setScale(-1f,1f);3->setRotate(180f);4->{setRotate(180f);postScale(-1f,1f)};5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f);7->{setRotate(-90f);postScale(-1f,1f)};8->setRotate(-90f)}}
            val bitmap=Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true)
            if(bitmap!==decoded)decoded.recycle()
            val directory=File(context.filesDir,"product_images").apply{mkdirs()};val target=File(directory,"${UUID.randomUUID()}.jpg")
            try{target.outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.JPEG,85,it))}}finally{bitmap.recycle()}
            require(target.length()<=5*1024*1024){"Elige una foto más pequeña."};target
        }
        val updated=p.copy(photoPath=file.absolutePath);gateway.remember(updated);mutable.update{it.copy(selected=updated)}
    }
}
