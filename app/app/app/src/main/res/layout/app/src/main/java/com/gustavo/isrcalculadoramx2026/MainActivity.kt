package com.gustavo.isrcalculadoramx2026

import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.gustavo.isrcalculadoramx2026.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.round

data class TramoISR(
    val limInf: Double,
    val limSup: Double,
    val cuota: Double,
    val tasa: Double
)

data class ISRDetalle(
    val isr: Double,
    val limiteInf: Double,
    val excedente: Double,
    val tasa: Double,
    val marginal: Double,
    val cuotaFija: Double
)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isPremium = false
    private var isSuperPremium = false

    private var ultimoISR = 0.0
    private var ultimoIMSS = 0.0
    private var ultimoNeto = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        MobileAds.initialize(this) {}
        binding.adView.loadAd(AdRequest.Builder().build())

        binding.tvEmote.alpha = 0f
        binding.tvResultado.text = ""

        binding.btnCalcular.setOnClickListener {
            calcularISR()
        }

        binding.btnUpgradePremium.setOnClickListener {
            isPremium = true
            binding.adView.visibility = View.GONE
            Toast.makeText(this, "Premium desbloqueado 💎", Toast.LENGTH_SHORT).show()
            binding.chartGrafica.visibility = View.VISIBLE
            if (ultimoNeto > 0) generarPDFGenérico(ultimoISR, ultimoIMSS, ultimoNeto)
        }

        binding.btnUpgradeSuperPremium.setOnClickListener {
            isSuperPremium = true
            isPremium = true
            binding.adView.visibility = View.GONE
            Toast.makeText(this, "Súper Premium desbloqueado 👑", Toast.LENGTH_SHORT).show()
            binding.chartGrafica.visibility = View.VISIBLE
            if (ultimoNeto > 0) generarPDFProfesional(ultimoISR, ultimoIMSS, ultimoNeto)
        }
    }

    private fun calcularISR() {
        val bruto = binding.etSueldoBruto.text.toString().toDoubleOrNull() ?: 0.0
        val deduccionesManual = binding.etDeducciones.text.toString().toDoubleOrNull() ?: 0.0

        if (bruto <= 0) {
            Toast.makeText(this, "Ingresa un monto válido", Toast.LENGTH_SHORT).show()
            return
        }

        val imss = if (binding.switchIMSS.isChecked) bruto * 0.02375 else 0.0
        val gravable = bruto - deduccionesManual - imss

        if (gravable <= 0) {
            Toast.makeText(this, "El ingreso gravable es 0", Toast.LENGTH_SHORT).show()
            return
        }

        val detalle = calcularISRDetalle(gravable)
        val neto = bruto - detalle.isr - imss - deduccionesManual

        val netoRedondeado = neto.round2()

        ultimoISR = detalle.isr
        ultimoIMSS = imss
        ultimoNeto = netoRedondeado

        val emote = when {
            netoRedondeado >= bruto * 0.85 -> "🤑 ¡Estás en la cima, jefe!"
            netoRedondeado >= bruto * 0.70 -> "😎 ¡Sigue rico!"
            netoRedondeado >= bruto * 0.55 -> "🙂 No está mal, eh"
            netoRedondeado >= bruto * 0.40 -> "😬 Uy… aprieta el cinturón"
            else -> "😭 El SAT mordió fuerte esta vez"
        }

        binding.tvResultado.text = """
            SALARIO NETO REAL: ${netoRedondeado.formatMoney()}
            
            - Deducciones manuales: ${deduccionesManual.formatMoney()}
            - Cuota IMSS auto (estimada 2.375%): ${imss.formatMoney()} 
              (puede variar según SBC y prestaciones)
            
            ISR a pagar: ${detalle.isr.formatMoney()}
            
            Tarifas actualizadas 2026 – Fuente: SAT
            Cálculo estimado, no sustituye declaración oficial.
        """.trimIndent()

        binding.tvEmote.text = emote
        binding.tvEmote.animate().alpha(1f).setDuration(600).start()

        if (isPremium) {
            dibujarGrafica(detalle.isr, imss, netoRedondeado)
        }

        if (isSuperPremium) {
            guardarHistorial(bruto, detalle.isr, imss, deduccionesManual, netoRedondeado)
        }
    }

    private fun Double.round2(): Double = round(this * 100) / 100

    private fun Double.formatMoney(): String = String.format("%,.2f", this)

    private fun calcularISRDetalle(gravable: Double): ISRDetalle {
        val tramos = listOf(
            TramoISR(0.01, 844.59, 0.0, 1.92),
            TramoISR(844.60, 7168.51, 16.22, 6.40),
            TramoISR(7168.52, 12598.02, 420.95, 10.88),
            TramoISR(12598.03, 14644.64, 1011.68, 16.00),
            TramoISR(14644.65, 17533.64, 1339.14, 17.92),
            TramoISR(17533.65, 35362.83, 1856.84, 21.36),
            TramoISR(35362.84, 55736.68, 5665.16, 23.52),
            TramoISR(55736.69, 106410.50, 10457.09, 30.00),
            TramoISR(106410.51, 141880.66, 25659.23, 32.00),
            TramoISR(141880.67, 425641.99, 37009.69, 34.00),
            TramoISR(425642.00, Double.MAX_VALUE, 133488.54, 35.00)
        )

        for (tramo in tramos) {
            if (gravable <= tramo.limSup) {
                val excedente = gravable - tramo.limInf
                val marginal = excedente * (tramo.tasa / 100)
                val isr = marginal + tramo.cuota
                return ISRDetalle(isr, tramo.limInf, excedente, tramo.tasa, marginal, tramo.cuota)
            }
        }
        return ISRDetalle(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }

    private fun dibujarGrafica(isr: Double, imss: Double, neto: Double) {
        val entries = listOf(
            PieEntry(isr.toFloat(), "ISR"),
            PieEntry(imss.toFloat(), "IMSS"),
            PieEntry(neto.toFloat(), "Neto")
        )

        val dataSet = PieDataSet(entries, "Desglose")
        dataSet.colors = intArrayOf(
            Color.parseColor("#EF5350"),
            Color.parseColor("#42A5F5"),
            Color.parseColor("#66BB6A")
        ).toList()
        val data = PieData(dataSet)

        binding.chartGrafica.data = data
        binding.chartGrafica.description.isEnabled = false
        binding.chartGrafica.setUsePercentValues(false)
        binding.chartGrafica.legend.isEnabled = true
        binding.chartGrafica.setEntryLabelColor(Color.BLACK)
        binding.chartGrafica.invalidate()
    }

    private fun guardarHistorial(bruto: Double, isr: Double, imss: Double, deduccionesManual: Double, neto: Double) {
        val prefs = getSharedPreferences("historial", MODE_PRIVATE)
        val editor = prefs.edit()
        val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val key = "calculo_${prefs.getInt("count", 0)}"
        editor.putString(key, "Bruto: ${bruto.formatMoney()} | ISR: ${isr.formatMoney()} | IMSS: ${imss.formatMoney()} | Deducciones: ${deduccionesManual.formatMoney()} | Neto: ${neto.formatMoney()} | Fecha: $fecha")
        editor.putInt("count", prefs.getInt("count", 0) + 1)
        editor.apply()
        Toast.makeText(this, "Historial guardado 📝", Toast.LENGTH_SHORT).show()
    }

    private fun generarPDFGenérico(isr: Double, imss: Double, neto: Double) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = android.graphics.Paint()
        paint.color = Color.BLACK
        paint.textSize = 12f

        canvas.drawText("Reporte ISR 2026 Genérico", 80f, 80f, paint)
        canvas.drawText("Sueldo Neto: ${neto.formatMoney()}", 80f, 100f, paint)
        canvas.drawText("ISR: ${isr.formatMoney()}", 80f, 120f, paint)
        canvas.drawText("IMSS: ${imss.formatMoney()}", 80f, 140f, paint)

        pdfDocument.finishPage(page)

        val directory = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val file = File(directory, "ReporteISR_Generico.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()

        Toast.makeText(this, "PDF Genérico guardado en archivos 📄", Toast.LENGTH_SHORT).show()
    }

    private fun generarPDFProfesional(isr: Double, imss: Double, neto: Double) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = android.graphics.Paint()
        paint.color = Color.BLACK
        paint.textSize = 12f

        canvas.drawText("Reporte ISR 2026 Profesional", 80f, 80f, paint)
        canvas.drawText("Sueldo Neto: ${neto.formatMoney()}", 80f, 100f, paint)
        canvas.drawText("ISR: ${isr.formatMoney()}", 80f, 120f, paint)
        canvas.drawText("IMSS: ${imss.formatMoney()}", 80f, 140f, paint)
        canvas.drawText("Recomendación: Optimiza con deducciones médicas y colegiaturas", 80f, 160f, paint)

        pdfDocument.finishPage(page)

        val directory = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val file = File(directory, "ReporteISR_Profesional.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()

        Toast.makeText(this, "PDF Profesional guardado en archivos 👑📄", Toast.LENGTH_SHORT).show()
    }
}
