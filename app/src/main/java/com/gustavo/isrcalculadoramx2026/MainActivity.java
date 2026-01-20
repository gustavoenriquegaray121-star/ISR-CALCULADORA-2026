package com.gustavo.isrcalculadoramx2026;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.text.DecimalFormat;

public class MainActivity extends AppCompatActivity {

    private EditText etSalario, etDias;
    private TextView tvResultado;
    private DecimalFormat df = new DecimalFormat("#,##0.00");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Vincular elementos de la interfaz
        etSalario = findViewById(R.id.et_salario);
        etDias = findViewById(R.id.et_dias);
        tvResultado = findViewById(R.id.tv_resultado);
        Button btnCalcular = findViewById(R.id.btn_calcular);

        // Acción del botón calcular
        btnCalcular.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calcularISR();
            }
        });
    }

    private void calcularISR() {
        // Validar que los campos no estén vacíos
        if (etSalario.getText().toString().isEmpty() || etDias.getText().toString().isEmpty()) {
            tvResultado.setText("Por favor ingresa todos los datos");
            return;
        }

        try {
            // Obtener valores ingresados
            double salarioMensual = Double.parseDouble(etSalario.getText().toString());
            int diasTrabajados = Integer.parseInt(etDias.getText().toString());

            // Calcular salario diario y percibido
            double salarioDiario = salarioMensual / 30;
            double salarioPercibido = salarioDiario * diasTrabajados;

            // Calcular ISR según tablas 2026 (simplificado para ejemplo)
            double isr = 0.0;
            if (salarioPercibido <= 874.04) {
                isr = 0.0;
            } else if (salarioPercibido <= 3500.54) {
                isr = (salarioPercibido - 874.04) * 0.1925;
            } else if (salarioPercibido <= 6234.40) {
                isr = 502.97 + (salarioPercibido - 3500.54) * 0.20;
            } else if (salarioPercibido <= 10019.99) {
                isr = 1050.14 + (salarioPercibido - 6234.40) * 0.22;
            } else if (salarioPercibido <= 12958.55) {
                isr = 1902.06 + (salarioPercibido - 10019.99) * 0.23;
            } else {
                isr = 2565.41 + (salarioPercibido - 12958.55) * 0.35;
            }

            // Mostrar resultados
            String resultado = "Salario percibido: $" + df.format(salarioPercibido) + "\n"
                    + "ISR a pagar: $" + df.format(isr) + "\n"
                    + "Salario neto: $" + df.format(salarioPercibido - isr);

            tvResultado.setText(resultado);

        } catch (NumberFormatException e) {
            tvResultado.setText("Por favor ingresa valores válidos");
        }
    }
}
