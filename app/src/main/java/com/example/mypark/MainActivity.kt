package com.example.mypark

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.pow
import kotlin.math.sqrt

//LA MAIN ACTIVITY IMPLEMENTA LE INTERFACCE LOCATIONLISTENER E SENSOREVENTLISTENER
class MainActivity : AppCompatActivity(), LocationListener, SensorEventListener {

    private val locationPermissionCode = 2 //è un codice usato in onRequestPermissionsResult che viene utilzzato
    // come identificatore univoco per richiedere i permessi di localizzazione

    private lateinit var locationManager: LocationManager //fornisce accesso ai serivizi di localizzazione di sistema
    //richiede Manifest.permission.ACCESS_COARSE_LOCATION o Manifest.permission.ACCESS_FINE_LOCATION

    private lateinit var sensorManager: SensorManager //fornisce accesso ai sensori del dispositivo
    private var accelerometer: Sensor? =null    //dichiara sensore accelerometro
    private var lastShake : Long = 0            //memorizza l'istante in cui è stato rilevato l'ultimo shake
    private val minimumWaitShake : Long = 1000  //definisce tempo minimo che deve intercorrere fra uno shake e l'altro (in millisecondi)

    //dichiara componenti del main layout
    private lateinit var mainMap: MapView  //oggetto MapView di Open Street Map
    private lateinit var locationTextView: TextView //mostra latitudine e longitudine
    private lateinit var parkButton: Button         //premi per parcheggiare
    private lateinit var favouritesButton: Button   //premi per aprire i preferiti

    //dichiara componenti per controllare la mappa
    private lateinit var controller: IMapController //controller della mappa (setZoom, setCenter, animateTo)
    private lateinit var center: GeoPoint           //centro della mappa (latitudine e longitudine)
    private lateinit var mark: Marker               //marker della mappa (indica la posizione sulla mappa)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //effettua il caricamento delle preferenze di Open Street Map salvate nelle shared preferences
        Configuration.getInstance().load(applicationContext, getSharedPreferences(getString(R.string.app_name), MODE_PRIVATE))

        locationTextView= findViewById(R.id.mainLocationText) //inizializza locationTextView
        setMap() //inizializza la mappa

