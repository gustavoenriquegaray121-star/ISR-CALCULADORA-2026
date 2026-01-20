package com.gustavo.isrcalculadoramx2026

import android.graphics.Color
import android.graphics.Paint
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
import kotlin.math.max
import kotlin.math.roundToInt

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

    private var bruto = 0.0
    private var deduccionesManual = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        MobileAds.initialize(this) {}
        binding.adView.loadAd(AdRequest.Builder().build())

        binding.tvEmote.alpha = 0f
        binding.tvResultado.text = ""

        binding.btnCalcular.setOnClickListener { calcularISR() }

        binding.btnUpgradePremium.setOnClickListener {
            isPremium = true
            binding.adView.visibility = View.GONE
            Toast.makeText(
                this,
                "💎 Versión Premium desbloqueada por $99/mes o $699/año\n¡Gráficas y PDF incluidos!",
                Toast.LENGTH_LONG
            ).show()
            
            if (ultimoNeto > 0) {
                binding.chartGrafica.visibility = View.VISIBLE
                dibujarGrafica(ultimoISR, ultimoIMSS, ultimoNeto)
                generarPDFGenerico(ultimoISR, ultimoIMSS, ultimoNeto)
            }
        }

        binding.btnUpgradeSuperPremium.setOnClickListener {
            isSuperPremium = true
            isPremium = true
            binding.adView.visibility = View.GONE
            Toast.makeText(
                this,
                "👑 Versión Súper Premium desbloqueada por $149/mes o $1299/año\n¡PDF profesional + soporte VIP!",
                Toast.LENGTH_LONG
            ).show()
            
            if (ultimoNeto > 0) {
                binding.chartGrafica.visibility = View.VISIBLE
                dibujarGrafica(ultimoISR, ultimoIMSS, ultimoNeto)
                generarPDFProfesional(ultimoISR, ultimoIMSS, ultimoNeto)
            }
        }
    }

    private fun calcularISR() {
        bruto = binding.etSueldoBruto.text.toString().toDoubleOrNull() ?: 0.0
        deduccionesManual = binding.etDeducciones.text.toString().toDoubleOrNull() ?: 0.0

        if (bruto <= 0) {
            Toast.makeText(this, "😅 Ingresa un sueldo bruto válido", Toast.LENGTH_SHORT).show()
            return
        }

        val imss = if (binding.switchIMSS.isChecked) bruto * 0.00625 else 0.0
        val gravable = bruto - deduccionesManual - imss

        if (gravable <= 0) {
            Toast.makeText(this, "🤔 El ingreso gravable es 0", Toast.LENGTH_SHORT).show()
            return
        }

        var detalle = calcularISRDetalle(gravable)
        detalle = aplicarSubsidio(detalle, gravable)

        val neto = bruto - detalle.isr - imss - deduccionesManual
        val netoRedondeado = (neto * 100).roundToInt() / 100.0

        ultimoISR = detalle.isr
        ultimoIMSS = imss
        ultimoNeto = netoRedondeado

        binding.tvResultado.text = """
            💰 Sueldo Neto Real: ${String.format("%,.2f", netoRedondeado)} MXN

            - Deducciones manuales: ${String.format("%,.2f", deduccionesManual)}
            - Cuota IMSS obrera aprox.: ${String.format("%,.2f", imss)}
            🔥 ISR a pagar: ${String.format("%,.2f", detalle.isr)}

            Cálculo estimado — SAT 2026
        """.trimIndent()

        binding.tvEmote.text = when {
            neto >= bruto * 0.85 -> "🤑 ¡Estás en la cima, jefe!"
            neto >= bruto * 0.70 -> "😎 ¡Sigue rico!"
            neto >= bruto * 0.55 -> "🙂 No está mal, eh"
            neto >= bruto * 0.40 -> "😬 Uy… aprieta el cinturón"
            else -> "😭 El SAT mordió fuerte esta vez"
        }

        binding.tvEmote.animate().alpha(1f).setDuration(600).start()

        if (isPremium) {
            binding.chartGrafica.visibility = View.VISIBLE
            dibujarGrafica(ultimoISR, ultimoIMSS, ultimoNeto)
        }
    }

    private fun calcularISRDetalle(gravable: Double): ISRDetalle {
        val tramos = listOf(
            TramoISR(0.01,          10135.11,    0.00,       1.92),
            TramoISR(10135.12,      86022.11,    194.59,     6.40),
            TramoISR(86022.12,      151176.19,   5051.37,    10.88),
            TramoISR(151176.20,     176935.68,   12140.16,   16.00),
            TramoISR(176935.69,     210403.68,   16069.68,   17.92),
            TramoISR(210403.69,     424354.00,   22282.08,   21.36),
            TramoISR(424354.01,     668840.16,   67981.92,   23.52),
            TramoISR(668840.17,     1276926.00,  125485.08,  30.00),
            TramoISR(1276926.01,    1702567.92,  307910.76,  32.00),
            TramoISR(1702567.93,    5107703.88,  444116.28,  34.00),
            TramoISR(5107703.89,    Double.MAX_VALUE, 1601862.48, 35.00)
        )

        for (tramo in tramos) {
            if (gravable <= tramo.limSup) {
                val excedente = gravable - tramo.limInf
                val marginal = excedente * (tramo.tasa / 100)
                return ISRDetalle(
                    marginal + tramo.cuota,
                    tramo.limInf,
                    excedente,
                    tramo.tasa,
                    marginal,
                    tramo.cuota
                )
            }
        }
        return ISRDetalle(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }

    private fun aplicarSubsidio(detalle: ISRDetalle, gravable: Double): ISRDetalle {
        val topeSubsidio = 11492.66
        if (gravable > topeSubsidio) return detalle

        val subsidioFijo = 536.22
        return detalle.copy(isr = max(0.0, detalle.isr - subsidioFijo))
    }

    private fun dibujarGrafica(isr: Double, imss: Double, neto: Double) {
        val entries = listOf(
            PieEntry(isr.toFloat(), "ISR"),
            PieEntry(imss.toFloat(), "IMSS"),
            PieEntry(neto.toFloat(), "Neto")
        )

        val dataSet = PieDataSet(entries, "Desglose de sueldo")
        dataSet.colors = listOf(
            Color.parseColor("#FFD700"),
            Color.parseColor("#FF8F00"),
            Color.parseColor("#00C853")
        )
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 16f

        val data = PieData(dataSet)
        binding.chartGrafica.data = data
        binding.chartGrafica.setEntryLabelColor(Color.WHITE)
        binding.chartGrafica.setEntryLabelTextSize(12f)
        binding.chartGrafica.description.isEnabled = false
        binding.chartGrafica.legend.textColor = Color.WHITE
        binding.chartGrafica.legend.textSize = 14f
        
        binding.chartGrafica.setDrawHoleEnabled(true)
        binding.chartGrafica.holeRadius = 50f
        binding.chartGrafica.transparentCircleRadius = 55f
        binding.chartGrafica.setHoleColor(Color.TRANSPARENT)
        
        binding.chartGrafica.invalidate()
    }

    private fun generarPDFGenerico(isr: Double, imss: Double, neto: Double) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        paint.textSize = 18f
        paint.isFakeBoldText = true
        paint.color = Color.BLACK
        canvas.drawText("Calculadora ISR 2026 - Reporte Premium", 40f, 60f, paint)

        paint.textSize = 14f
        paint.isFakeBoldText = false
        var yPos = 100f
        
        canvas.drawText("Sueldo Bruto: ${String.format("%,.2f", bruto)} MXN", 40f, yPos, paint)
        yPos += 25f
        canvas.drawText("Deducciones: ${String.format("%,.2f", deduccionesManual)} MXN", 40f, yPos, paint)
        yPos += 25f
        canvas.drawText("IMSS (0.625% aprox.): ${String.format("%,.2f", imss)} MXN", 40f, yPos, paint)
        yPos += 25f
        canvas.drawText("ISR a pagar: ${String.format("%,.2f", isr)} MXN", 40f, yPos, paint)
        yPos += 30f
        
        paint.isFakeBoldText = true
        paint.textSize = 16f
        canvas.drawText("SUELDO NETO: ${String.format("%,.2f", neto)} MXN", 40f, yPos, paint)
        
        yPos += 40f
        paint.isFakeBoldText = false
        paint.textSize = 12f
        canvas.drawText("Cálculo basado en tablas SAT 2026", 40f, yPos, paint)
        yPos += 20f
        canvas.drawText("Este es un cálculo estimado, no sustituye declaración oficial", 40f, yPos, paint)

        pdfDocument.finishPage(page)

        val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir != null) {
            val file = File(downloadsDir, "ISR_Premium_${System.currentTimeMillis()}.pdf")
            try {
                pdfDocument.writeTo(FileOutputStream(file))
                Toast.makeText(this, "✅ PDF guardado en: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "❌ Error al guardar PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        pdfDocument.close()
    }

    private fun generarPDFProfesional(isr: Double, imss: Double, neto: Double) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())

        paint.textSize = 20f
        paint.isFakeBoldText = true
        paint.color = Color.BLACK
        canvas.drawText("REPORTE PROFESIONAL ISR 2026", 40f, 60f, paint)

        paint.textSize = 12f
        paint.isFakeBoldText = false
        canvas.drawText("Fecha de generación: $fecha", 40f, 85f, paint)

        paint.strokeWidth = 2f
        canvas.drawLine(40f, 95f, 555f, 95f, paint)

        paint.textSize = 14f
        var yPos = 120f

        paint.isFakeBoldText = true
        canvas.drawText("DATOS DE ENTRADA:", 40f, yPos, paint)
        paint.isFakeBoldText = false
        yPos += 25f
        
        canvas.drawText("• Sueldo Bruto Mensual: ${String.format("%,.2f", bruto)} MXN", 60f, yPos, paint)
        yPos += 20f
        canvas.drawText("• Deducciones Manuales: ${String.format("%,.2f", deduccionesManual)} MXN", 60f, yPos, paint)
        yPos += 20f
        canvas.drawText("• Cuota IMSS obrera aprox. (0.625%): ${String.format("%,.2f", imss)} MXN", 60f, yPos, paint)
        
        yPos += 35f
        paint.strokeWidth = 1f
        canvas.drawLine(40f, yPos, 555f, yPos, paint)
        yPos += 25f

        paint.isFakeBoldText = true
        canvas.drawText("CÁLCULO DE ISR:", 40f, yPos, paint)
        paint.isFakeBoldText = false
        yPos += 25f
        
        canvas.drawText("• ISR calculado: ${String.format("%,.2f", isr)} MXN", 60f, yPos, paint)
        yPos += 20f
        canvas.drawText("• Subsidio aplicado: $536.22 MXN (SAT 2026)", 60f, yPos, paint)
        
        yPos += 35f
        paint.strokeWidth = 1f
        canvas.drawLine(40f, yPos, 555f, yPos, paint)
        yPos += 25f

        paint.isFakeBoldText = true
        paint.textSize = 18f
        canvas.drawText("SUELDO NETO FINAL: ${String.format("%,.2f", neto)} MXN", 40f, yPos, paint)
        
        yPos += 40f
        paint.strokeWidth = 2f
        canvas.drawLine(40f, yPos, 555f, yPos, paint)

        paint.isFakeBoldText = false
        paint.textSize = 10f
        yPos += 25f
        canvas.drawText("Este reporte es generado por ISR Calculadora MX 2026 - Versión Súper Premium", 40f, yPos, paint)
        yPos += 15f
        canvas.drawText("Basado en las tablas oficiales del SAT vigentes para 2026", 40f, yPos, paint)
        yPos += 15f
        canvas.drawText("Este cálculo es estimado y no sustituye una declaración oficial ante el SAT", 40f, yPos, paint)

        pdfDocument.finishPage(page)

        val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir != null) {
            val file = File(downloadsDir, "ISR_SuperPremium_${System.currentTimeMillis()}.pdf")
            try {
                pdfDocument.writeTo(FileOutputStream(file))
                Toast.makeText(this, "✅ PDF Profesional guardado en: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "❌ Error al guardar PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        pdfDocument.close()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.adView.destroy()
    }
}
