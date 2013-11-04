package com.example.detectordegestos;
/*
 * Detector de gestos. Versi�n 1, que incluye la detecci�n por:
 *    - Diferencias de cuadrados
 */

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Queue;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;


public class DetectorDeGestos extends Activity implements SensorEventListener {
	private SensorManager sm;          //Controlador del sensor
	private Sensor accel;	           //Estructura para el aceler�metro
	private int estado;                //Controla el estado del programa (registrando o detectando movimiento)
	private MediaPlayer sonido = null; //Reproductor de sonidos
	
	//Estructuras de datos para reconocer gestos
	private ArrayList<ArrayList<Float>> datos = new ArrayList<ArrayList<Float>>();  //Almacena el registro del gesto (memorizaci�n del gesto)
	private Queue<ArrayList<Float>> buffer    = new ArrayDeque<ArrayList<Float>>(); //Cola que almacena los �ltimos movimientos hechos por el usuario (detecci�n)	
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.detector_de_gestos, menu);
        return true;
    }
    /**************************************
     * Eventos de estado de la aplicaci�n *
     **************************************/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detector_de_gestos);
        //Inicializamos el sensor
        sm     = (SensorManager) getSystemService(SENSOR_SERVICE);
        accel  = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        //Cargamos el sonido que se reproducir�
        sonido = MediaPlayer.create(this, R.raw.touch);
    }
       
    protected void onPause(){
    	sm.unregisterListener(this);
    	super.onPause();    	
    }
    protected void onStop()  { super.onStop();   }
    protected void onResume(){ super.onResume(); }

    /**************************************
     * Eventos propios del sensor         *
     **************************************/
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) { }

	//Cuando se detecten nuevos datos en el sensor
	@Override
	public void onSensorChanged(SensorEvent event) { 
		synchronized (this) {
			//Si se ha pulsado memorizar movimiento...
			if(estado==0){
				memorizarMovimiento(event);
			//Si se ha pulsado detectar movimiento...
			}else{	
				detectarMovimiento(event);
			}
		}
	}	
	
    /**************************************
     * Acciones de los botones            *
     **************************************/
	//Comienza la memorizaci�n de un movimiento (bot�n start)
	public void startMemorizarMovimiento(View v){
		//Cambiamos estado y comenzamos a escuchar el aceler�metro
		estado = 0;
		datos.clear();
		sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
	}
	
	//Termina la memorizaci�n de un movimiento (bot�n stop)
	public void stopMemorizarMovimiento(View v){
		//Cerramos la escucha del aceler�metro
		sm.unregisterListener(this);
	}
	
	//Activa o desactiva el reconocimiento de movimientos (toggleButton iniciar/detener detector) 
	public void reconocerMovimiento(View v){		
		//Iniciar reconocimiento
		if(((CompoundButton) v).isChecked() && datos.size()>0){		
			//Cambiamos estado y comenzamos a escuchar el aceler�metro
			estado = 1;	
			buffer.clear();
			sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);	
		}else{ //detener reconocimiento
			//Cambiamos estado y cerramos la escucha del aceler�metro
			estado = 0;
			sm.unregisterListener(this);
		}		
	}	
	
	//Muestra datos de debus por consola (bot�n mostrar info por log)
	public void debugConsola(View v){
		int i=0;
		
		Toast.makeText(this, "mostrando datos en logcat", Toast.LENGTH_SHORT).show();		
		for (ArrayList<Float> tmp : datos) {
			System.out.println("iter. "+i+" X:"+tmp.get(0)+" Y:"+tmp.get(1)+" Z:"+tmp.get(2) );
			i++;
		}				
	}
		
	
    /**************************************
     * M�todos varios                     *
     **************************************/
	//Memoriza los valores del aceler�metro
	private void memorizarMovimiento(SensorEvent event){	
		//Array para amacenar las tres coordenadas
    	ArrayList<Float> coords = new ArrayList<Float>(3);
        
    	//Redondeamos a 3 decimales
    	coords.add((float) (Math.round(event.values[0]*1000.0)/1000.0));
    	coords.add((float) (Math.round(event.values[1]*1000.0)/1000.0));
    	coords.add((float) (Math.round(event.values[2]*1000.0)/1000.0));

    	//Actualizamos valores en pantalla
    	((TextView) findViewById(R.id.xCoordText)).setText(getString(R.string.coord_x_) + coords.get(0));
        ((TextView) findViewById(R.id.yCoordText)).setText(getString(R.string.coord_y_) + coords.get(1));
        ((TextView) findViewById(R.id.zCoordText)).setText(getString(R.string.coord_z_) + coords.get(2));
        
        datos.add(coords);     
	}
	
	//Almacena un movimiento cuando se est� reconocimendo gestos 
	//y determina si el gesto realizado es el mismo que el memorizado
	private void detectarMovimiento(SensorEvent event){
		//Array para amacenar las tres coordenadas
    	ArrayList<Float> coords       = new ArrayList<Float>(3);

    	//Redondeamos a 3 decimales
    	coords.add((float) (Math.round(event.values[0]*1000.0)/1000.0));
    	coords.add((float) (Math.round(event.values[1]*1000.0)/1000.0));
    	coords.add((float) (Math.round(event.values[2]*1000.0)/1000.0));
    	
    	//Llenamos el buffer y lo mantenemos al mismo tama�o que el gesto memorizado
		if(buffer.size()==datos.size()){
			buffer.poll();
			buffer.add(coords);	
			//Reconocemos el gesto seg�n el m�todo que queramos
			reconocimientoDifCuadrados();	
		}else
			buffer.add(coords);  
	}
	
    /**************************************
     * M�todos para reconocer gestos      *
     **************************************/
	//Reconoce un gesto haciendo diferencia de cuadrados por coordenadas
	private void reconocimientoDifCuadrados(){
		float sumaX, sumaY, sumaZ;        
    	float umbral1=500, umbral2=1000;
    	int i;
    	Iterator<ArrayList<Float>> coordsIt;
    	ArrayList<Float> coordsBuffer = new ArrayList<Float>(3);
    	    	
		coordsIt = buffer.iterator();
		i = 0; 
		sumaX = sumaY = sumaZ = 0;
		//Recorremos el buffer y calculamos las diferencias por coordenadas
		while(coordsIt.hasNext()){
			coordsBuffer = coordsIt.next();
			sumaX += Math.pow((datos.get(i).get(0)-coordsBuffer.get(0)), 2);
			sumaY += Math.pow((datos.get(i).get(1)-coordsBuffer.get(1)), 2);
			sumaZ += Math.pow((datos.get(i).get(2)-coordsBuffer.get(2)), 2);				
			i++;			
		}		
		
		//Si no se supera el umbral, se acepta el gesto realizado. Reproducimos sonido y vaciamos el buffer
		if(sumaX<umbral1 && sumaY<umbral1 && sumaZ<umbral1 &&(sumaX+sumaY+sumaZ)<umbral2){
			System.out.println("Diferencia [X, Y, Z]: [" + sumaX + ", "+ sumaY + ", "+ sumaZ + "]");
			sonido.start();
			buffer.clear();
		}
		//Actualizamos en pantalla los valores de las sumas
    	((TextView) findViewById(R.id.xDiffText)).setText(getString(R.string.diff_x_) + sumaX);
        ((TextView) findViewById(R.id.yDiffText)).setText(getString(R.string.diff_y_) + sumaY);
        ((TextView) findViewById(R.id.zDiffText)).setText(getString(R.string.diff_z_) + sumaZ);
        ((TextView) findViewById(R.id.sumDiffText)).setText(getString(R.string.suma_diff_) + (sumaX+sumaY+sumaZ));
	}	
}
