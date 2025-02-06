package org.dd.camerapreview.draggableruler;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import org.dd.camerapreview.RulerValueCameraManager;

import java.util.List;

public class DraggableRulerView extends View {

    // Variabili di stato
    private Paint paint;
    private float rulerPosition = 0; // Posizione corrente della linea draggabile
    private float lastTouchX; // Ultima posizione X del tocco
    private float offsetX = 0; // Offset del righello

    private int selectedValueColor = Color.RED; // Colore di default del valore selezionato
    private List<String> customValues = null; // Lista di valori personalizzati

    private int minValue = -5000; // Valore minimo del righello
    private int maxValue = 5000;  // Valore massimo del righello
    private int step = 50;        // Incremento per i valori (lunghezza tra tacche lunghe)
    private int centerX;          // Posizione centrale dello schermo
    private int smallTickInterval = 5; // Imposta il numero di tacche corte tra i valori
    private float tickSpacing = 2.0f; // Imposta la distanza tra le tacche principali (valore di default)

    public void setSelectedValueColor(int color) {
        this.selectedValueColor = color;
        invalidate(); // Aggiorna la View
    }

    public void setCustomValues(List<String> values) {
        this.customValues = values;

        // Imposta automaticamente minValue e maxValue in base alla lista
        this.minValue = 0; // Indice del primo valore
        this.maxValue = (values.size() - 1) * step; // Indice dell'ultimo valore moltiplicato per lo step

        // Aggiorna l'offset per posizionare il valore iniziale al centro
        this.offsetX = minValue;

        invalidate(); // Ridisegna la View
    }

    public void setTickSpacing(float spacing) {
        this.tickSpacing = spacing;
        invalidate(); // Ridisegna la View
    }

    public void setSmallTickInterval(int interval) {
        this.smallTickInterval = interval;
        invalidate(); // Ridisegna la View
    }

    // Metodo pubblico per impostare un valore iniziale
    public void setInitialValue(String initialValue) {
        if (customValues != null && customValues.contains(initialValue)) {
            int index = customValues.indexOf(initialValue);
            // Calcola l'offset in base all'indice del valore selezionato
            offsetX = index * step;
            invalidate(); // Ridisegna la View per visualizzare il valore iniziale
        }
    }

    // Interfaccia per il listener
    public interface OnRulerPositionChangeListener {
        void onPositionChanged(String value);
    }

    private OnRulerPositionChangeListener listener;

    public void setOnRulerPositionChangeListener(OnRulerPositionChangeListener listener) {
        this.listener = listener;
    }

    // Costruttori
    public DraggableRulerView(Context context) {
        super(context);
        init();
    }

    public DraggableRulerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DraggableRulerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    // Inizializzazione
    private void init() {
        paint = new Paint();
        paint.setColor(Color.BLACK); // Colore delle linee e del testo
        paint.setStrokeWidth(2); // Spessore delle linee
        paint.setTextSize(20); // Dimensione del testo
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float centerY = getHeight() / 2;

        // Disegna la linea centrale del righello
        canvas.drawLine(0, centerY, getWidth(), centerY, paint);

        // Disegna tacche lunghe (a ogni 'step')
        for (int i = minValue; i <= maxValue; i += step) {
            float x = centerX + ((i - offsetX) * tickSpacing); // Posizione relativa delle tacche, moltiplicata per tickSpacing

            if (x >= 0 && x <= getWidth()) { // Disegna solo tacche visibili
                // Tacche lunghe
                if ((i - minValue) % step == 0) {
                    // Disegna tacca lunga
                    canvas.drawLine(x, centerY - 30, x, centerY + 30, paint);

                    // Colore del valore selezionato
                    if (Math.abs(i - offsetX) < step / 2) {
                        paint.setColor(selectedValueColor); // Colore del valore selezionato
                    } else {
                        paint.setColor(Color.BLACK);
                    }

                    // Disegna il valore (gestione delle stringhe)
                    String valueText = customValues != null && (i - minValue) / step < customValues.size()
                            ? customValues.get((i - minValue) / step) // Indice corretto per customValues
                            : String.valueOf(i / step);
                    canvas.drawText(valueText, x - paint.measureText(valueText) / 2, centerY + 60, paint);
                }
            }
        }

        // Disegna tacche corte tra i valori principali
        for (int i = minValue + step; i < maxValue; i += step / smallTickInterval) {
            float x = centerX + ((i - offsetX) * tickSpacing); // Posizione delle tacche corte

            if (x >= 0 && x <= getWidth()) {
                // Disegna solo tacche corte (senza valori associati)
                canvas.drawLine(x, centerY - 10, x, centerY + 10, paint);
            }
        }

        // Disegna la linea centrale come "maniglia"
        paint.setColor(selectedValueColor);
        canvas.drawLine(centerX, 0, centerX, getHeight(), paint);
        paint.setColor(Color.BLACK); // Ripristina il colore originale
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = x;
                break;

            case MotionEvent.ACTION_MOVE:
                float deltaX = lastTouchX - x;
                lastTouchX = x;

                // Calcola il nuovo offset, rispettando i limiti
                float newOffsetX = offsetX + deltaX;

                if (newOffsetX < minValue) {
                    offsetX = minValue;
                } else if (newOffsetX > maxValue) {
                    offsetX = maxValue;
                } else {
                    offsetX = newOffsetX;
                }

                invalidate(); // Ridisegna la View
                break;

            case MotionEvent.ACTION_UP:
                // Allinea alla tacca più vicina
                offsetX = Math.round(offsetX / step) * step;

                // Notifica il listener con il valore selezionato
                if (listener != null) {
                    int index = Math.round(offsetX / step);
                    String selectedValue = customValues != null && index < customValues.size()
                            ? customValues.get(index)
                            : String.valueOf(index);
                    listener.onPositionChanged(selectedValue);
                    RulerValueCameraManager.getInstance(getContext()).insertRulerValue(getId(), selectedValue);
                }

                invalidate();
                break;
        }

        return true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2; // Calcoliamo la posizione centrale della vista
    }

    // Metodo pubblico per ottenere il valore selezionato
    public String getSelectedValue() {
        int index = Math.round(offsetX / step);
        return customValues != null && index < customValues.size()
                ? customValues.get(index)
                : String.valueOf(index);
    }
}
