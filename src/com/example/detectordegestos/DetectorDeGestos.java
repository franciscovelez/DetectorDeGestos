package com.example.detectordegestos;
/*
 * Detector de gestos. Versi�n 1, que incluye la detecci�n por:
 *    - Diferencias de cuadrados
 *    - Media y desviaci�n t�pica por coordenadas
 *    - Media y desviaci�n t�pica global
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
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;


public class DetectorDeGestos extends Activity implements SensorEventListener {
	private SensorManager sm;          //Controlador del sensor
	private Sensor accel;	           //Estructura para el aceler�metro
	private int estado;                //Controla el estado del programa (registrando o detectando movimiento)
	private MediaPlayer sonido = null; //Reproductor de sonidos
	private int modoDeteccion  = 0;    //Modo en el que se comparan los gestos: 0->diff cuadrados; 1->med/desv global; 2-> med/desv coords
	
	//Estructuras de datos para reconocer gestos
	private ArrayList<ArrayList<Float>> datos = new ArrayList<ArrayList<Float>>();  //Almacena el registro del gesto (memorizaci�n del gesto)
	private Queue<ArrayList<Float>> buffer    = new ArrayDeque<ArrayList<Float>>(); //Cola que almacena los �ltimos movimientos hechos por el usuario (detecci�n)	
	
	//Variables para calcular medias y desviaciones
	private float mediaDatos_, mediaBuffer_, desvDatos_, desvBuffer_;
	private float mediaDatos  [] = new float[3];
	private float mediaBuffer [] = new float[3];
	private float desvDatos   [] = new float[3];
	private float desvBuffer  [] = new float[3];
	
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
        //this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
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
		//Calculamos media y desviaci�n del movimiento memorizado
		if(modoDeteccion == 1){
			reconocimientoDatosGlobal();	
		}else if(modoDeteccion == 2){
			reconocimientoDatosCoords();
		}		
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
	
	public void cambiarModo(View v){		
		Button bt = (Button) findViewById(R.id.modoButton);
		
		if(!((ToggleButton) findViewById(R.id.detectorButton)).isChecked()){
			modoDeteccion = (modoDeteccion+1)%3;
			if(modoDeteccion == 0){
				bt.setText(R.string.modo_diff_cuad);
			}else if(modoDeteccion == 1){
				reconocimientoDatosGlobal();
				bt.setText(R.string.modo_med_desv_g);
			}else if(modoDeteccion == 2){
				reconocimientoDatosCoords();
				bt.setText(R.string.modo_med_desv_c);
			}
		}else{
			Toast.makeText(this, "Por favor, detenga reconocimiento de gestos", Toast.LENGTH_SHORT).show();		
		}
	}
	
	
    /**************************************
     * M�todos varios                     *
     **************************************/
	//Memoriza los valores del aceler�metro
	private void memorizarMovimiento(SensorEvent event){
		//long current_time       = event.timestamp; //en nanosecs.
		//long time_difference    = event.timestamp - time;
		
		//Array para amacenar las tres coordenadas
    	ArrayList<Float> coords = new ArrayList<Float>(3);
        
    	//Redondeamos a 3 decimales
    	coords.add((float) (Math.round(event.values[0]*1000.0)/1000.0));
    	coords.add((float) (Math.round(event.values[1]*1000.0)/1000.0));
    	coords.add((float) (Math.round(event.values[2]*1000.0)/1000.0));
              
        //if(time_difference > 100000){
        	//time = current_time;
    	//Actualizamos valores en pantalla
    	((TextView) findViewById(R.id.xCoordText)).setText(getString(R.string.coord_x_) + coords.get(0));
        ((TextView) findViewById(R.id.yCoordText)).setText(getString(R.string.coord_y_) + coords.get(1));
        ((TextView) findViewById(R.id.zCoordText)).setText(getString(R.string.coord_z_) + coords.get(2));
        
        datos.add(coords);
        //}        
	}
	
	//Almacena un movimiento cuando se est� reconocimendo gestos 
	//y determina si el gesto realizado es el mismo que el memorizado
	private void detectarMovimiento(SensorEvent event){
		//long current_time       = event.timestamp; //en nanosecs.
		//long time_difference    = event.timestamp - time;
		//Array para amacenar las tres coordenadas
    	ArrayList<Float> coords       = new ArrayList<Float>(3);

    	//Redondeamos a 3 decimales
    	coords.add((float) (Math.round(event.values[0]*1000.0)/1000.0));
    	coords.add((float) (Math.round(event.values[1]*1000.0)/1000.0));
    	coords.add((float) (Math.round(event.values[2]*1000.0)/1000.0));
        
        //if(time_difference > 100000){
        //	time = current_time;
    	
    	//Llenamos el buffer y lo mantenemos al mismo tama�o que el gesto memorizado
		if(buffer.size()==datos.size()){
			buffer.poll();
			buffer.add(coords);
			
			//Reconocemos el gesto seg�n el m�todo que queramos
			if(modoDeteccion==0){
				reconocimientoDifCuadrados();
			}else if(modoDeteccion==1){
				reconocimientoMediaDesvGlobal();
			}else if(modoDeteccion==2){
				reconocimientoMediaDesvCoord();
			}
		}else
			buffer.add(coords);
       // }        
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
	
	//Reconoce un gesto calculando la media y desviaci�n de los �ltimos movimientos realizados
	private void reconocimientoMediaDesvGlobal(){
    	Iterator<ArrayList<Float>> coordsIt;
    	ArrayList<Float> coordsBuffer = new ArrayList<Float>(3);
    	float umbral1=(float) 1.5, umbral2=(float) 0.5;
    	
		mediaBuffer_ = 0;
		desvBuffer_  = 0;
		
		//Calculamos la media del buffer
		coordsIt = buffer.iterator();
		while(coordsIt.hasNext()){		
			coordsBuffer = coordsIt.next();
			mediaBuffer_ += Math.abs(coordsBuffer.get(0)) + Math.abs(coordsBuffer.get(1)) + Math.abs(coordsBuffer.get(2));
		}
		mediaBuffer_ /= buffer.size();		
		
		//Calculamos la desviaci�n t�pica del buffer
		coordsIt = buffer.iterator();
		while(coordsIt.hasNext()){		
			coordsBuffer = coordsIt.next();
			desvBuffer_ += (float) Math.pow((Math.abs(coordsBuffer.get(0))+Math.abs(coordsBuffer.get(1))+Math.abs(coordsBuffer.get(2))-mediaBuffer_), 2);
		}
		desvBuffer_ /= buffer.size();

		//Si no se supera el umbral, se acepta el gesto realizado. Reproducimos sonido y vaciamos el buffer
		if(Math.abs(mediaBuffer_ - mediaDatos_)<umbral1 &&  Math.abs(desvBuffer_ - desvDatos_)<umbral2){
			System.out.println("mDat, mBuf, dDat, dBuf: " + mediaDatos_ + ", " + desvDatos_ + ", " + mediaBuffer_ + ", " + desvBuffer_  + " <<-------");
			sonido.start();		
			buffer.clear();
		}else
			System.out.println("mDat, mBuf, dDat, dBuf: " + mediaDatos_ + ", " + desvDatos_ + ", " + mediaBuffer_ + ", " + desvBuffer_);
		
	}
	//Reconoce un gesto calculando la media y desviaci�n por coordenadas de los �ltimos movimientos realizados
	private void reconocimientoMediaDesvCoord(){
		Iterator<ArrayList<Float>> coordsIt;
    	ArrayList<Float> coordsBuffer = new ArrayList<Float>(3);
    	float umbral1=(float) 0.5, umbral2=(float) 0.5;
    	
		mediaBuffer[0]=0; mediaBuffer[1]=0; mediaBuffer[2]=0;
		desvBuffer[0] =0; desvBuffer[1] =0; desvBuffer[2] =0;
		
		//Calculamos la media del buffer
		coordsIt = buffer.iterator();
		while(coordsIt.hasNext()){		
			coordsBuffer = coordsIt.next();
			mediaBuffer[0] += Math.abs(coordsBuffer.get(0));
			mediaBuffer[1] += Math.abs(coordsBuffer.get(1));
			mediaBuffer[2] += Math.abs(coordsBuffer.get(2));
		}
		mediaBuffer[0] /= buffer.size();
		mediaBuffer[1] /= buffer.size();
		mediaBuffer[2] /= buffer.size();
		
		//Calculamos la desviaci�n t�pica del buffer
		coordsIt = buffer.iterator();
		while(coordsIt.hasNext()){		
			coordsBuffer = coordsIt.next();
			desvBuffer[0] += (float) (Math.pow((Math.abs(coordsBuffer.get(0))-mediaBuffer[0]), 2));
			desvBuffer[1] += (float) (Math.pow((Math.abs(coordsBuffer.get(1))-mediaBuffer[1]), 2));
			desvBuffer[2] += (float) (Math.pow((Math.abs(coordsBuffer.get(2))-mediaBuffer[2]), 2));
		}
		desvBuffer[0] /= buffer.size();
		desvBuffer[1] /= buffer.size();
		desvBuffer[2] /= buffer.size();

		//Si no se supera el umbral, se acepta el gesto realizado. Reproducimos sonido y vaciamos el buffer
		if( Math.abs(mediaBuffer[0]-mediaDatos[0])<umbral1 &&  Math.abs(desvBuffer[0]-desvDatos[0])<umbral2 &&
			Math.abs(mediaBuffer[1]-mediaDatos[1])<umbral1 &&  Math.abs(desvBuffer[1]-desvDatos[1])<umbral2 &&
			Math.abs(mediaBuffer[2]-mediaDatos[2])<umbral1 &&  Math.abs(desvBuffer[2]-desvDatos[2])<umbral2    ){
			System.out.println("mDat, mBuf, dDat, dBuf: " + mediaDatos + ", " + desvDatos + ", " + mediaBuffer + ", " + desvBuffer  + " <<-------");
			sonido.start();		
			buffer.clear();
		}//else
		//	System.out.println("mDat, mBuf, dDat, dBuf: " + mediaDatos + ", " + desvDatos + ", " + mediaBuffer + ", " + desvBuffer);
	}
	
	//Calcula la media y desviaci�n del gesto memorizado
private void reconocimientoDatosGlobal(){
	mediaDatos_ = 0;
	desvDatos_  = 0;
	
	//Calculamos la media del gesto memorizado
	for(ArrayList<Float> tmp : datos){
		mediaDatos_ += Math.abs(tmp.get(0)) + Math.abs(tmp.get(1)) + Math.abs(tmp.get(2));
	}
	mediaDatos_ /= datos.size();	
	
	//Calculamos la desviaci�n t�pica del gesto memorizado
	for(ArrayList<Float> tmp : datos){
		desvDatos_ += (Math.pow((Math.abs(tmp.get(0))+Math.abs(tmp.get(1))+Math.abs(tmp.get(2))-mediaDatos_), 2));
	}
	desvDatos_ /= datos.size();
}
//Calcula la media y desviaci�n por coordenadas del gesto memorizado
private void reconocimientoDatosCoords(){
	mediaDatos[0]=0; mediaDatos[1]=0; mediaDatos[2]=0;
	desvDatos[0] =0; desvDatos[1] =0; desvDatos[2] =0;
	
	//Calculamos la media del gesto memorizado
	for(ArrayList<Float> tmp : datos){
		mediaDatos[0] += Math.abs(tmp.get(0));
		mediaDatos[1] += Math.abs(tmp.get(1));
		mediaDatos[2] += Math.abs(tmp.get(2));
	}
	mediaDatos[0] /= datos.size();
	mediaDatos[1] /= datos.size();
	mediaDatos[2] /= datos.size();
	
	//Calculamos la desviaci�n t�pica del gesto memorizado
	for(ArrayList<Float> tmp : datos){			
		desvDatos[0] += (float) (Math.pow((Math.abs(tmp.get(0))-mediaDatos[0]), 2));
		desvDatos[1] += (float) (Math.pow((Math.abs(tmp.get(1))-mediaDatos[1]), 2));
		desvDatos[2] += (float) (Math.pow((Math.abs(tmp.get(2))-mediaDatos[2]), 2));
	}
	desvDatos[0] /= datos.size();
	desvDatos[1] /= datos.size();
	desvDatos[2] /= datos.size();
}
	
}