        //GESTISCO I PULSANTI
        parkButton= findViewById(R.id.parkButton)
        parkButton.setOnClickListener { //quando il pulsante viene premuto, parcheggia e avvia la second activity
            startSecondActivity()
        }
        favouritesButton= findViewById(R.id.mainFavouritesButton)
        favouritesButton.setOnClickListener{ //quando viene premuto avvia favouites activity
            val intent = Intent(this, FavouritesActivity::class.java)
            startActivity(intent)
        }
    }

    //inizializza la mappa
    private fun setMap(){
        mainMap= findViewById(R.id.mainMap)
        mainMap.setTileSource(TileSourceFactory.MAPNIK) //imposta la sorgente delle immagini della mappa
        mainMap.setMultiTouchControls(true)             //abilita i controlli multitocco sulla mappa
        center= GeoPoint (39.36456735626405, 16.22573181088389) //inizializza centro della mappa
        controller= mainMap.controller //inizializza il controller
        controller.setZoom(18.0)       //imposta lo zoom iniziale
        controller.setCenter(center)   //imposta il centro della mappa
        controller.animateTo(center)   //spostati sul centro
        mark= Marker(mainMap)          //aggiungi un marker
        mark.position=center           //posiziona il marker al centro
        mark.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        mainMap.overlays.add(mark)     //sovrapponi il marker alla mappa
        mainMap.invalidate()           //aggiorna la mappa
    }

    override fun onStart(){
        super.onStart()
        setLocationManager()   //inizializza locationManager e ottiene la posizione
        //inizializza sensorManager
        sensorManager= getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer= sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) //accede al sensore accelerometro
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    //inizializza locationManager e ottiene la posizione
    private fun setLocationManager() {
        //inizializza locationManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        //controlla se ha i permessi di localizzazione
        val permissionState= ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if(permissionState != PackageManager.PERMISSION_GRANTED){ //SE NON HA I PERMESSI DI LOCALIZZAZIONE
            //RICHIEDE I PERMESSI PER ACCEDERE ALLA POSIZIONE
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationPermissionCode)
        }
        else{
            myRequestLocationUpdates() //SE HA I PERMESSI richiede aggiornamenti sulla posizione
        }
    }

    //metodo che si esegue quando consento (o nego) i permessi di localizzazione
    //visualizza un toast per comunicare l'esito della richiesta dei permessi
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode==locationPermissionCode){    //SE IL CODICE DI RICHIESTA CORRISPONDE A QUELLO PER RICHIEDERE LA POSIZIONE
            if(grantResults.isNotEmpty() && grantResults[0]==PackageManager.PERMISSION_GRANTED){ //SE IL VETTORE DEI PERMESSI CONCESSI NON E' VUOTO E CONTIENE UN PERMESSO CONCESSO
                Toast.makeText(this, "Location permission allowed", Toast.LENGTH_SHORT).show()
                myRequestLocationUpdates() //richiedi aggiornamenti sulla posizione
            }
            else{
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    //imposta aggiornamenti sulla posizione ogni 3 secondi o quando la posizione cambia di 3 metri
    private fun myRequestLocationUpdates(){
        lateinit var provider : String
        if(isGpsEnabled()) {   //se il gps è attivo
            provider = LocationManager.GPS_PROVIDER     //imposta provider=GPS_PROVIDER
            Log.d("MAIN_ACTIVITY", "Using GPS provider")
        } else {
            provider=LocationManager.NETWORK_PROVIDER   //altrimenti imposta provider=NETWORK_PROVIDER
            Log.d("MAIN_ACTIVITY", "Using NETWORK provider")
        }
        try{
            val location = locationManager.getLastKnownLocation(provider)
            if (location != null) {
                Log.d("MAIN_ACTIVITY", "LastKnownLocation Timestamp: "+location.time)
                showLocation(location)
            }
            //imposta aggiornamenti sulla posizione ogni 3 secondi o quando la posizione cambia di 1 metro
            locationManager.requestLocationUpdates(provider, 3000, 1f, this)
            Log.d("MAIN_ACTIVITY", "Location Updates Requested")

        }catch (e: SecurityException){
            Log.e("MAIN_ACTIVITY", e.stackTraceToString())
        }
    }

    //CONTROLLA SE IL GPS E' ATTIVO
    private fun isGpsEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    //QUANDO LA LOCALIZZAZIONE CAMBIA (override del metodo dell'interfaccia LocationListener)
    override fun onLocationChanged(location: Location) {
        showLocation(location) //mostra le coordinate sulla mappa e nella textview
        Log.d("MAIN_ACTIVITY", "LocationChanged: Lat:"+location.latitude+", Lon:"+location.longitude)
    }

    //mostra le coordinate sulla mappa e nella textview
    private fun showLocation(location: Location) {
        updateLocationTextView(location) //aggiorna la TextView di latitudine e longitudine
        //AGGIORNA LA MAPPA
        center = GeoPoint(location.latitude, location.longitude) //imposta il centro
        controller.animateTo(center) //visualizza il centro
        mark.position = center //imposta il marker
        mark.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        mainMap.invalidate() //aggiorna la mappa
    }

    //aggiorna la TextView di latitudine e longitudine
    private fun updateLocationTextView(location: Location) {
        //costruisce la stringa di testo
        locationTextView.text= buildString {
            append("Latitude: ")
            append(String.format("%.3f", location.latitude))
            append(" Longitude: ")
            append(String.format("%.3f", location.longitude))
        }
    }

    //avvia la second activity
    private fun startSecondActivity(){
        val intent= Intent(this, SecondActivity::class.java) //crea Intent
        intent.putExtra("latitude", center.latitude)
        intent.putExtra("longitude", center.longitude)
        startActivity(intent)   // avvia la second activity
    }

    //QUANDO RILEVA SHAKE CHIEDE SE VUOI PARCHEGGIARE
    //(override del metodo del SensorEventListener)
    override fun onSensorChanged(event: SensorEvent?) {
        //controlla se l'evento è stato generato dall'accelerometro
        if(event?.sensor?.type==Sensor.TYPE_ACCELEROMETER){
            //Controlla quanto tempo è passato dall'ultimo evento di questo tipo
            //(ignora eventi shake troppo vicini fra loro)
            val now=System.currentTimeMillis() //controlla il tempo attuale
            if (now < lastShake + minimumWaitShake) {
                return //se non è passato abbastanza tempo, esci senza fare nulla
            }
            lastShake=now //altrimenti aggiorna il tempo dell'ultimo shake
            //legge i valori dell'accelerazione misurati dal sensore (Array di Float)
            val acceleration=event.values
            val magnitude= sqrt(acceleration[0].pow(2) + acceleration[1].pow(2) + acceleration[2].pow(2))
            if(magnitude>18){ //ignoro i movimenti troppo deboli
                //Costruisce un Alert Dialog per chiedere all'utente se vuole parcheggiare
                val builder= AlertDialog.Builder(this)     // Crea l'oggetto AlertDialog Builder
                builder.setMessage("Do you want to park here?")   // Imposta il messaggio
                builder.setTitle("Shake detected")                // Imposta il titolo
                builder.setCancelable(true) //se l'utente preme al di fuori della dialog box il popup si cancella
                builder.setPositiveButton("Yes") { _, _ ->
                    startSecondActivity() //se Yes viene premuto, avvia la second activity
                }
                builder.setNegativeButton("No") { dialog, _ ->
                    dialog.cancel()       //altrimenti annulla
                }
                val alertDialog = builder.create() // crea la dialog box
                alertDialog.show() //mostra la dialog box
            }
        }
    }
    //deve essere implementata per l'interfaccia SensorEventListener, ma non viene utilizzata
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }

    //Disabilita gli aggiornamenti di posizione e sensori quando l'attività va in pausa
    override fun onStop() {
        super.onStop()
        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(this)
    }
    //Riprende gli aggiornamenti di posizione e sensori quando l'attività riprende
    override fun onRestart() {
        super.onRestart()
        setLocationManager()
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }
}