package com.example.mypark

import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

class FavouritesActivity : AppCompatActivity() {

    //dichiara sharedPreferences
    private lateinit var favourites : SharedPreferences

    //dichiara componenti del layout
    private lateinit var layout: LinearLayout
    private lateinit var backButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favourites)

        favourites = getSharedPreferences("favourites", MODE_PRIVATE) //carica le shared preferences
        //inizializza le componenti del layout
        layout= findViewById(R.id.favouritesLinearLayout) //in questo layout visualizzerò la lista delle posizioni salvate
        backButton= findViewById(R.id.favouritesBackButton)

        buildParkList() //COSTRUISCE LA LISTA DEI PARCHEGGI SALVATI

        backButton.setOnClickListener{
            finish() //chiude l'activity corrente
        }
    }

    //COSTRUISCE LA LISTA DEI PARCHEGGI SALVATI
    private fun buildParkList(){
        //ottengo la lista dei nomi dei parcheggi salvata nelle shared prefrences
        val nameSet= favourites.getStringSet("nameSet", null)
        if(nameSet==null || nameSet.size==0){
            val tv= TextView(this)
            tv.text= buildString { append("The favourites list is empty") }
            layout.addView(tv)
            return
        }
        for(name in nameSet){ //name=nome del parcheggio
            //per ogni parcheggio (name) cerco i valori di latitudine e lognitudine
            val lat= favourites.getFloat(name+"Lat", 39.364567F) //latitudine
            val lon= favourites.getFloat(name+"Lon", 16.22573F) //longitudine
            //COSTRUISCO UN BOTTONE
            val button= Button(this)
            button.text=name //il testo del bottone è il nome del parcheggio
            button.setOnClickListener{
                //premendo il bottone avvio la second activity per visualizzare il parcheggio corrispondente
                startSecondActivity(lat.toDouble(), lon.toDouble())
                finish() //chiudo l'activity corrente
            }
            button.isLongClickable = true
            button.setOnLongClickListener{ //con una pressione prolungata cancello il parcheggio
                removePark(name) //chiede all'utente conferma prima di cancellare il parcheggio
                false
            }
            layout.addView(button) //aggiunge il bottone al layout
        }
    }

    //chiede all'utente conferma prima di cancellare il parcheggio
    private fun removePark(name:String) {
        val builder= AlertDialog.Builder(this) // Crea l'oggetto AlertDialog Builder
        builder.setTitle("Delete park?")              // Imposta il titolo
        builder.setMessage("Do you want to delete $name?") // Imposta il messaggio
        builder.setCancelable(true) //se l'utente preme al di fuori della dialog box il popup si cancella
        builder.setPositiveButton("Yes") { _, _ ->
            val editor= favourites.edit() //inizializza editor delle sharedPreferences
            val nameSet= favourites.getStringSet("nameSet", null)
            var newNameSet= mutableSetOf<String>()
            if(nameSet!=null){
                newNameSet=nameSet.toMutableSet()
                newNameSet.remove(name)
            } else {
                Log.e("FAVOURITES_ACTIVITY", "nameSet not found when removing park: $name")
            }
            editor.putStringSet("nameSet", newNameSet)
            editor.remove(name+"Lat") //rimuove latitudine
            editor.remove(name+"Lon") //rimuove longitudine
            editor.apply()      //applica le modifiche al file
            Toast.makeText(this, "DELETED $name", Toast.LENGTH_SHORT).show() //conferma all'utente la cancellazione
            startActivity(Intent(this, FavouritesActivity::class.java)) //riavvia l'activity per visualizzare le modifiche
            finish()
        }
        builder.setNegativeButton("No") { dialog, _ ->
            dialog.cancel()
        }
        val alertDialog = builder.create() // crea la dialog box
        alertDialog.show() //mostra la dialog box
    }

    private fun startSecondActivity(latitude:Double, longitude:Double){
        val intent= Intent(this, SecondActivity::class.java) //crea Intent
        intent.putExtra("latitude", latitude)
        intent.putExtra("longitude", longitude)
        startActivity(intent)   // avvio la second activity
    }
}