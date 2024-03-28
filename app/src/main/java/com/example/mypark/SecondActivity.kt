package com.example.mypark

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import org.osmdroid.api.IMapController
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker


class SecondActivity : AppCompatActivity() {

    //dichiara componenti del second layout
    private lateinit var secondMap: MapView
    private lateinit var locationTextView: TextView
    private lateinit var cancelButton: Button
    private lateinit var navigateButton: Button
    private lateinit var addToFavouritesButton: Button

    //dichiara componenti della mappa
    private lateinit var controller: IMapController //controller della mappa
    private lateinit var center: GeoPoint           //centro della mappa
    private lateinit var mark: Marker               //marker della mappa

    //dichiara variabili latitudine e longitudine in cui
    private var latitude= 0.0
    private var longitude= 0.0

    //dichiara favourites : sharedPreferences
    private lateinit var favourites: SharedPreferences //lista dei parcheggi preferiti

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)

        //effettua il caricamneto della lista di parcheggi preferiti dalle SharedPreferences
        favourites= getSharedPreferences("favourites", Context.MODE_PRIVATE)

        //legge i valori passati tramite l'intent
        latitude= intent.getDoubleExtra("latitude", 39.36456735626405)
        longitude= intent.getDoubleExtra("longitude", 16.22573181088389)

        setMap()  //imposta la mappa
        setLocationTextView(center) //imposta  la TextView di latitudine e longitudine

        //GESTISCO I PULSANTI
        cancelButton= findViewById(R.id.cancelButton)
        cancelButton.setOnClickListener{
            finish() //CHIUDE L'ACTIVITY
        }
        navigateButton=findViewById(R.id.navigateButton)
        navigateButton.setOnClickListener{
            navigate() //AVVIA IL NAVIGATORE (se google maps non è installata si avvia nel browser)
        }
        addToFavouritesButton= findViewById(R.id.addToFavouritesButton)
        addToFavouritesButton.setOnClickListener{
            addFavourite() //AGGIUNGE IL PARCHEGGIO ALLA LISTA DEI PREFERITI
        }
    }

    //inizializza la mappa
    private fun setMap(){
        secondMap= findViewById(R.id.secondMap)
        secondMap.setTileSource(TileSourceFactory.MAPNIK) //imposta la sorgente delle immagini della mappa
        secondMap.setMultiTouchControls(true)             //abilita i controlli multitocco sulla mappa
        center= GeoPoint (latitude, longitude)            //inizializza centro della mappa
        controller= secondMap.controller                  //inizializza il controller
        controller.setZoom(16.0)                          //imposta lo zoom iniziale
        controller.setCenter(center)                      //imposta il centro della mappa
        controller.animateTo(center)                      //spostati sul centro
        mark= Marker(secondMap)                           //aggiungi un marker
        mark.position= center                             //posiziona il marker al centro
        mark.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        secondMap.overlays.add(mark)                      //sovrapponi il marker alla mappa
        secondMap.invalidate()                            //aggiorna la mappa
    }

    //aggiorna la TextView di latitudine e longitudine
    private fun setLocationTextView(location: GeoPoint) {
        locationTextView= findViewById(R.id.secondLocationText)  //inizializza locationTextView
        locationTextView.text= buildString {
            append("Latitude: ")
            append(String.format("%.5f", location.latitude))
            append(" Longitude: ")
            append(String.format("%.5f", location.longitude))
        }
    }

    //AVVIA IL NAVIGATORE (se google maps non è installata si avvia nel browser)
    private fun navigate(){
        val builder = Uri.Builder() //costruisce un indirizzo web
        builder.scheme("https")
            .authority("www.google.com")
            .appendPath("maps")
            .appendPath("dir")
            .appendPath("")
            .appendQueryParameter("api", "1")
            .appendQueryParameter("destination", "$latitude,$longitude")
        val url = builder.build().toString()
        Log.d("Directions", url)
        val i = Intent(Intent.ACTION_VIEW) //usa un intent per visualizzare l'indirizzo web
        i.setData(Uri.parse(url))
        startActivity(i) //se è disponibile avvia il navigatore di google maps, altrimenti lo apre nel browser web
    }

    //AGGIUNGE IL PARCHEGGIO ALLA LISTA DEI PREFERITI
    private fun addFavourite(){
        //USO UN ALERT DIALOG PER RICHIEDERE ALL'UTENTE DI INSERIRE UN NOME PER IL PARCHEGGIO
        //all'interno dell'AlertDialog aggiungerò un campo di testo in cui scrivere
        val textEdit = EditText(this)
        val textInput= TextInputLayout(this) //input di testo in cui scrivere
        textInput.hint="Park Name"  //suggerimento per il nome
        textInput.addView(textEdit) //aggiungo l'input di testo a EditText
        //costruisco l'AlertDialog
        val builder = AlertDialog.Builder(this) // Crea l'oggetto AlertDialog Builder
        builder.setTitle("Park Name") // Imposta il titolo
        builder.setView(textInput)    //AGGIUNGO IL TEXTINPUT ALL'ALERT
        builder.setMessage("Please enter a name for this parking spot") // Imposta il messaggio
        builder.setCancelable(false)
        builder.setPositiveButton("Submit") { dialog, _ ->
            val editor= favourites.edit() //inizializza editor delle sharedPreferences
            val name= textEdit.text.toString() //acquisisce il testo inserito dall'utente
            var validName=true //dovrà controllare se il nome inserito è valido
            val nameSet= favourites.getStringSet("nameSet", null)
            //NOTA: non devo modificare la lista estrapolata dalle shared preferences
            // quindi creo una nuova lista, che sarà un clone di quella appena ottenuta
            var newNameSet= mutableSetOf<String>()
            if(nameSet != null) {
                newNameSet = nameSet.toMutableSet() //clona la lista di nomi
                //CONTROLLA LA VALIDITà DEL NOME
                if (nameSet.contains(name)) { //se la lista contiene già il nome del parcheggio
                    Toast.makeText(this, "Invalid park name: already used", Toast.LENGTH_SHORT).show()
                    validName = false
                }
            }
            if(validName){ //se l'utente ha inserito un nome valido
                newNameSet.add(name)
                editor.putStringSet("nameSet", newNameSet)
                //Ogni parcheggio è memorizzato con due coppie: "nameLat"-Latitudine, "nameLon"-Longitudine
                editor.putFloat(name+"Lat", latitude.toFloat())
                editor.putFloat(name+"Lon", longitude.toFloat())
                editor.apply() //applica le modifiche al file
                Toast.makeText(this, "Saved $name", Toast.LENGTH_SHORT).show()
            }
            dialog.cancel() //chiude alert dialog
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }
        val alert= builder.create()
        alert.show() //visualizzo alertDialog
    }
}